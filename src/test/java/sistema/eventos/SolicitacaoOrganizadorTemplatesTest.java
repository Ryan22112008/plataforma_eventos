package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolicitacaoOrganizadorTemplatesTest {

    @Test
    void formularioDeveVincularEExibirErroDoOrgao() throws IOException {
        String formulario = lerTemplate("templates/solicitar-organizador.html");

        assertTrue(formulario.contains("th:field=\"*{orgao}\""));
        assertTrue(formulario.contains("th:errors=\"*{orgao}\""));
        assertFalse(formulario.contains("th:field*{orgao}"));
        assertTrue(formulario.contains("minlength=\"3\""));
        assertTrue(formulario.contains("maxlength=\"50\""));
        assertTrue(formulario.contains("maxlength=\"100\""));
        assertTrue(formulario.contains("maxlength=\"1000\""));
    }

    @Test
    void edicaoDoSuperAdminDeveExibirErroDoOrgaoCorretamente() throws IOException {
        String edicao = lerTemplate("templates/super-admin-editar-solicitacao.html");

        assertTrue(edicao.contains("th:errors=\"*{orgao}\""));
        assertFalse(edicao.contains("th:errors=\"${#fields.hasErrors('orgao')}\""));
    }

    @Test
    void bannerDeveAbrirTelaDeSolicitacaoComGet() throws IOException {
        String index = lerTemplate("templates/index.html");

        Matcher banner = Pattern.compile("(?s)<a\\b[^>]*organizer-access-callout[^>]*>.*?</a>")
                .matcher(index);
        assertTrue(banner.find(), "O banner deve ser um link para a tela de solicitação.");
        assertTrue(banner.group().contains("th:href=\"@{/participante/solicitar-organizador}\""));
        assertTrue(banner.group().contains("organizer-access-button"));

        assertFalse(Pattern.compile("(?s)<form\\b[^>]*organizer-access-callout[^>]*>")
                .matcher(index).find());
        assertFalse(index.contains("th:name=\"${_csrf.parameterName}\""));
    }

    @Test
    void mensagemDeSucessoDeveDependerSomenteDoEnvio() throws IOException {
        String index = lerTemplate("templates/index.html");

        assertTrue(index.contains("th:if=\"${solicitacaoOrganizadorEnviada == true}\""));
        assertFalse(index.contains("solicitacaoOrganizadorEnviada == true and solicitacaoOrganizadorPendente == false"));
    }

    private static String lerTemplate(String caminho) throws IOException {
        try (InputStream input = new ClassPathResource(caminho).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
