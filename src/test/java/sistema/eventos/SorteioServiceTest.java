package sistema.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.SorteioService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SorteioServiceTest {

    private static final Long EVENTO_ID = 1L;
    private static final String AUTOR = "organizador@seplag.mt.gov.br";

    @Mock
    private InscricaoService inscricaoService;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private SorteioRodadaRepository rodadaRepository;

    @Mock
    private SorteioResultadoRepository resultadoRepository;

    @Mock
    private SorteioAuditoriaRepository auditoriaRepository;

    private SorteioService sorteioService;

    private Evento evento;

    @BeforeEach
    void preparar() {
        sorteioService = new SorteioService(inscricaoService, eventoRepository, rodadaRepository,
                resultadoRepository, auditoriaRepository);
        evento = new Evento();
        evento.setId(EVENTO_ID);
        evento.setTitulo("Evento de Teste");
        evento.setSorteioFrequenciaMinima(75);
        evento.setSorteioNumeroRodada(0);

        lenient().when(eventoRepository.findByIdForUpdate(EVENTO_ID)).thenReturn(Optional.of(evento));
        lenient().when(resultadoRepository.findByEventoIdOrderByRealizadoEmDescIdDesc(EVENTO_ID))
                .thenReturn(List.of());
        lenient().when(rodadaRepository.maiorNumeroRodada(EVENTO_ID)).thenReturn(null);
        lenient().when(rodadaRepository.save(any(SorteioRodada.class))).thenAnswer(invocation -> {
            SorteioRodada rodada = invocation.getArgument(0);
            if (rodada.getId() == null) {
                rodada.setId(99L);
            }
            return rodada;
        });
        lenient().when(resultadoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(resultadoRepository.save(any(SorteioResultado.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void realizaSorteioComSnapshotDaRodadaEAuditoria() {
        when(inscricaoService.listarInscritosComFrequencia(EVENTO_ID)).thenReturn(List.of(
                elegivel("Ana", "ana@seplag.mt.gov.br", 100.0),
                elegivel("Bruno", "bruno@seplag.mt.gov.br", 90.0),
                elegivel("Carla", "carla@seplag.mt.gov.br", 80.0)));

        SorteioRodada rodada = sorteioService.realizarSorteio(EVENTO_ID, 2, "  Camiseta  ", AUTOR);

        assertThat(rodada.getNumeroRodada()).isEqualTo(1);
        assertThat(rodada.getPremio()).isEqualTo("Camiseta");
        assertThat(rodada.getPercentualMinimo()).isEqualTo(75);
        assertThat(rodada.getTotalElegiveis()).isEqualTo(3);
        assertThat(rodada.getQuantidadeSorteada()).isEqualTo(2);
        assertThat(rodada.getRealizadoPor()).isEqualTo(AUTOR);
        assertThat(rodada.getStatus()).isEqualTo(StatusRodadaSorteio.REALIZADA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SorteioResultado>> captor = ArgumentCaptor.forClass(List.class);
        verify(resultadoRepository).saveAll(captor.capture());
        List<SorteioResultado> vencedores = captor.getValue();
        assertThat(vencedores).hasSize(2);
        assertThat(vencedores).extracting(SorteioResultado::getNomeVencedor).doesNotHaveDuplicates();
        assertThat(vencedores).allSatisfy(resultado -> {
            assertThat(resultado.getRodadaSorteio()).isSameAs(rodada);
            assertThat(resultado.getStatusVencedorEfetivo()).isEqualTo(StatusVencedorSorteio.PENDENTE);
            assertThat(resultado.getRodada()).isEqualTo(1);
        });

        assertThat(evento.getSorteioNumeroRodada()).isEqualTo(1);
        assertThat(evento.getSorteioVencedorNome()).isEqualTo(vencedores.getFirst().getNomeVencedor());

        SorteioAuditoria auditoria = ultimaAuditoria();
        assertThat(auditoria.getAcao()).isEqualTo(AcaoAuditoriaSorteio.SORTEIO_REALIZADO);
        assertThat(auditoria.getDetalhes())
                .contains("Rodada 1 realizada")
                .contains("2 vencedor(es) entre 3 elegíveis")
                .contains("Rodada 1 realizada");
    }

    @Test
    void bloqueiaQuantidadeMaiorQueElegiveis() {
        when(inscricaoService.listarInscritosComFrequencia(EVENTO_ID)).thenReturn(List.of(
                elegivel("Ana", "ana@seplag.mt.gov.br", 100.0),
                elegivel("Bruno", "bruno@seplag.mt.gov.br", 90.0)));

        assertThatThrownBy(() -> sorteioService.realizarSorteio(EVENTO_ID, 3, null, AUTOR))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Há apenas 2 participante(s) elegível(is)");

        verify(resultadoRepository, never()).saveAll(any());
    }

    @Test
    void listaElegiveisIgnorandoVencedoresAtivosCanceladosEBaixaFrequencia() {
        SorteioRodada ativa = rodada(1, StatusRodadaSorteio.REALIZADA, "Fone");
        SorteioRodada cancelada = rodada(2, StatusRodadaSorteio.CANCELADA, "Livro");

        SorteioResultado vencedorAtivo = resultado("Ana", "ana@seplag.mt.gov.br", ativa,
                StatusVencedorSorteio.PENDENTE);
        SorteioResultado vencedorCancelado = resultado("Carla", "carla@seplag.mt.gov.br", cancelada,
                StatusVencedorSorteio.PENDENTE);
        when(resultadoRepository.findByEventoIdOrderByRealizadoEmDescIdDesc(EVENTO_ID))
                .thenReturn(List.of(vencedorAtivo, vencedorCancelado));
        when(inscricaoService.listarInscritosComFrequencia(EVENTO_ID)).thenReturn(List.of(
                elegivel("Ana", "ana@seplag.mt.gov.br", 100.0),
                elegivel("Bruno", "bruno@seplag.mt.gov.br", 80.0),
                elegivel("Carla", "carla@seplag.mt.gov.br", 90.0),
                elegivel("Diego", "diego@seplag.mt.gov.br", 70.0),
                elegivel("Elisa", "elisa@seplag.mt.gov.br", 75.0)));

        List<InscritoFrequenciaDto> elegiveis = sorteioService.listarElegiveisDisponiveis(evento);

        assertThat(elegiveis).extracting(InscritoFrequenciaDto::getNomeParticipante)
                .containsExactly("Bruno", "Carla", "Elisa");
    }

    @Test
    void repoeVencedorAusenteComMesmoPremioEVinculoComRodadaOriginal() {
        SorteioRodada original = rodada(1, StatusRodadaSorteio.REALIZADA, "Fone Bluetooth");
        SorteioResultado ausente = resultado("Ana", "ana@seplag.mt.gov.br", original,
                StatusVencedorSorteio.AUSENTE);
        ausente.setId(50L);
        when(resultadoRepository.findById(50L)).thenReturn(Optional.of(ausente));
        when(inscricaoService.listarInscritosComFrequencia(EVENTO_ID)).thenReturn(List.of(
                elegivel("Bruno", "bruno@seplag.mt.gov.br", 100.0),
                elegivel("Diego", "diego@seplag.mt.gov.br", 90.0)));
        when(rodadaRepository.maiorNumeroRodada(EVENTO_ID)).thenReturn(1);
        SorteioResultado novo = new SorteioResultado();
        novo.setId(51L);
        novo.setNomeVencedor("Bruno");
        when(resultadoRepository.findByRodadaSorteioIdOrderByIdAsc(99L)).thenReturn(List.of(novo));

        SorteioRodada reposicao = sorteioService.sortearNovamente(EVENTO_ID, 50L, AUTOR);

        assertThat(reposicao.getNumeroRodada()).isEqualTo(2);
        assertThat(reposicao.getRodadaOrigem()).isSameAs(original);
        assertThat(reposicao.getPremio()).isEqualTo("Fone Bluetooth");
        assertThat(reposicao.getQuantidadeSorteada()).isEqualTo(1);
        assertThat(ausente.getSubstituidoPor()).isSameAs(novo);
        assertThat(ultimaAuditoria().getAcao()).isEqualTo(AcaoAuditoriaSorteio.SORTEIO_REPESCAGEM);
    }

    @Test
    void reposicaoSomenteAceitaVencedorAusente() {
        SorteioRodada original = rodada(1, StatusRodadaSorteio.REALIZADA, "Fone");
        SorteioResultado pendente = resultado("Ana", "ana@seplag.mt.gov.br", original,
                StatusVencedorSorteio.PENDENTE);
        pendente.setId(50L);
        when(resultadoRepository.findById(50L)).thenReturn(Optional.of(pendente));

        assertThatThrownBy(() -> sorteioService.sortearNovamente(EVENTO_ID, 50L, AUTOR))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("só é permitida para vencedores marcados como Ausente");
    }

    @Test
    void desfazSomenteARodadaAtivaMaisRecente() {
        SorteioRodada rodada = rodada(2, StatusRodadaSorteio.REALIZADA, "Fone");
        when(rodadaRepository.findFirstByEventoIdAndStatusOrderByNumeroRodadaDesc(
                EVENTO_ID, StatusRodadaSorteio.REALIZADA)).thenReturn(Optional.of(rodada));
        when(resultadoRepository.findByRodadaSorteioIdOrderByIdAsc(rodada.getId()))
                .thenReturn(List.of(resultado("Ana", "ana@seplag.mt.gov.br", rodada,
                        StatusVencedorSorteio.PENDENTE)));

        sorteioService.desfazerUltimaRodada(EVENTO_ID, 2L, AUTOR);

        assertThat(rodada.getStatus()).isEqualTo(StatusRodadaSorteio.CANCELADA);
        SorteioAuditoria auditoria = ultimaAuditoria();
        assertThat(auditoria.getAcao()).isEqualTo(AcaoAuditoriaSorteio.RODADA_DESFEITA);
        assertThat(auditoria.getDetalhes()).contains("Rodada 2 desfeita");
    }

    @Test
    void naoPermiteDesfazerRodadaAnterior() {
        SorteioRodada rodada = rodada(2, StatusRodadaSorteio.REALIZADA, "Fone");
        when(rodadaRepository.findFirstByEventoIdAndStatusOrderByNumeroRodadaDesc(
                EVENTO_ID, StatusRodadaSorteio.REALIZADA)).thenReturn(Optional.of(rodada));

        assertThatThrownBy(() -> sorteioService.desfazerUltimaRodada(EVENTO_ID, 1L, AUTOR))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Somente a rodada ativa mais recente");

        assertThat(rodada.getStatus()).isEqualTo(StatusRodadaSorteio.REALIZADA);
    }

    @Test
    void filtraHistoricoPorNomeEEmail() {
        SorteioRodada rodada = rodada(1, StatusRodadaSorteio.REALIZADA, "Camiseta");
        SorteioResultado ana = resultado("Ana Souza", "ana@seplag.mt.gov.br", rodada,
                StatusVencedorSorteio.PENDENTE);
        SorteioResultado bruno = resultado("Bruno Lima", "bruno@seplag.mt.gov.br", rodada,
                StatusVencedorSorteio.AUSENTE);

        List<SorteioResultado> todos = List.of(ana, bruno);

        assertThat(sorteioService.filtrar(todos, "ANA")).containsExactly(ana);
        assertThat(sorteioService.filtrar(todos, "bruno@seplag")).containsExactly(bruno);
        assertThat(sorteioService.filtrar(todos, "inexistente")).isEmpty();
        assertThat(sorteioService.filtrar(todos, null)).containsExactly(ana, bruno);
    }

    @Test
    void exportaCsvEPdfComCamposCompletos() {
        SorteioRodada rodada = rodada(3, StatusRodadaSorteio.REALIZADA, "Fone Bluetooth");
        SorteioResultado resultado = resultado("Ana Souza", "ana@seplag.mt.gov.br", rodada,
                StatusVencedorSorteio.AUSENTE);
        resultado.setPercentualFrequencia(87.5);
        resultado.setRealizadoEm(LocalDateTime.of(2026, 11, 15, 14, 30, 25));

        String csv = sorteioService.gerarCsv(List.of(resultado));
        assertThat(csv).startsWith("\uFEFFRodada;Vencedor;E-mail;Frequência;Data");
        assertThat(csv).contains("\"3\";\"Ana Souza\";\"ana@seplag.mt.gov.br\";\"87.5%\"");
        assertThat(csv).contains("\"15/11/2026 14:30:25\"");

        byte[] pdf = sorteioService.gerarPdf("Evento de Teste", List.of(resultado));
        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void validaPercentualMinimoAntesDeSalvar() {
        assertThatThrownBy(() -> sorteioService.configurarFrequencia(EVENTO_ID, 101, AUTOR))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("entre 0 e 100");
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void registraAuditoriaAoAlterarPercentual() {
        when(eventoRepository.findByIdForUpdate(EVENTO_ID)).thenReturn(Optional.of(evento));

        boolean alterado = sorteioService.configurarFrequencia(EVENTO_ID, 80, AUTOR);

        assertThat(alterado).isTrue();
        assertThat(evento.getSorteioFrequenciaMinima()).isEqualTo(80);
        SorteioAuditoria auditoria = ultimaAuditoria();
        assertThat(auditoria.getAcao()).isEqualTo(AcaoAuditoriaSorteio.PERCENTUAL_ALTERADO);
        assertThat(auditoria.getDetalhes()).contains("de 75% para 80%");
    }

    private SorteioAuditoria ultimaAuditoria() {
        ArgumentCaptor<SorteioAuditoria> captor = ArgumentCaptor.forClass(SorteioAuditoria.class);
        verify(auditoriaRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().getLast();
    }

    private InscritoFrequenciaDto elegivel(String nome, String email, Double percentual) {
        return InscritoFrequenciaDto.builder()
                .nomeParticipante(nome)
                .emailParticipante(email)
                .percentualFrequencia(percentual)
                .build();
    }

    private SorteioRodada rodada(int numero, StatusRodadaSorteio status, String premio) {
        SorteioRodada rodada = new SorteioRodada();
        rodada.setId((long) numero);
        rodada.setNumeroRodada(numero);
        rodada.setStatus(status);
        rodada.setPremio(premio);
        rodada.setEvento(evento);
        rodada.setTotalElegiveis(10);
        rodada.setPercentualMinimo(75);
        rodada.setQuantidadeSorteada(1);
        rodada.setRealizadoEm(LocalDateTime.now());
        return rodada;
    }

    private SorteioResultado resultado(String nome, String email, SorteioRodada rodada,
                                       StatusVencedorSorteio status) {
        SorteioResultado resultado = new SorteioResultado();
        resultado.setEvento(evento);
        resultado.setNomeVencedor(nome);
        resultado.setEmailVencedor(email);
        resultado.setRodada(rodada.getNumeroRodada());
        resultado.setRodadaSorteio(rodada);
        resultado.setStatusVencedor(status);
        resultado.setPercentualFrequencia(100.0);
        resultado.setRealizadoEm(LocalDateTime.now());
        return resultado;
    }
}
