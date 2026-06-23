package sistema.eventos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String diretorioUploads;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve o caminho absoluto de forma portável para o Spring
        Path caminhoUploads = Paths.get(diretorioUploads).toAbsolutePath().normalize();

        // Mapeia a URL pública do site "/imagens-eventos/**" para ler a pasta física
        registry.addResourceHandler("/imagens-eventos/**")
                .addResourceLocations("file:" + caminhoUploads.toString() + "/");
    }
}