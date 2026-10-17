package sistema.eventos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Uses DATABASE_URL when configured; local development keeps the H2 fallback. */
@Configuration
@ConditionalOnProperty(name = "DATABASE_URL")
public class DatabaseUrlDataSourceConfig {

    @Bean
    public DataSource databaseUrlDataSource(Environment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL", "").trim();
        if (databaseUrl.startsWith("jdbc:mysql:")) {
            return DataSourceBuilder.create()
                    .driverClassName("com.mysql.cj.jdbc.Driver")
                    .url(databaseUrl)
                    .username(environment.getProperty("DB_USERNAME", environment.getProperty("DB_USER", "")))
                    .password(environment.getProperty("DB_PASSWORD", ""))
                    .build();
        }

        URI uri;
        try {
            uri = URI.create(databaseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DATABASE_URL não é uma URL MySQL válida.");
        }

        if (!"mysql".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("DATABASE_URL precisa usar mysql:// ou jdbc:mysql://.");
        }
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() < 2) {
            throw new IllegalStateException("DATABASE_URL precisa incluir host e nome do banco MySQL.");
        }

        String jdbcUrl = "jdbc:mysql://" + uri.getHost()
                + (uri.getPort() >= 0 ? ":" + uri.getPort() : "")
                + uri.getRawPath()
                + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int separator = userInfo.indexOf(':');
            username = decode(separator >= 0 ? userInfo.substring(0, separator) : userInfo);
            if (separator >= 0) {
                password = decode(userInfo.substring(separator + 1));
            }
        }
        if (username == null || username.isBlank()) {
            username = environment.getProperty("DB_USERNAME", environment.getProperty("DB_USER", ""));
        }
        if (password == null) {
            password = environment.getProperty("DB_PASSWORD", "");
        }

        return DataSourceBuilder.create()
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }

    private String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
