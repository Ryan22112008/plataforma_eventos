package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InscritosTemplateTest {

    @Test
    void telaDeInscritosMostraFrequenciaCertificadoEAcaoDeValidacao() throws IOException {
        String template = lerTemplate("templates/inscritos.html");

        assertThat(template).contains("Frequência");
        assertThat(template).contains("Certificado");
        assertThat(template).contains("Validar participação");
        assertThat(template).contains("Participação validada");
        assertThat(template).contains("/validar-participacao");
        assertThat(template).contains("Pendente");
        assertThat(template).contains("Liberado");
        assertThat(template).contains("Validar selecionados");
        assertThat(template).contains("selecionarTodos");
        assertThat(template).contains("inscricaoIds");
        assertThat(template).contains("Selecione ao menos um participante");
    }

    @Test
    void telaDeInscritosNaoExpoeMaisAConsolidacaoEmLoteComoValidacao() throws IOException {
        String template = lerTemplate("templates/inscritos.html");

        assertThat(template).doesNotContain("Status / Inscrição");
        assertThat(template).doesNotContain("consolidar-presencas");
        assertThat(template).doesNotContain("inscricoesPresentes");
    }

    private static String lerTemplate(String caminho) throws IOException {
        try (InputStream input = new ClassPathResource(caminho).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
