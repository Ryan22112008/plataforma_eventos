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

    @Value("${APP_BOOTSTRAP_SUPER_ADMIN_NAME:}")
    private String superAdminName;

    @Value("${APP_BOOTSTRAP_SUPER_ADMIN_EMAIL:}")
    private String superAdminEmail;

    @Value("${APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD:}")
    private String superAdminPassword;

    public DataInitializer(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // --- Administrador inicial (mantido exatamente como estava) ---
        if (!adminName.isBlank() || !adminEmail.isBlank() || !adminPassword.isBlank()) {
            if (adminName.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
                throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_NAME, APP_BOOTSTRAP_ADMIN_EMAIL e APP_BOOTSTRAP_ADMIN_PASSWORD devem ser informados juntos.");
            }
            if (adminPassword.length() < 12) {
                throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD precisa ter pelo menos 12 caracteres.");
            }
            if (adminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD não pode exceder 72 bytes UTF-8.");
            }

            adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
            adminName = adminName.trim();
            if (!usuarioRepository.existsByEmailIgnoreCase(adminEmail)) {
                Usuario admin = Usuario.builder()
                        .nome(adminName)
                        .email(adminEmail)
                        .senha(passwordEncoder.encode(adminPassword))
                        .perfil(Perfil.ROLE_ADMIN)
                        .ativo(true)
                        .build();
                usuarioRepository.save(admin);
                logger.info("Administrador inicial criado a partir das variáveis de ambiente.");
            } else {
                logger.info("Administrador inicial já existe; senha e permissões existentes foram preservadas.");
            }
        } else {
            logger.info("Nenhum administrador inicial configurado; nenhum usuário padrão será criado.");
        }

        // --- Super Admin inicial (novo) ---
        if (!superAdminName.isBlank() || !superAdminEmail.isBlank() || !superAdminPassword.isBlank()) {
            if (superAdminName.isBlank() || superAdminEmail.isBlank() || superAdminPassword.isBlank()) {
                throw new IllegalStateException("APP_BOOTSTRAP_SUPER_ADMIN_NAME, APP_BOOTSTRAP_SUPER_ADMIN_EMAIL e APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD devem ser informados juntos.");
            }
            if (superAdminPassword.length() < 12) {
                throw new IllegalStateException("APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD precisa ter pelo menos 12 caracteres.");
            }
            if (superAdminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalStateException("APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD não pode exceder 72 bytes UTF-8.");
            }

            superAdminEmail = superAdminEmail.trim().toLowerCase(Locale.ROOT);
            superAdminName = superAdminName.trim();
            if (usuarioRepository.existsByEmailIgnoreCase(superAdminEmail)) {
                logger.info("Super Admin inicial já existe; senha e permissões existentes foram preservadas.");
            } else {
                Usuario superAdmin = Usuario.builder()
                        .nome(superAdminName)
                        .email(superAdminEmail)
                        .senha(passwordEncoder.encode(superAdminPassword))
                        .perfil(Perfil.ROLE_SUPER_ADMIN)
                        .ativo(true)
                        .build();
                usuarioRepository.save(superAdmin);
                logger.info("Super Admin inicial criado a partir das variáveis de ambiente.");
            }
        }
    }
}
