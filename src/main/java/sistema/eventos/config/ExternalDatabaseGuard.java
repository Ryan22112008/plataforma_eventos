package sistema.eventos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.Locale;

/** Prevents production deployments from silently storing data in ephemeral H2. */
@Component
public class ExternalDatabaseGuard {

    @Value("${REQUIRE_EXTERNAL_DATABASE:false}")
    private boolean requireExternalDatabase;

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @PostConstruct
    public void validateDatabaseConfiguration() {
        if (requireExternalDatabase && (databaseUrl == null || databaseUrl.isBlank())) {
            throw new IllegalStateException(
                    "REQUIRE_EXTERNAL_DATABASE está ativo, mas DATABASE_URL não foi configurada. "
                            + "Configure a conexão MySQL ou PostgreSQL antes de iniciar em produção.");
        }
        if (requireExternalDatabase && !isExternalDatabaseUrl(databaseUrl)) {
            throw new IllegalStateException(
                    "DATABASE_URL precisa apontar para MySQL ou PostgreSQL quando REQUIRE_EXTERNAL_DATABASE está ativo.");
        }
    }

    private boolean isExternalDatabaseUrl(String url) {
        String normalizedUrl = url.trim().toLowerCase(Locale.ROOT);
        return normalizedUrl.startsWith("mysql://")
                || normalizedUrl.startsWith("jdbc:mysql:")
                || normalizedUrl.startsWith("postgres://")
                || normalizedUrl.startsWith("postgresql://")
                || normalizedUrl.startsWith("jdbc:postgresql:");
    }
}
