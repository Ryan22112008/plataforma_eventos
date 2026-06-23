package sistema.eventos.config;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThymeleafLayoutConfig {

    // Registra o dialeto de layout automaticamente para que o Thymeleaf entenda as tags layout:decorate e layout:fragment
    @Bean
    public LayoutDialect layoutDialect() {
        return new LayoutDialect();
    }
}