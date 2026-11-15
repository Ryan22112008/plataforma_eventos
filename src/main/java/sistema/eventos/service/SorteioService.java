package sistema.eventos.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema.eventos.dto.InscritoFrequenciaDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.AcaoAuditoriaSorteio;
import sistema.eventos.model.Evento;
import sistema.eventos.model.SorteioAuditoria;
import sistema.eventos.model.SorteioResultado;
import sistema.eventos.model.SorteioRodada;
import sistema.eventos.model.StatusRodadaSorteio;
import sistema.eventos.model.StatusVencedorSorteio;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.SorteioAuditoriaRepository;
import sistema.eventos.repository.SorteioResultadoRepository;
import sistema.eventos.repository.SorteioRodadaRepository;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras dos sorteios do organizador. Todo o sorteio acontece no servidor e
 * usa {@link SecureRandom} com embaralhamento Fisher-Yates — nunca Math.random.
 */
@Service
public class SorteioService {

    private static final int LIMITE_QUANTIDADE = 1000;
    private static final int LIMITE_PREMIOS = 120;
    private static final int LIMITE_AUTOR = 150;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA_HORA_EXPORTACAO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * SecureRandom.nextInt(bound) é o equivalente criptográfico de
     * crypto.randomInt e é usado somente dentro do Fisher-Yates abaixo.
     */
    private static final SecureRandom CRYPTO_RANDOM = new SecureRandom();

    private final InscricaoService inscricaoService;
    private final EventoRepository eventoRepository;
    private final SorteioRodadaRepository rodadaRepository;
    private final SorteioResultadoRepository resultadoRepository;
    private final SorteioAuditoriaRepository auditoriaRepository;

