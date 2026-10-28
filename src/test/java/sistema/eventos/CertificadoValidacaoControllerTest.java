package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import sistema.eventos.controller.CertificadoValidacaoController;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.InscricaoRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificadoValidacaoControllerTest {

    @Mock
    private InscricaoRepository inscricaoRepository;

    @InjectMocks
    private CertificadoValidacaoController controller;

    @Test
    void validacaoPublicaAceitaCodigoEv3DeParticipacaoValidada() {
        String token = UUID.randomUUID().toString();
        Inscricao inscricao = inscricao(token, StatusInscricao.UTILIZADO);
        when(inscricaoRepository.findByTokenIngressoComRelacoes(token)).thenReturn(Optional.of(inscricao));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.validar("EV3-" + token, model, new MockHttpServletResponse());

        assertThat(view).isEqualTo("validar-certificado");
        assertThat(model.getAttribute("valido")).isEqualTo(true);
        assertThat(model.getAttribute("participante")).isEqualTo("Participante de Teste");
        assertThat(model.getAttribute("evento")).isEqualTo("Evento de Teste");
        assertThat(model.getAttribute("cargaHoraria")).isEqualTo(8);
    }

    @Test
    void validacaoPublicaRejeitaCodigoEv3DeParticipacaoAindaNaoValidada() {
        String token = UUID.randomUUID().toString();
        Inscricao inscricao = inscricao(token, StatusInscricao.ATIVO);
        when(inscricaoRepository.findByTokenIngressoComRelacoes(token)).thenReturn(Optional.of(inscricao));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.validar("EV3-" + token, model, new MockHttpServletResponse());

        assertThat(view).isEqualTo("validar-certificado");
        assertThat(model.getAttribute("valido")).isEqualTo(false);
        assertThat(model.getAttribute("participante")).isNull();
    }

    @Test
    void validacaoPublicaRejeitaCodigoSemPrefixoEv3() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.validar(
                "XYZ-" + UUID.randomUUID(), model, new MockHttpServletResponse());

        assertThat(view).isEqualTo("validar-certificado");
        assertThat(model.getAttribute("valido")).isEqualTo(false);
        verifyNoInteractions(inscricaoRepository);
    }

    private Inscricao inscricao(String token, StatusInscricao status) {
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento de Teste");
        evento.setCargaHoraria(8);

        Usuario participante = new Usuario();
        participante.setId(1L);
        participante.setNome("Participante de Teste");
        participante.setEmail("participante@seplag.mt.gov.br");

        return Inscricao.builder()
                .id(101L)
                .evento(evento)
                .participante(participante)
                .status(status)
                .tokenIngresso(token)
                .dataCheckIn(LocalDateTime.of(2026, 10, 10, 8, 30))
                .build();
    }
}
