package sistema.eventos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migração segura de dados: converte qualquer usuário com perfil ROLE_ADMIN
 * para ROLE_SUPER_ADMIN no boot da aplicação em produção.
 *
 * Esta classe existe porque o projeto NÃO utiliza Flyway nem Liquibase.
 * O arquivo src/main/resources/migration/V1__converter_role_admin_para_super_admin.sql
 * nunca era executado automaticamente pelo Spring Boot.
 *
 * A operação é idempotente: se não houver nenhum usuário com ROLE_ADMIN,
 * zero linhas são afetadas e o startup segue normalmente.
 *
 * Apenas a coluna 'perfil' é alterada. Nome, e-mail, senha, ativo e demais
 * dados são preservados integralmente. Nenhum usuário é criado, removido ou
 * teve senha/e-mail/nome alterado.
 *
 * A exceção é capturada para não impedir o startup da aplicação em ambientes
 * onde o esquema ainda não contém o valor ROLE_ADMIN (ex.: banco H2 de teste
 * com CHECK constraint desatualizado). Em produção (MySQL ou PostgreSQL) o UPDATE executa
 * normalmente e converte as contas administrativas existentes.
 */
@Component
public class MigrateRoleAdminToSuperAdmin implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MigrateRoleAdminToSuperAdmin.class);

    private final JdbcTemplate jdbcTemplate;

    public MigrateRoleAdminToSuperAdmin(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            int linhasAfetadas = jdbcTemplate.update(
                    "UPDATE tb_usuario SET perfil = 'ROLE_SUPER_ADMIN' WHERE perfil = 'ROLE_ADMIN'"
            );

            if (linhasAfetadas > 0) {
                logger.info("Migração concluída: {} usuário(s) com perfil ROLE_ADMIN convertido(s) para ROLE_SUPER_ADMIN.", linhasAfetadas);
            } else {
                logger.info("Nenhum usuário com perfil ROLE_ADMIN encontrado. Migração não necessária.");
            }
        } catch (Exception ex) {
            logger.warn("Não foi possível executar a migração de ROLE_ADMIN para ROLE_SUPER_ADMIN: {}", ex.getMessage());
            logger.warn("A aplicação continuará iniciando. Verifique se o banco de produção possui usuários com ROLE_ADMIN.");
        }
    }
}
