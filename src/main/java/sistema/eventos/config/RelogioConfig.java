package sistema.eventos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Centraliza a data/hora atual da aplicação no fuso oficial de Mato Grosso.
 * Evita LocalDate.now()/LocalDateTime.now() espalhados e mantém os filtros
 * de vigência consistentes entre desenvolvimento e produção.
 */
@Configuration
public class RelogioConfig {

    public static final ZoneId ZONA_APLICACAO = ZoneId.of("America/Cuiaba");

    @Bean
    public Clock relogioAplicacao() {
        return Clock.system(ZONA_APLICACAO);
    }
}