    public SorteioService(InscricaoService inscricaoService,
                          EventoRepository eventoRepository,
                          SorteioRodadaRepository rodadaRepository,
                          SorteioResultadoRepository resultadoRepository,
                          SorteioAuditoriaRepository auditoriaRepository) {
        this.inscricaoService = inscricaoService;
        this.eventoRepository = eventoRepository;
        this.rodadaRepository = rodadaRepository;
        this.resultadoRepository = resultadoRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    public int percentualMinimo(Evento evento) {
        return evento.getSorteioFrequenciaMinima() == null ? 75 : evento.getSorteioFrequenciaMinima();
    }

    /**
     * Participantes que atendem à frequência mínima e ainda não venceram em
     * uma rodada ativa. Quando uma rodada é desfeita, seus vencedores voltam
     * automaticamente para esta lista.
     */
    @Transactional(readOnly = true)
    public List<InscritoFrequenciaDto> listarElegiveisDisponiveis(Evento evento) {
        int minimo = percentualMinimo(evento);
        Set<String> vencedoresAtivos = resultadosAtivos(evento.getId()).stream()
                .map(this::chaveVencedor)
                .collect(Collectors.toSet());

        return inscricaoService.listarInscritosComFrequencia(evento.getId()).stream()
                .filter(i -> !i.isInscricaoCancelada())
                .filter(i -> i.getPercentualFrequencia() != null && i.getPercentualFrequencia() >= minimo)
                .filter(i -> !vencedoresAtivos.contains(chaveParticipante(i)))
                .sorted(Comparator.comparing(InscritoFrequenciaDto::getNomeParticipante,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SorteioResultado> listarResultados(Long eventoId) {
        return resultadoRepository.findByEventoIdOrderByRealizadoEmDescIdDesc(eventoId);
    }

    @Transactional(readOnly = true)
    public List<SorteioResultado> listarVencedoresDaRodada(Long rodadaId) {
        return rodadaRepository.findById(rodadaId)
                .map(rodada -> resultadoRepository.findByRodadaSorteioIdOrderByIdAsc(rodada.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Optional<SorteioRodada> buscarRodadaDoEvento(Long eventoId, Long rodadaId) {
        if (rodadaId == null) {
            return Optional.empty();
        }
        return rodadaRepository.findById(rodadaId)
                .filter(rodada -> rodada.getEvento() != null
                        && eventoId.equals(rodada.getEvento().getId()));
    }

    @Transactional(readOnly = true)
    public Optional<SorteioRodada> ultimaRodadaAtiva(Long eventoId) {
        return rodadaRepository.findFirstByEventoIdAndStatusOrderByNumeroRodadaDesc(
                eventoId, StatusRodadaSorteio.REALIZADA);
    }

    @Transactional(readOnly = true)
    public long contarRodadasAtivas(Long eventoId) {
        return rodadaRepository.countByEventoIdAndStatus(eventoId, StatusRodadaSorteio.REALIZADA);
    }

    @Transactional(readOnly = true)
    public List<SorteioAuditoria> listarAuditoria(Long eventoId) {
        return auditoriaRepository.findTop200ByEventoIdOrderByRealizadoEmDescIdDesc(eventoId);
    }

    /**
     * Filtro do histórico. Busca por nome/e-mail, prêmio e status individual.
     */
    public List<SorteioResultado> filtrar(List<SorteioResultado> resultados, String busca,
                                          String premio, StatusVencedorSorteio status) {
        String termo = normalizar(busca);
        String termoPremio = normalizar(premio);
        return resultados.stream()
                .filter(r -> termo == null
                        || contem(r.getNomeVencedor(), termo)
                        || contem(r.getEmailVencedor(), termo))
                .filter(r -> termoPremio == null || contem(r.getPremioRodada(), termoPremio))
                .filter(r -> status == null || r.getStatusVencedorEfetivo() == status)
                .toList();
    }

    @Transactional
    public boolean configurarFrequencia(Long eventoId, Integer percentual, String autorEmail) {
        if (percentual == null || percentual < 0 || percentual > 100) {
            throw new NegocioException("Informe um percentual entre 0 e 100.");
        }
        Evento evento = exigirEventoParaAtualizacao(eventoId);
        int anterior = percentualMinimo(evento);
        if (anterior == percentual) {
            return false;
        }
        evento.setSorteioFrequenciaMinima(percentual);
        eventoRepository.save(evento);
        auditar(evento, AcaoAuditoriaSorteio.PERCENTUAL_ALTERADO,
                "Frequência mínima alterada de " + anterior + "% para " + percentual + "%.",
                autorEmail);
        return true;
    }

    @Transactional
    public SorteioRodada realizarSorteio(Long eventoId, Integer quantidade, String premio, String autorEmail) {
        Evento evento = exigirEventoParaAtualizacao(eventoId);
        validarQuantidade(quantidade);
        int minimo = percentualMinimo(evento);
        List<InscritoFrequenciaDto> elegiveis = new ArrayList<>(listarElegiveisDisponiveis(evento));
        if (elegiveis.isEmpty()) {
            throw new NegocioException("Não há participantes elegíveis. Confira o percentual mínimo e as presenças registradas.");
        }
        if (quantidade > elegiveis.size()) {
            throw new NegocioException("Há apenas " + elegiveis.size()
                    + " participante(s) elegível(is) para este sorteio.");
        }
        fisherYates(elegiveis);
        return criarRodada(evento, elegiveis, quantidade, premio, minimo, null, autorEmail,
                AcaoAuditoriaSorteio.SORTEIO_REALIZADO, null);
    }

    /**
     * Repõe um vencedor ausente com uma nova rodada de uma pessoa. A nova
     * rodada herda o prêmio e fica vinculada à rodada original.
     */
    @Transactional
    public SorteioRodada sortearNovamente(Long eventoId, Long resultadoId, String autorEmail) {
        Evento evento = exigirEventoParaAtualizacao(eventoId);
        SorteioResultado original = resultadoRepository.findById(resultadoId)
                .orElseThrow(() -> new NegocioException("Vencedor não encontrado."));
        if (original.getEvento() == null || !eventoId.equals(original.getEvento().getId())) {
            throw new NegocioException("Este vencedor não pertence ao evento informado.");
        }
        if (original.getStatusVencedorEfetivo() != StatusVencedorSorteio.AUSENTE) {
            throw new NegocioException("A reposição só é permitida para vencedores marcados como Ausente.");
        }
        SorteioRodada rodadaOriginal = original.getRodadaSorteio();
        if (rodadaOriginal == null) {
            throw new NegocioException("A rodada original não possui metadados suficientes para a reposição.");
        }
        if (rodadaOriginal.isCancelada()) {
            throw new NegocioException("Não é possível repor um vencedor de uma rodada cancelada.");
        }

        int minimo = percentualMinimo(evento);
        List<InscritoFrequenciaDto> elegiveis = new ArrayList<>(listarElegiveisDisponiveis(evento));
        if (elegiveis.isEmpty()) {
            throw new NegocioException("Não há participantes elegíveis para a reposição.");
        }
        fisherYates(elegiveis);

        SorteioRodada reposicao = criarRodada(evento, elegiveis, 1, rodadaOriginal.getPremio(),
                minimo, rodadaOriginal, autorEmail, AcaoAuditoriaSorteio.SORTEIO_REPESCAGEM,
                "Reposição do vencedor ausente da rodada " + rodadaOriginal.getNumeroRodada() + ".");

        List<SorteioResultado> novos = resultadoRepository.findByRodadaSorteioIdOrderByIdAsc(reposicao.getId());
        if (!novos.isEmpty()) {
            original.setSubstituidoPor(novos.get(0));
            resultadoRepository.save(original);
        }
        return reposicao;
    }

    @Transactional
    public SorteioRodada desfazerUltimaRodada(Long eventoId, Long rodadaId, String autorEmail) {
        Evento evento = exigirEventoParaAtualizacao(eventoId);
        SorteioRodada rodada = rodadaRepository
                .findFirstByEventoIdAndStatusOrderByNumeroRodadaDesc(eventoId, StatusRodadaSorteio.REALIZADA)
                .orElseThrow(() -> new NegocioException("Não há rodada ativa para desfazer."));
        if (rodadaId != null && !rodadaId.equals(rodada.getId())) {
            throw new NegocioException("Somente a rodada ativa mais recente pode ser desfeita.");
        }

        int quantidade = resultadoRepository.findByRodadaSorteioIdOrderByIdAsc(rodada.getId()).size();
        rodada.setStatus(StatusRodadaSorteio.CANCELADA);
        rodadaRepository.save(rodada);
        sincronizarCamposLegado(evento);
        eventoRepository.save(evento);

        auditar(evento, AcaoAuditoriaSorteio.RODADA_DESFEITA,
                "Rodada " + rodada.getNumeroRodada() + " desfeita e marcada como cancelada. "
                        + quantidade + " vencedor(es) voltaram a ficar disponíveis.",
                autorEmail);
        return rodada;
    }

    @Transactional
    public SorteioResultado alterarStatusVencedor(Long eventoId, Long resultadoId,
                                                  StatusVencedorSorteio novoStatus, String autorEmail) {
        if (novoStatus == null) {
            throw new NegocioException("Selecione um status válido.");
        }
        Evento evento = exigirEventoParaAtualizacao(eventoId);
        SorteioResultado resultado = resultadoRepository.findById(resultadoId)
                .orElseThrow(() -> new NegocioException("Vencedor não encontrado."));
        if (resultado.getEvento() == null || !eventoId.equals(resultado.getEvento().getId())) {
            throw new NegocioException("Este vencedor não pertence ao evento informado.");
        }
        if (resultado.isRodadaCancelada()) {
            throw new NegocioException("Não é possível alterar o status de uma rodada cancelada.");
        }
        StatusVencedorSorteio anterior = resultado.getStatusVencedorEfetivo();
        resultado.setStatusVencedor(novoStatus);
        resultadoRepository.save(resultado);

        auditar(evento, AcaoAuditoriaSorteio.STATUS_VENCEDOR_ALTERADO,
                "Status de " + resultado.getNomeVencedor() + " na rodada " + resultado.getRodada()
                        + " alterado de " + anterior.getDescricao() + " para " + novoStatus.getDescricao() + ".",
                autorEmail);
        return resultado;
    }

    private void validarQuantidade(Integer quantidade) {
        if (quantidade == null || quantidade < 1) {
            throw new NegocioException("Informe pelo menos 1 pessoa para sortear.");
        }
        if (quantidade > LIMITE_QUANTIDADE) {
            throw new NegocioException("Escolha uma quantidade de até " + LIMITE_QUANTIDADE + " pessoas.");
        }
    }

    private SorteioRodada criarRodada(Evento evento, List<InscritoFrequenciaDto> elegiveis,
                                      int quantidade, String premio, int minimo,
                                      SorteioRodada rodadaOrigem, String autorEmail,
                                      AcaoAuditoriaSorteio acao, String contextoExtra) {
        int numero = Math.max(
                evento.getSorteioNumeroRodada() == null ? 0 : evento.getSorteioNumeroRodada(),
                Optional.ofNullable(rodadaRepository.maiorNumeroRodada(evento.getId())).orElse(0)) + 1;
        LocalDateTime realizadoEm = LocalDateTime.now();
        String premioLimpo = limparPremio(premio);

        SorteioRodada rodada = new SorteioRodada();
        rodada.setEvento(evento);
        rodada.setNumeroRodada(numero);
        rodada.setPremio(premioLimpo);
        rodada.setPercentualMinimo(minimo);
        rodada.setTotalElegiveis(elegiveis.size());
        rodada.setQuantidadeSorteada(quantidade);
        rodada.setRealizadoEm(realizadoEm);
        rodada.setRealizadoPor(limitar(autorEmail, LIMITE_AUTOR));
        rodada.setStatus(StatusRodadaSorteio.REALIZADA);
        rodada.setRodadaOrigem(rodadaOrigem);
        rodadaRepository.save(rodada);

        List<SorteioResultado> resultados = new ArrayList<>();
        for (InscritoFrequenciaDto participante : elegiveis.subList(0, quantidade)) {
            SorteioResultado resultado = new SorteioResultado();
            resultado.setEvento(evento);
            resultado.setRodada(numero);
            resultado.setNomeVencedor(participante.getNomeParticipante());
            resultado.setEmailVencedor(participante.getEmailParticipante());
            resultado.setPercentualFrequencia(participante.getPercentualFrequencia());
            resultado.setRealizadoEm(realizadoEm);
            resultado.setRodadaSorteio(rodada);
            resultado.setStatusVencedor(StatusVencedorSorteio.PENDENTE);
            resultados.add(resultado);
        }
        resultadoRepository.saveAll(resultados);

        evento.setSorteioNumeroRodada(numero);
        InscritoFrequenciaDto primeiro = elegiveis.get(0);
        evento.setSorteioVencedorNome(primeiro.getNomeParticipante());
        evento.setSorteioVencedorEmail(primeiro.getEmailParticipante());
        evento.setSorteioRealizadoEm(realizadoEm);
        eventoRepository.save(evento);

        StringBuilder detalhe = new StringBuilder();
        detalhe.append("Rodada ").append(numero).append(" realizada: ")
                .append(quantidade).append(" vencedor(es) entre ")
                .append(elegiveis.size()).append(" elegíveis (frequência ≥ ")
                .append(minimo).append("%).");
        if (premioLimpo != null) {
            detalhe.append(" Prêmio: ").append(premioLimpo).append(".");
        }
        if (contextoExtra != null && !contextoExtra.isBlank()) {
            detalhe.append(" ").append(contextoExtra);
        }
        auditar(evento, acao, detalhe.toString(), autorEmail);
        return rodada;
    }

    private void sincronizarCamposLegado(Evento evento) {
        Optional<SorteioRodada> ultima = rodadaRepository.findFirstByEventoIdAndStatusOrderByNumeroRodadaDesc(
                evento.getId(), StatusRodadaSorteio.REALIZADA);
        if (ultima.isEmpty()) {
            evento.setSorteioVencedorNome(null);
            evento.setSorteioVencedorEmail(null);
            evento.setSorteioRealizadoEm(null);
            return;
        }
        SorteioRodada rodada = ultima.get();
        List<SorteioResultado> vencedores = resultadoRepository
                .findByRodadaSorteioIdOrderByIdAsc(rodada.getId());
        if (vencedores.isEmpty()) {
            evento.setSorteioVencedorNome(null);
            evento.setSorteioVencedorEmail(null);
        } else {
            SorteioResultado primeiro = vencedores.get(0);
            evento.setSorteioVencedorNome(primeiro.getNomeVencedor());
            evento.setSorteioVencedorEmail(primeiro.getEmailVencedor());
        }
        evento.setSorteioRealizadoEm(rodada.getRealizadoEm());
    }

    private List<SorteioResultado> resultadosAtivos(Long eventoId) {
        return resultadoRepository.findByEventoIdOrderByRealizadoEmDescIdDesc(eventoId).stream()
                .filter(SorteioResultado::isRodadaAtiva)
                .toList();
    }

    /**
     * Fisher-Yates com fonte criptográfica. A cada posição é escolhido um
     * índice seguro dentro do intervalo ainda não sorteado.
     */
    private void fisherYates(List<InscritoFrequenciaDto> lista) {
        for (int i = lista.size() - 1; i > 0; i--) {
            int j = CRYPTO_RANDOM.nextInt(i + 1);
            InscritoFrequenciaDto temporario = lista.get(i);
            lista.set(i, lista.get(j));
            lista.set(j, temporario);
        }
    }

    private Evento exigirEventoParaAtualizacao(Long eventoId) {
        return eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
    }

    private void auditar(Evento evento, AcaoAuditoriaSorteio acao, String detalhes, String autorEmail) {
        SorteioAuditoria auditoria = new SorteioAuditoria();
        auditoria.setEvento(evento);
        auditoria.setAcao(acao);
        auditoria.setDetalhes(limitar(detalhes, 4000));
        auditoria.setAutorEmail(limitar(autorEmail, LIMITE_AUTOR));
        auditoria.setAutorNome(limitar(autorEmail, LIMITE_AUTOR));
        auditoria.setRealizadoEm(LocalDateTime.now());
        auditoriaRepository.save(auditoria);
    }

    private String chaveParticipante(InscritoFrequenciaDto participante) {
        return chave(participante.getEmailParticipante(), participante.getNomeParticipante());
    }

    private String chaveVencedor(SorteioResultado resultado) {
        return chave(resultado.getEmailVencedor(), resultado.getNomeVencedor());
    }

    private String chave(String email, String nome) {
        if (email != null && !email.isBlank()) {
            return "e:" + email.trim().toLowerCase(Locale.ROOT);
        }
        return "n:" + (nome == null ? "" : nome.trim().toLowerCase(Locale.ROOT));
    }

    private boolean contem(String valor, String termo) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(termo);
    }

    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    private String limparPremio(String premio) {
        if (premio == null || premio.isBlank()) {
            return null;
        }
        return limitar(premio.trim(), LIMITE_PREMIOS);
    }

    private String limitar(String valor, int limite) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.length() <= limite ? limpo : limpo.substring(0, limite);
    }

    // ---------------------------------------------------------------------
    // Exportação do histórico
    // ---------------------------------------------------------------------

    public String gerarCsv(List<SorteioResultado> resultados) {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Rodada;Prêmio;Vencedor;E-mail;Frequência;Critério usado;Status;Data\n");
        for (SorteioResultado resultado : resultados) {
            csv.append(csvEscape(numeroRodada(resultado))).append(';')
                    .append(csvEscape(valorOuVazio(resultado.getPremioRodada()))).append(';')
                    .append(csvEscape(valorOuVazio(resultado.getNomeVencedor()))).append(';')
                    .append(csvEscape(valorOuVazio(resultado.getEmailVencedor()))).append(';')
                    .append(csvEscape(formatarPercentual(resultado.getPercentualFrequencia()))).append(';')
                    .append(csvEscape(resultado.getCriterioAplicado())).append(';')
                    .append(csvEscape(resultado.getStatusExibicao())).append(';')
                    .append(csvEscape(formatarDataExportacao(resultado.getRealizadoEm())))
                    .append('\n');
        }
        return csv.toString();
    }

    public byte[] gerarPdf(String tituloEvento, List<SorteioResultado> resultados) {
        Document document = new Document(PageSize.A4.rotate(), 22, 22, 24, 24);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, saida);
            document.open();

            Font titulo = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(58, 24, 110));
            Font subtitulo = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(90, 82, 105));
            Font cabecalho = new Font(Font.HELVETICA, 7.5f, Font.BOLD, new Color(45, 25, 75));
            Font corpo = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, new Color(35, 35, 40));

            Paragraph cabecalhoTitulo = new Paragraph("Histórico de sorteios — " + tituloEvento, titulo);
            cabecalhoTitulo.setSpacingAfter(4);
            document.add(cabecalhoTitulo);
            Paragraph geradoEm = new Paragraph("Gerado em " + LocalDateTime.now().format(DATA_HORA)
                    + " · " + resultados.size() + " registro(s)", subtitulo);
            geradoEm.setSpacingAfter(12);
            document.add(geradoEm);

            if (resultados.isEmpty()) {
                document.add(new Paragraph("Nenhum sorteio registrado para este evento.", corpo));
            } else {
                PdfPTable tabela = new PdfPTable(new float[]{0.55f, 1.15f, 1.4f, 1.75f, 0.65f, 2.1f, 1.0f, 1.15f});
                tabela.setWidthPercentage(100);
                tabela.setHeaderRows(1);
                String[] colunas = {"Rodada", "Prêmio", "Vencedor", "E-mail", "Freq.", "Critério", "Status", "Data"};
                for (String coluna : colunas) {
                    PdfPCell celula = new PdfPCell(new Phrase(coluna, cabecalho));
                    celula.setBackgroundColor(new Color(237, 231, 248));
                    celula.setPadding(5);
                    celula.setHorizontalAlignment(Element.ALIGN_LEFT);
                    tabela.addCell(celula);
                }
                for (SorteioResultado resultado : resultados) {
                    adicionarCelulaPdf(tabela, numeroRodada(resultado), corpo);
                    adicionarCelulaPdf(tabela, valorOuVazio(resultado.getPremioRodada()), corpo);
                    adicionarCelulaPdf(tabela, valorOuVazio(resultado.getNomeVencedor()), corpo);
                    adicionarCelulaPdf(tabela, valorOuVazio(resultado.getEmailVencedor()), corpo);
                    adicionarCelulaPdf(tabela, formatarPercentual(resultado.getPercentualFrequencia()), corpo);
                    adicionarCelulaPdf(tabela, resultado.getCriterioAplicado(), corpo);
                    adicionarCelulaPdf(tabela, resultado.getStatusExibicao(), corpo);
                    adicionarCelulaPdf(tabela, formatarDataHora(resultado.getRealizadoEm()), corpo);
                }
                document.add(tabela);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o PDF do histórico de sorteios.", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return saida.toByteArray();
    }

    private void adicionarCelulaPdf(PdfPTable tabela, String texto, Font fonte) {
        PdfPCell celula = new PdfPCell(new Phrase(texto == null ? "" : texto, fonte));
        celula.setPadding(4);
        celula.setHorizontalAlignment(Element.ALIGN_LEFT);
        tabela.addCell(celula);
    }

    private String numeroRodada(SorteioResultado resultado) {
        return resultado.getRodada() == null ? "—" : String.valueOf(resultado.getRodada());
    }

    private String valorOuVazio(String valor) {
        return valor == null ? "" : valor;
    }

    private String formatarPercentual(Double percentual) {
        if (percentual == null) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f%%", percentual);
    }

    private String formatarDataHora(LocalDateTime data) {
        return data == null ? "—" : data.format(DATA_HORA);
    }

    private String formatarDataExportacao(LocalDateTime data) {
        return data == null ? "" : data.format(DATA_HORA_EXPORTACAO);
    }

    private String csvEscape(String valor) {
        return "\"" + (valor == null ? "" : valor.replace("\"", "\"\"")) + "\"";
    }
}
