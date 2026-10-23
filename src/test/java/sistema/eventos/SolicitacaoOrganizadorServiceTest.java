package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.password.PasswordEncoder;
import sistema.eventos.dto.RequestSolicitacaoOrganizadorDto;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.EmailService;
import sistema.eventos.service.PresencaService;
import sistema.eventos.service.UsuarioService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoOrganizadorServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SolicitacaoRepository solicitacaoRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private InscricaoRepository inscricaoRepository;

    @Mock
    private PresencaService presencaService;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void devePersistirOrgaoNaSolicitacaoDeOrganizador() {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setEmail("maria@seplag.mt.gov.br");
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);

        when(usuarioRepository.findByEmailIgnoreCase("maria@seplag.mt.gov.br"))
                .thenReturn(Optional.of(usuario));
        when(solicitacaoRepository.findByUsuarioId(10L)).thenReturn(Optional.empty());

        RequestSolicitacaoOrganizadorDto dto = RequestSolicitacaoOrganizadorDto.builder()
                .nomeCompleto("Maria Silva")
                .matricula("123456")
                .emailInstitucional("maria@seplag.mt.gov.br")
                .justificativa("Atuo na organização de eventos institucionais.")
                .orgao("  SEPLAG-MT  ")
                .build();

        usuarioService.solicitarAcessoOrganizador("maria@seplag.mt.gov.br", dto);

        ArgumentCaptor<SolicitacaoOrganizador> captor = ArgumentCaptor.forClass(SolicitacaoOrganizador.class);
        verify(solicitacaoRepository).save(captor.capture());
        assertEquals("SEPLAG-MT", captor.getValue().getOrgao());
        assertEquals(StatusSolicitacao.PENDENTE, captor.getValue().getStatus());
        verify(emailService).enviarSolicitacaoParaAdmin(usuario);
    }
}
