package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import sistema.eventos.controller.PresencaController;
import sistema.eventos.dto.AlteracaoPresencaRequestDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.StatusPresenca;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.PresencaService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresencaControllerLoteTest {

    private static final Long EVENTO_ID = 10L;
    private static final Long ALUNO_ID = 1L;
    private static final Long SESSAO_ID = 100L;

    @Mock
    private PresencaService presencaService;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private InscricaoRepository inscricaoRepository;

    @Mock
    private SessaoRepository sessaoRepository;

    @Mock
    private PresencaRepository presencaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PresencaController presencaController;

    @Test
    void usuarioNaoAutenticadoRecebeNaoAutorizado() {
        ResponseEntity<Map<String, Object>> resposta = presencaController.alterarStatusEmLote(
                EVENTO_ID, List.of(alteracao()), null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody()).containsKey("erro");
    }

    @Test
    void organizadorSalvaLoteERecebeQuantidadeDeAlteracoes() {
        when(presencaService.atualizarStatusPresencasEmLote(eq(EVENTO_ID), anyList(), eq("dono@seplag.mt.gov.br")))
                .thenReturn(1);

        ResponseEntity<Map<String, Object>> resposta = presencaController.alterarStatusEmLote(
                EVENTO_ID, List.of(alteracao()), usuario("dono@seplag.mt.gov.br"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsEntry("salvas", 1);
        verify(presencaService).atualizarStatusPresencasEmLote(
                eq(EVENTO_ID), anyList(), eq("dono@seplag.mt.gov.br"));
    }

    @Test
    void loteInvalidoRetornaErroDeNegocio() {
        when(presencaService.atualizarStatusPresencasEmLote(eq(EVENTO_ID), anyList(), eq("dono@seplag.mt.gov.br")))
                .thenThrow(new NegocioException("A sessão informada não pertence a este evento."));

        ResponseEntity<Map<String, Object>> resposta = presencaController.alterarStatusEmLote(
                EVENTO_ID, List.of(alteracao()), usuario("dono@seplag.mt.gov.br"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).containsEntry(
                "erro", "A sessão informada não pertence a este evento.");
    }

    @Test
    void organizadorSemAcessoRecebeProibido() {
        when(presencaService.atualizarStatusPresencasEmLote(eq(EVENTO_ID), anyList(), eq("outro@seplag.mt.gov.br")))
                .thenThrow(new AccessDeniedException("Você não tem acesso a este evento."));

        ResponseEntity<Map<String, Object>> resposta = presencaController.alterarStatusEmLote(
                EVENTO_ID, List.of(alteracao()), usuario("outro@seplag.mt.gov.br"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).containsKey("erro");
    }

    private AlteracaoPresencaRequestDto alteracao() {
        return new AlteracaoPresencaRequestDto(ALUNO_ID, SESSAO_ID, StatusPresenca.PRESENTE);
    }

    private UserDetails usuario(String email) {
        return User.withUsername(email).password("senha").roles("ORGANIZADOR").build();
    }
}
