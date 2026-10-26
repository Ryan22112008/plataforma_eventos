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
import java.util.Locale;

/**
 * Uses DATABASE_URL when configured; local development keeps the H2 fallback.
 *
 * MySQL (Aiven/Render e configurações existentes) e PostgreSQL (Supabase/Render)
 * são aceitos. URLs JDBC completas e URLs no formato de URI são suportadas:
 * jdbc:mysql://, mysql://, jdbc:postgresql://, postgres:// e postgresql://.
 */
@Configuration
@ConditionalOnProperty(name = "DATABASE_URL")
public class DatabaseUrlDataSourceConfig {

    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";

    @Bean
    public DataSource databaseUrlDataSource(Environment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL", "").trim();
        if (databaseUrl.isBlank()) {
            throw new IllegalStateException("DATABASE_URL está vazia.");
        }

        String normalizedUrl = databaseUrl.toLowerCase(Locale.ROOT);
        if (normalizedUrl.startsWith("jdbc:mysql:")) {
            return buildDataSource(MYSQL_DRIVER, databaseUrl, environment);
        }
        if (normalizedUrl.startsWith("jdbc:postgresql:")) {
            return buildDataSource(POSTGRESQL_DRIVER, databaseUrl, environment);
        }

        URI uri;
        try {
            uri = URI.create(databaseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DATABASE_URL não é uma URL de banco de dados válida.", exception);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean mysql = "mysql".equals(scheme);
        boolean postgresql = "postgres".equals(scheme) || "postgresql".equals(scheme);
        if (!mysql && !postgresql) {
            throw new IllegalStateException(
                    "DATABASE_URL precisa usar mysql://, jdbc:mysql://, postgres://, postgresql:// ou jdbc:postgresql://.");
        }
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() < 2) {
            throw new IllegalStateException("DATABASE_URL precisa incluir host e nome do banco de dados.");
        }

        String jdbcUrl = (mysql ? "jdbc:mysql://" : "jdbc:postgresql://") + uri.getHost()
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

        return buildDataSource(mysql ? MYSQL_DRIVER : POSTGRESQL_DRIVER, jdbcUrl, username, password);
    }

    private DataSource buildDataSource(String driverClassName, String jdbcUrl, Environment environment) {
        return buildDataSource(
                driverClassName,
                jdbcUrl,
                environment.getProperty("DB_USERNAME", environment.getProperty("DB_USER", "")),
                environment.getProperty("DB_PASSWORD", "")
        );
    }

    private DataSource buildDataSource(String driverClassName, String jdbcUrl, String username, String password) {
        return DataSourceBuilder.create()
                .driverClassName(driverClassName)
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }

    private String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
