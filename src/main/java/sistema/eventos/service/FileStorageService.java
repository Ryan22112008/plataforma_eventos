package sistema.eventos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import sistema.eventos.exception.NegocioException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final System.Logger LOGGER = System.getLogger(FileStorageService.class.getName());
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16_000_000L;

    @Value("${app.upload.dir:uploads}")
    private String uploadDirectory;

    private final Cloudinary cloudinary;
    private final boolean externalStorageRequired;

    public FileStorageService(@Value("${CLOUDINARY_URL:}") String cloudinaryUrl,
                              @Value("${REQUIRE_EXTERNAL_STORAGE:false}") boolean requireExternalStorage,
                              @Value("${RENDER_SERVICE_ID:}") String renderServiceId) {
        Cloudinary configuredCloudinary = null;
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank()) {
            try {
                configuredCloudinary = new Cloudinary(cloudinaryUrl.trim());
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.ERROR, "CLOUDINARY_URL está configurada em formato inválido; uploads ficarão bloqueados.", e);
            }
        }
        this.cloudinary = configuredCloudinary;
        boolean runningOnRender = renderServiceId != null && !renderServiceId.isBlank();
        this.externalStorageRequired = requireExternalStorage || runningOnRender;
    }

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
                    String safePurpose = purpose == null ? "imagem" : purpose.replaceAll("[^a-zA-Z0-9_-]", "");
                    String filename = UUID.randomUUID() + "_" + safePurpose;
                    byte[] imageBytes = sanitized.toByteArray();
                    if (cloudinary == null && externalStorageRequired) {
                        throw new NegocioException("Configure o armazenamento de imagens no ambiente antes de enviar arquivos.");
                    }
                    if (cloudinary != null) {
                        Map<?, ?> result = cloudinary.uploader().upload(imageBytes, ObjectUtils.asMap(
                                "folder", "eventos-seplag",
                                "public_id", filename,
                                "overwrite", false,
                                "resource_type", "image"));
                        Object secureUrl = result.get("secure_url");
                        if (!(secureUrl instanceof String url) || !url.startsWith("https://res.cloudinary.com/")) {
                            throw new IOException("O armazenamento remoto não retornou uma URL segura.");
                        }
                        return url;
                    }

                    Path root = uploadRoot();
                    Files.createDirectories(root);
                    Path destination = root.resolve(filename + ".png").normalize();
                    if (!destination.startsWith(root)) throw new NegocioException("Destino de arquivo inválido.");
                    Files.write(destination, imageBytes);
                    return destination.getFileName().toString();
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

    public String publicImageUrl(String reference) {
        if (reference == null || reference.isBlank()) return null;
        if (isCloudinaryUrl(reference)) return reference;
        if (!reference.matches("[A-Za-z0-9._-]{1,255}") || reference.contains("..")) return null;
        return "/imagens-eventos/" + reference;
    }

    public byte[] readImage(String reference) throws IOException {
        if (isCloudinaryUrl(reference)) {
            HttpURLConnection connection = (HttpURLConnection) URI.create(reference).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            try {
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300 || connection.getContentLengthLong() > MAX_IMAGE_BYTES) {
                    throw new IOException("Não foi possível obter a imagem remota.");
                }
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_IMAGE_BYTES) throw new IOException("A imagem remota excede o limite permitido.");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                }
            } finally {
                connection.disconnect();
            }
        }

        if (reference == null || !reference.matches("[A-Za-z0-9._-]{1,255}") || reference.contains("..")) {
            throw new IOException("Arquivo de imagem inválido.");
        }
        Path root = uploadRoot();
        Path path = root.resolve(reference).normalize();
        if (!path.startsWith(root) || !root.equals(path.getParent()) || !Files.exists(path) || Files.size(path) > MAX_IMAGE_BYTES) {
            throw new IOException("A imagem não foi encontrada no armazenamento.");
        }
        return Files.readAllBytes(path);
    }

    public void deleteStoredImage(String filename) throws IOException {
        if (filename == null || filename.isBlank()) return;
        if (isCloudinaryUrl(filename)) {
            if (cloudinary == null) throw new IOException("As credenciais do armazenamento remoto não estão configuradas.");
            String publicId = cloudinaryPublicId(filename);
            if (publicId == null) throw new IOException("Referência remota de imagem inválida.");
            try {
                cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
            } catch (RuntimeException e) {
                throw new IOException("Não foi possível remover a imagem do armazenamento remoto.", e);
            }
            return;
        }
        Path root = uploadRoot();
        Path candidate = root.resolve(filename).normalize();
        if (!candidate.startsWith(root) || !candidate.getParent().equals(root)) {
            throw new IOException("Nome de arquivo armazenado inválido.");
        }
        Files.deleteIfExists(candidate);
    }

    private boolean isCloudinaryUrl(String value) {
        if (value == null || !value.startsWith("https://")) return false;
        try {
            URI uri = URI.create(value);
            return "res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null && uri.getPath().contains("/image/upload/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String cloudinaryPublicId(String secureUrl) {
        try {
            String path = URI.create(secureUrl).getPath();
            int marker = path.indexOf("/image/upload/");
            if (marker < 0) return null;
            String assetPath = path.substring(marker + "/image/upload/".length());
            assetPath = assetPath.replaceFirst("^v[0-9]+/", "");
            int extension = assetPath.lastIndexOf('.');
            if (extension > assetPath.lastIndexOf('/')) assetPath = assetPath.substring(0, extension);
            return assetPath.startsWith("eventos-seplag/") && assetPath.length() <= 255 ? assetPath : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
