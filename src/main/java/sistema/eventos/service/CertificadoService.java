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
import sistema.eventos.repository.InscricaoRepository;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
@Slf4j
public class CertificadoService {

    private final TemplateEngine templateEngine;
    private final FileStorageService fileStorageService;
    private final InscricaoRepository inscricaoRepository;

    @Value("${app.public-url:}")
    private String urlPublicaConfigurada;

    public CertificadoService(TemplateEngine templateEngine, FileStorageService fileStorageService, InscricaoRepository inscricaoRepository) {
        this.templateEngine = templateEngine;
        this.fileStorageService = fileStorageService;
        this.inscricaoRepository = inscricaoRepository;
    }

    @Transactional
    public byte[] gerarCertificadoPdf(Inscricao inscricao) {
        if (inscricao.getStatus() != StatusInscricao.UTILIZADO) {
            throw new NegocioException("A declaração de presença só pode ser emitida após a confirmação de presença.");
        }

        // CORREÇÃO 1: Buscando os campos corretos da Frente e do Verso na Entidade
        String nomeArteFrente = inscricao.getEvento().getCertificadoTemplate();
        String nomeArteVerso = inscricao.getEvento().getCertificadoTemplateVerso();

        if (nomeArteFrente == null || nomeArteFrente.trim().isEmpty()) {
            throw new NegocioException("O organizador ainda não realizou o upload da arte da frente da declaração de presença.");
        }
        if (nomeArteVerso == null || nomeArteVerso.trim().isEmpty()) {
            throw new NegocioException("O organizador ainda não realizou o upload da arte do verso da declaração de presença.");
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 1. Processa a Imagem de Fundo da FRENTE
            byte[] bytesFrente = fileStorageService.readImage(nomeArteFrente);
            String extFrente = extensionOf(nomeArteFrente);
            validarExtensaoImagem(extFrente);
            if (extFrente.equals("jpg")) extFrente = "jpeg";
            String base64Frente = "data:image/" + extFrente + ";base64," + Base64.getEncoder().encodeToString(bytesFrente);

            // 2. Processa a Imagem de Fundo do VERSO
            byte[] bytesVerso = fileStorageService.readImage(nomeArteVerso);
            String extVerso = extensionOf(nomeArteVerso);
            validarExtensaoImagem(extVerso);
            if (extVerso.equals("jpg")) extVerso = "jpeg";
            String base64Verso = "data:image/" + extVerso + ";base64," + Base64.getEncoder().encodeToString(bytesVerso);

            // 3. Gera dinamicamente o QR Code do Verso
            String tokenValidacao = "EV3-" + inscricao.getTokenIngresso();
            String urlValidacao = construirUrlValidacao(tokenValidacao);
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

            if (inscricao.getId() != null) inscricaoRepository.registrarCertificadoGerado(inscricao.getId());

            return out.toByteArray();

        } catch (NegocioException ne) {
            throw ne;
        } catch (Exception e) {
<<<<<<< HEAD
            log.error("Falha interna ao renderizar o certificado da inscrição {}.", inscricao.getId(), e);
=======
            log.error("Falha interna ao renderizar a declaração de presença da inscrição {}.", inscricao.getId(), e);
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
            throw new NegocioException("Não foi possível gerar a declaração de presença no momento.");
        }
    }

    private String construirUrlValidacao(String codigo) {
        UriComponentsBuilder urlBase = urlPublicaConfigurada == null || urlPublicaConfigurada.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath()
                : UriComponentsBuilder.fromUriString(urlPublicaConfigurada.trim().replaceAll("/+$", ""));
        return urlBase.path("/declaracoes/validar")
                .queryParam("codigo", codigo).build().encode().toUriString();
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

    private String extensionOf(String reference) {
        String path = reference;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) path = path.substring(0, queryIndex);
        int slashIndex = path.lastIndexOf('/');
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex <= slashIndex || dotIndex == path.length() - 1) {
<<<<<<< HEAD
            throw new NegocioException("Arquivo da declaração de presença inválido.");
=======
            throw new NegocioException("Arquivo de declaração de presença inválido.");
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
        }
        return path.substring(dotIndex + 1).toLowerCase();
    }

    private void validarExtensaoImagem(String extension) {
        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")) {
            throw new NegocioException("O template da declaração de presença precisa ser uma imagem PNG ou JPEG.");
        }
    }
}
