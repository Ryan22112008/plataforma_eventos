package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuperAdminTemplatesTest {

    @Test
    void painelNaoDeveExibirCardsObsoletosDeAvaliadoresOuAdministradores() throws IOException {
        String painel = lerTemplate("templates/super-admin-usuarios.html");

        assertFalse(painel.contains(">Avaliadores<"), "O card de Avaliadores não deve mais existir.");
        assertFalse(painel.contains(">Administradores<"), "O card de Administradores não deve mais existir.");
        assertFalse(painel.contains("totalAvaliadores"));
        assertFalse(painel.contains("totalAdmins"));

        assertTrue(painel.contains(">Participantes<"));
        assertTrue(painel.contains(">Organizadores<"));
        assertTrue(painel.contains(">Super Admins<"));
        assertTrue(painel.contains("seplag-stats--4"), "Os cards restantes devem ficar equilibrados.");
    }

    @Test
    void detalhesDeUsuarioDevemTerUmUnicoBotaoVoltarParaAListagemCorreta() throws IOException {
        String detalhes = lerTemplate("templates/super-admin-detalhes-usuario.html");

        assertTrue(detalhes.contains("${ehOrganizador} ? @{/super-admin/organizadores} : @{/super-admin/usuarios}"));
        assertTrue(detalhes.contains("</i> Voltar"));
        assertFalse(detalhes.contains("Voltar ao painel Super Admin"));
        assertFalse(detalhes.contains("></i> Organizadores</a>"));
        assertFalse(detalhes.contains("hover:bg-white/5 hover:text-white\">Usuários</a>"));
    }

    @Test
    void edicaoDeSolicitacaoDeveVoltarParaAListagemDeSolicitacoes() throws IOException {
        String edicao = lerTemplate("templates/super-admin-editar-solicitacao.html");

        assertTrue(edicao.contains("th:href=\"@{/super-admin/solicitacoes}\""));
        assertTrue(edicao.contains("</i> Voltar"));
        assertFalse(edicao.contains("Voltar à lista de solicitações"));
    }

    @Test
    void organizadoresDevemUsarFotoPerfilComFallbackParaInicial() throws IOException {
        String organizadores = lerTemplate("templates/super-admin-organizadores.html");

        assertTrue(organizadores.contains("${fotosOrganizadores[organizador.id] != null}"));
        assertTrue(organizadores.contains("th:src=\"${fotosOrganizadores[organizador.id]}\""));
        assertTrue(organizadores.contains("${fotosOrganizadores[organizador.id] == null}"));
        assertTrue(organizadores.contains("#strings.substring(organizador.nome, 0, 1).toUpperCase()"));
    }

    private static String lerTemplate(String caminho) throws IOException {
        try (InputStream input = new ClassPathResource(caminho).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
