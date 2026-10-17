package sistema.eventos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusInscricao;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Service
@Slf4j
public class CertificadoService {

    private final TemplateEngine templateEngine;

    @Value("${app.upload.dir:uploads}")
    private String diretorioUploadsConfigurado;

    @Value("${app.public-url:}")
    private String urlPublicaConfigurada;

    public CertificadoService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Transactional(readOnly = true)
    public byte[] gerarCertificadoPdf(Inscricao inscricao) {
        if (inscricao.getStatus() != StatusInscricao.UTILIZADO) {
            throw new NegocioException("O certificado só pode ser emitido após a confirmação de presença.");
        }

        // CORREÇÃO 1: Buscando os campos corretos da Frente e do Verso na Entidade
        String nomeArteFrente = inscricao.getEvento().getCertificadoTemplate();
        String nomeArteVerso = inscricao.getEvento().getCertificadoTemplateVerso();

        if (nomeArteFrente == null || nomeArteFrente.trim().isEmpty()) {
            throw new NegocioException("O organizador ainda não realizou o upload da arte da frente do certificado.");
        }
        if (nomeArteVerso == null || nomeArteVerso.trim().isEmpty()) {
            throw new NegocioException("O organizador ainda não realizou o upload da arte do verso do certificado.");
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 1. Processa a Imagem de Fundo da FRENTE
            Path caminhoFrente = resolverImagemCertificado(nomeArteFrente);
            if (!Files.exists(caminhoFrente) || Files.size(caminhoFrente) > 10L * 1024 * 1024) {
                throw new NegocioException("A imagem de frente do certificado não foi encontrada no servidor.");
            }
            byte[] bytesFrente = Files.readAllBytes(caminhoFrente);
            String extFrente = nomeArteFrente.substring(nomeArteFrente.lastIndexOf(".") + 1).toLowerCase();
            validarExtensaoImagem(extFrente);
            if (extFrente.equals("jpg")) extFrente = "jpeg";
            String base64Frente = "data:image/" + extFrente + ";base64," + Base64.getEncoder().encodeToString(bytesFrente);

            // 2. Processa a Imagem de Fundo do VERSO
            Path caminhoVerso = resolverImagemCertificado(nomeArteVerso);
            if (!Files.exists(caminhoVerso) || Files.size(caminhoVerso) > 10L * 1024 * 1024) {
                throw new NegocioException("A imagem do verso do certificado não foi encontrada no servidor.");
            }
            byte[] bytesVerso = Files.readAllBytes(caminhoVerso);
            String extVerso = nomeArteVerso.substring(nomeArteVerso.lastIndexOf(".") + 1).toLowerCase();
            validarExtensaoImagem(extVerso);
            if (extVerso.equals("jpg")) extVerso = "jpeg";
            String base64Verso = "data:image/" + extVerso + ";base64," + Base64.getEncoder().encodeToString(bytesVerso);

            // 3. Gera dinamicamente o QR Code do Verso
            String tokenValidacao = "EV3-" + inscricao.getTokenIngresso();
            UriComponentsBuilder urlBase = urlPublicaConfigurada == null || urlPublicaConfigurada.isBlank()
                    ? ServletUriComponentsBuilder.fromCurrentContextPath()
                    : UriComponentsBuilder.fromUriString(urlPublicaConfigurada.trim().replaceAll("/+$", ""));
            String urlValidacao = urlBase.path("/certificados/validar")
                    .queryParam("codigo", tokenValidacao).build().encode().toUriString();
            String base64QRCode = gerarQRCodeBase64(urlValidacao);

            // 4. Monta o Contexto de variáveis do Thymeleaf
            Context context = new Context();
            context.setVariable("usuario", inscricao.getParticipante());
            context.setVariable("evento", inscricao.getEvento());
            context.setVariable("inscricao", inscricao);
            context.setVariable("imagemFundoBase64", base64Frente);
            context.setVariable("imagemVersoBase64", base64Verso);
            context.setVariable("qrCodeBase64", base64QRCode);

            // 5. Processa o HTML através do Engine
            String htmlProcessado = templateEngine.process("certificado", context);

            // 6. Compilação final para PDF via Flying Saucer
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlProcessado);
            renderer.layout();
            renderer.createPDF(out);

            return out.toByteArray();

        } catch (NegocioException ne) {
            throw ne;
        } catch (Exception e) {
            log.error("Falha interna ao renderizar o certificado da inscrição {}.", inscricao.getId(), e);
            throw new NegocioException("Não foi possível gerar o certificado no momento.");
        }
    }

    private String gerarQRCodeBase64(String texto) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(texto, BarcodeFormat.QR_CODE, 250, 250);

        try (ByteArrayOutputStream pngOut = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOut);
            byte[] qrBytes = pngOut.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes);
        }
    }

    private Path resolverImagemCertificado(String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9._-]{1,255}") || filename.contains("..")) {
            throw new NegocioException("Arquivo de certificado inválido.");
        }
        Path base = Paths.get(diretorioUploadsConfigurado).toAbsolutePath().normalize();
        Path path = base.resolve(filename).normalize();
        if (!path.startsWith(base) || !base.equals(path.getParent())) throw new NegocioException("Arquivo de certificado inválido.");
        return path;
    }

    private void validarExtensaoImagem(String extension) {
        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")) {
            throw new NegocioException("O template do certificado precisa ser uma imagem PNG ou JPEG.");
        }
    }
}
