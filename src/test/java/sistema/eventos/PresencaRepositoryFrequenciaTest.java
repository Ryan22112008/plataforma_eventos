package sistema.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import sistema.eventos.dto.PresencaPorAlunoProjection;
import sistema.eventos.model.Evento;
import sistema.eventos.model.ModalidadeEvento;
import sistema.eventos.model.NichoEvento;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Presenca;
import sistema.eventos.model.Sessao;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.StatusPresenca;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida a consulta agregada usada pela lista de inscritos. A presença só deve
 * contar quando for PRESENTE, em sessões do evento consultado e uma única vez
 * por sessão.
 */
@DataJpaTest
class PresencaRepositoryFrequenciaTest {

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Autowired
    private PresencaRepository presencaRepository;

    private Evento evento;
    private Evento outroEvento;
    private Usuario participanteComPresenca;
    private Usuario participanteSemPresenca;
    private Sessao sessao1;
    private Sessao sessao2;

    @BeforeEach
    void prepararDados() {
        Usuario organizador = usuarioRepository.save(Usuario.builder()
                .nome("Organizador Frequência")
                .email("organizador.frequencia@seplag.mt.gov.br")
                .senha("senha-segura")
                .perfil(Perfil.ROLE_ORGANIZADOR)
                .ativo(true)
                .build());

        evento = eventoRepository.save(criarEvento("Evento Principal", organizador));
        outroEvento = eventoRepository.save(criarEvento("Outro Evento", organizador));

        participanteComPresenca = usuarioRepository.save(Usuario.builder()
                .nome("Participante Presente")
                .email("presente@seplag.mt.gov.br")
                .senha("senha-segura")
                .perfil(Perfil.ROLE_PARTICIPANTE)
                .ativo(true)
                .build());

        participanteSemPresenca = usuarioRepository.save(Usuario.builder()
                .nome("Participante Ausente")
                .email("ausente@seplag.mt.gov.br")
                .senha("senha-segura")
                .perfil(Perfil.ROLE_PARTICIPANTE)
                .ativo(true)
                .build());

        sessao1 = sessaoRepository.save(criarSessao(evento, "Sessão 1", "token-sessao-1"));
        sessao2 = sessaoRepository.save(criarSessao(evento, "Sessão 2", "token-sessao-2"));
        Sessao sessaoOutroEvento = sessaoRepository.save(
                criarSessao(outroEvento, "Sessão de outro evento", "token-outro-evento"));

        presencaRepository.save(criarPresenca(participanteComPresenca, sessao1, StatusPresenca.PRESENTE));
        presencaRepository.save(criarPresenca(participanteComPresenca, sessao2, StatusPresenca.PRESENTE));
        // Registro duplicado na mesma sessão não pode inflar a frequência.
        presencaRepository.save(criarPresenca(participanteComPresenca, sessao1, StatusPresenca.PRESENTE));
        // Presença de outro evento não pode entrar no cálculo deste evento.
        presencaRepository.save(criarPresenca(participanteComPresenca, sessaoOutroEvento, StatusPresenca.PRESENTE));
        // FALTA e JUSTIFICADA não contam como presença.
        presencaRepository.save(criarPresenca(participanteSemPresenca, sessao1, StatusPresenca.FALTA));
        presencaRepository.save(criarPresenca(participanteSemPresenca, sessao2, StatusPresenca.JUSTIFICADA));
    }

    @Test
    void contaSomenteSessoesDistintasComPresencaPresenteDoEvento() {
        Map<Long, Long> presencasPorAluno = presencaRepository
                .contarPresencasPresentesPorAluno(evento.getId(), StatusPresenca.PRESENTE)
                .stream()
                .collect(Collectors.toMap(
                        PresencaPorAlunoProjection::getAlunoId,
                        PresencaPorAlunoProjection::getTotalPresentes));

        assertThat(presencasPorAluno)
                .containsEntry(participanteComPresenca.getId(), 2L)
                .doesNotContainKey(participanteSemPresenca.getId());
    }

    private Evento criarEvento(String titulo, Usuario organizador) {
        return Evento.builder()
                .titulo(titulo)
                .descricao("Descrição do evento")
                .dataInicio(LocalDate.of(2026, 10, 1))
                .dataTermino(LocalDate.of(2026, 10, 31))
                .vagasTotais(50)
                .vagasRestantes(50)
                .status(StatusEvento.PUBLICADO)
                .local("Auditório SEPLAG-MT")
                .modalidade(ModalidadeEvento.PRESENCIAL)
                .nicho(NichoEvento.TECNOLOGIA)
                .organizador(organizador)
                .build();
    }

    private Sessao criarSessao(Evento evento, String nome, String token) {
        Sessao sessao = new Sessao();
        sessao.setNome(nome);
        sessao.setDataHoraInicio(LocalDateTime.of(2026, 10, 10, 8, 0));
        sessao.setDataHoraFim(LocalDateTime.of(2026, 10, 10, 12, 0));
        sessao.setQrCodeToken(token);
        sessao.setEvento(evento);
        return sessao;
    }

    private Presenca criarPresenca(Usuario aluno, Sessao sessao, StatusPresenca status) {
        Presenca presenca = new Presenca();
        presenca.setAluno(aluno);
        presenca.setSessao(sessao);
        presenca.setStatus(status);
        return presenca;
    }
}
