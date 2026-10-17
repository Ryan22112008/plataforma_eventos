package sistema.eventos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sistema.eventos.exception.NegocioException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16_000_000L;

    @Value("${app.upload.dir:uploads}")
    private String uploadDirectory;

    public String storeImage(MultipartFile file, String purpose) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new NegocioException("Envie uma imagem válida de até 10 MB.");
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageInput == null) throw new NegocioException("O arquivo enviado não é uma imagem válida.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new NegocioException("Use uma imagem PNG ou JPEG válida.");

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) {
                    throw new NegocioException("Somente imagens PNG ou JPEG são aceitas.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 6000 || height > 6000 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new NegocioException("A imagem excede as dimensões permitidas.");
                }

                BufferedImage image = reader.read(0);
                if (image == null) throw new NegocioException("Não foi possível decodificar a imagem enviada.");
                try (ByteArrayOutputStream sanitized = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(image, "png", sanitized) || sanitized.size() > MAX_IMAGE_BYTES) {
                        throw new NegocioException("A imagem processada excede o limite permitido.");
                    }
                    Path root = uploadRoot();
                    Files.createDirectories(root);
                    String safePurpose = purpose == null ? "imagem" : purpose.replaceAll("[^a-zA-Z0-9_-]", "");
                    String filename = UUID.randomUUID() + "_" + safePurpose + ".png";
                    Path destination = root.resolve(filename).normalize();
                    if (!destination.startsWith(root)) throw new NegocioException("Destino de arquivo inválido.");
                    Files.write(destination, sanitized.toByteArray());
                    return filename;
                } finally {
                    image.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new NegocioException("Não foi possível processar a imagem enviada.", e);
        }
    }

    public Path uploadRoot() {
        return Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    public void deleteStoredImage(String filename) throws IOException {
        if (filename == null || filename.isBlank()) return;
        Path root = uploadRoot();
        Path candidate = root.resolve(filename).normalize();
        if (!candidate.startsWith(root) || !candidate.getParent().equals(root)) {
            throw new IOException("Nome de arquivo armazenado inválido.");
        }
        Files.deleteIfExists(candidate);
    }
}
