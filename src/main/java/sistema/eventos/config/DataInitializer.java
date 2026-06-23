package sistema.eventos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import sistema.eventos.model.Perfil;
import sistema.eventos.service.UsuarioService;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final UsuarioService usuarioService;

    public DataInitializer(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("⏳ Verificando e atualizando carga inicial de dados de usuários...");


        // e-mail pessoal como Organizador
        usuarioService.criarOuAtualizarUsuarioFixo(
                "Ryan Rodrigues Mendes",
                "ryanrodriguesmendes071@gmail.com",
                "123",
                Perfil.ROLE_ORGANIZADOR
        );

        // e-mail institucional/corporativo como Organizador
        usuarioService.criarOuAtualizarUsuarioFixo(
                "Ryan Rodrigues Mendes (SEPLAG)",
                "ryanmendes@seplag.mt.gov.br",
                "ryan22",
                Perfil.ROLE_ORGANIZADOR
        );
        logger.info("🚀 USUÁRIOS CONFIGURADOS COM SUCESSO NO BANCO DE DADOS!");
    }
}