package sistema.eventos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${APP_BOOTSTRAP_ADMIN_NAME:}")
    private String adminName;

    @Value("${APP_BOOTSTRAP_ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${APP_BOOTSTRAP_ADMIN_PASSWORD:}")
    private String adminPassword;

    public DataInitializer(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (adminName.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
            logger.info("Nenhum administrador inicial configurado; nenhum usuário padrão será criado.");
            return;
        }
        if (adminPassword.length() < 12) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD precisa ter pelo menos 12 caracteres.");
        }
        if (adminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD não pode exceder 72 bytes UTF-8.");
        }

        adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        adminName = adminName.trim();
        if (usuarioRepository.existsByEmailIgnoreCase(adminEmail)) {
            logger.info("Administrador inicial já existe; senha e permissões existentes foram preservadas.");
            return;
        }

        Usuario admin = Usuario.builder()
                .nome(adminName)
                .email(adminEmail)
                .senha(passwordEncoder.encode(adminPassword))
                .perfil(Perfil.ROLE_ADMIN)
                .ativo(true)
                .build();
        usuarioRepository.save(admin);
        logger.info("Administrador inicial criado a partir das variáveis de ambiente.");
    }
}
