package sistema.eventos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Controle progressivo de tentativas de login por identificador de conta. */
public class LoginAttemptService {

    private static final int FALHAS_ATE_BLOQUEIO = 5;
    private static final int LIMITE_ESTADOS = 10_000;
    private static final Duration RETENCAO_ESTADO = Duration.ofHours(12);
    private static final Duration[] DURACOES_BLOQUEIO = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1)
    };

    private final ConcurrentHashMap<String, EstadoTentativa> estados = new ConcurrentHashMap<>();
    private final AtomicLong operacoes = new AtomicLong();

    public boolean estaBloqueado(String identificador) {
        String chave = chave(identificador);
        if (chave == null) return false;
        EstadoTentativa estado = estados.get(chave);
        if (estado == null) return false;
        synchronized (estado) {
            if (estado.bloqueadoAte != null && !Instant.now().isBefore(estado.bloqueadoAte)) {
                estado.bloqueadoAte = null;
            }
            return estado.bloqueadoAte != null;
        }
    }

    public void registrarFalha(String identificador) {
        String chave = chave(identificador);
        if (chave == null) return;
        Instant agora = Instant.now();
        estados.compute(chave, (ignorado, estado) -> {
            EstadoTentativa atual = estado == null ? new EstadoTentativa() : estado;
            synchronized (atual) {
                atual.ultimaTentativa = agora;
                if (atual.bloqueadoAte == null || !agora.isBefore(atual.bloqueadoAte)) {
                    atual.bloqueadoAte = null;
                    atual.falhas++;
                    if (atual.falhas >= FALHAS_ATE_BLOQUEIO) {
                        int nivel = Math.min(atual.nivelBloqueio, DURACOES_BLOQUEIO.length - 1);
                        atual.bloqueadoAte = agora.plus(DURACOES_BLOQUEIO[nivel]);
                        atual.nivelBloqueio = Math.min(nivel + 1, DURACOES_BLOQUEIO.length - 1);
                        atual.falhas = 0;
                    }
                }
            }
            return atual;
        });
        limparEstadosExpirados(agora);
    }

    public void registrarSucesso(String identificador) {
        String chave = chave(identificador);
        if (chave != null) estados.remove(chave);
    }

    private void limparEstadosExpirados(Instant agora) {
        if (operacoes.incrementAndGet() % 256 != 0 && estados.size() <= LIMITE_ESTADOS) return;
        Instant limite = agora.minus(RETENCAO_ESTADO);
        estados.entrySet().removeIf(entry -> {
            EstadoTentativa estado = entry.getValue();
            synchronized (estado) {
                return (estado.ultimaTentativa == null || estado.ultimaTentativa.isBefore(limite))
                        && (estado.bloqueadoAte == null || !agora.isBefore(estado.bloqueadoAte));
            }
        });
        if (estados.size() > LIMITE_ESTADOS) {
            estados.entrySet().stream()
                    .sorted((a, b) -> compararUltimaTentativa(a.getValue(), b.getValue()))
                    .limit(estados.size() - LIMITE_ESTADOS)
                    .map(java.util.Map.Entry::getKey)
                    .toList()
                    .forEach(estados::remove);
        }
    }

    private int compararUltimaTentativa(EstadoTentativa a, EstadoTentativa b) {
        Instant primeira;
        Instant segunda;
        synchronized (a) { primeira = a.ultimaTentativa; }
        synchronized (b) { segunda = b.ultimaTentativa; }
        if (primeira == null) return segunda == null ? 0 : -1;
        if (segunda == null) return 1;
        return primeira.compareTo(segunda);
    }

    private String chave(String identificador) {
        if (identificador == null || identificador.isBlank()) return null;
        String normalizado = identificador.trim().toLowerCase(Locale.ROOT);
        if (normalizado.length() > 254) normalizado = normalizado.substring(0, 254);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalizado.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    private static final class EstadoTentativa {
        private int falhas;
        private int nivelBloqueio;
        private Instant bloqueadoAte;
        private Instant ultimaTentativa;
    }
}
