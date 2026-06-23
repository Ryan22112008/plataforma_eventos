package sistema.eventos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
public class CertificadoService {

    private final TemplateEngine templateEngine;

    @Value("${app.upload.dir:uploads}")
    private String diretorioUploadsConfigurado;

    public CertificadoService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Transactional(readOnly = true)
    public byte[] gerarCertificadoPdf(Inscricao inscricao) {
        if (inscricao.getStatus() != StatusInscricao.UTILIZADO &&
                inscricao.getStatus() != StatusInscricao.ATIVO) {
            throw new NegocioException("O participante não possui uma inscrição válida para emitir o certificado.");
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

            // RESOLUÇÃO DE CAMINHO SÊNIOR: Unifica a raiz e evita caminhos colados sem barra
            Path diretorioBase = Paths.get(diretorioUploadsConfigurado).toAbsolutePath().normalize();

            // 1. Processa a Imagem de Fundo da FRENTE
            Path caminhoFrente = diretorioBase.resolve(nomeArteFrente);
            if (!Files.exists(caminhoFrente)) {
                throw new NegocioException("A imagem de frente do certificado não foi encontrada no servidor.");
            }
            byte[] bytesFrente = Files.readAllBytes(caminhoFrente);
            String extFrente = nomeArteFrente.substring(nomeArteFrente.lastIndexOf(".") + 1).toLowerCase();
            if (extFrente.equals("jpg")) extFrente = "jpeg";
            String base64Frente = "data:image/" + extFrente + ";base64," + Base64.getEncoder().encodeToString(bytesFrente);

            // 2. Processa a Imagem de Fundo do VERSO
            Path caminhoVerso = diretorioBase.resolve(nomeArteVerso);
            if (!Files.exists(caminhoVerso)) {
                throw new NegocioException("A imagem do verso do certificado não foi encontrada no servidor.");
            }
            byte[] bytesVerso = Files.readAllBytes(caminhoVerso);
            String extVerso = nomeArteVerso.substring(nomeArteVerso.lastIndexOf(".") + 1).toLowerCase();
            if (extVerso.equals("jpg")) extVerso = "jpeg";
            String base64Verso = "data:image/" + extVerso + ";base64," + Base64.getEncoder().encodeToString(bytesVerso);

            // 3. Gera dinamicamente o QR Code do Verso
            String tokenValidacao = "EV3-" + inscricao.getTokenIngresso();
            String urlValidacao = "https://seusistema.com/validar?code=" + tokenValidacao;
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
            throw new NegocioException("Erro de infraestrutura ao processar o motor Thymeleaf/Flying-Saucer: " + e.getMessage(), e);
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
}