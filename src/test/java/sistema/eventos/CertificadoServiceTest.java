package sistema.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.FileStorageService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificadoServiceTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private CertificadoService certificadoService;

    @BeforeEach
    void configurarUrlPublica() {
        ReflectionTestUtils.setField(certificadoService, "urlPublicaConfigurada", "https://seplag.test");
    }

    @Test
    void naoGeraCertificadoAntesDaValidacaoDaParticipacao() {
        Inscricao inscricao = inscricao(StatusInscricao.ATIVO);

        assertThatThrownBy(() -> certificadoService.gerarCertificadoPdf(inscricao))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("confirmação de presença");

        verifyNoInteractions(fileStorageService, templateEngine);
    }

    @Test
    void urlDeValidacaoPublicaMantemPrefixoEv3() {
        String token = UUID.randomUUID().toString();

        String url = ReflectionTestUtils.invokeMethod(
                certificadoService, "construirUrlValidacao", "EV3-" + token);

        assertThat(url).isEqualTo("https://seplag.test/certificados/validar?codigo=EV3-" + token);
    }

    @Test
    void qrCodeContinuaSendoGeradoComoImagemPng() {
        String dataUri = ReflectionTestUtils.invokeMethod(
                certificadoService, "gerarQRCodeBase64",
                "https://seplag.test/certificados/validar?codigo=EV3-" + UUID.randomUUID());

        assertThat(dataUri).startsWith("data:image/png;base64,");
        assertThat(dataUri.length()).isGreaterThan(100);
    }

    @Test
    void certificadoFicaDisponivelAposValidacaoDaParticipacao() throws Exception {
        Inscricao inscricao = inscricao(StatusInscricao.UTILIZADO);
        when(fileStorageService.readImage(anyString())).thenReturn(imagemPng());
        when(templateEngine.process(eq("certificado"), any(Context.class)))
                .thenReturn("<html><body><h1>Certificado</h1></body></html>");

        byte[] pdf = certificadoService.gerarCertificadoPdf(inscricao);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");

        ArgumentCaptor<Context> contexto = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("certificado"), contexto.capture());
        Object qrCode = contexto.getValue().getVariable("qrCodeBase64");
        assertThat(qrCode).isInstanceOf(String.class);
        assertThat((String) qrCode).startsWith("data:image/png;base64,");
    }

    private Inscricao inscricao(StatusInscricao status) {
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento de Teste");
        evento.setCertificadoTemplate("frente.png");
        evento.setCertificadoTemplateVerso("verso.png");

        Usuario participante = new Usuario();
        participante.setId(1L);
        participante.setNome("Participante de Teste");
        participante.setEmail("participante@seplag.mt.gov.br");

        return Inscricao.builder()
                .id(101L)
                .evento(evento)
                .participante(participante)
                .status(status)
                .tokenIngresso(UUID.randomUUID().toString())
                .build();
    }

    private byte[] imagemPng() throws Exception {
        BufferedImage imagem = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            ImageIO.write(imagem, "png", saida);
            return saida.toByteArray();
        }
    }
}
