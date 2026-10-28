package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvaliacaoEventoTemplateTest {

    @Test
    void detalhesDoEventoDeveConterTextosEmPortuguesSemEscapesUnicode() throws IOException {
        String template = lerTemplate("templates/detalhes.html");

        assertTrue(template.contains("Avaliações"));
        assertTrue(template.contains("Ainda não há avaliações."));
        assertTrue(template.contains("Comentário"));
        assertTrue(template.contains("Você já avaliou este evento."));
        assertTrue(template.contains("Enviar avaliação"));
        assertFalse(template.contains("\\u00"), "O template não deve conter escapes unicode literais.");
    }

    @Test
    void formularioDeveOferecerAsCincoNotasEAsAcoesDeEdicaoEExclusao() throws IOException {
        String template = lerTemplate("templates/detalhes.html");

        for (int nota = 1; nota <= 5; nota++) {
            assertTrue(template.contains("name=\"nota\" value=\"" + nota + "\""),
                    "O formulário deve oferecer a nota " + nota + ".");
        }
        assertTrue(template.contains("/avaliacoes/editar"));
        assertTrue(template.contains("/avaliacoes/excluir"));
        assertTrue(template.contains("maxlength=\"1000\""));
    }

    private static String lerTemplate(String caminho) throws IOException {
        try (InputStream input = new ClassPathResource(caminho).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
