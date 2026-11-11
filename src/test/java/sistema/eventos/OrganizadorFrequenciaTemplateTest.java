package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que as telas do organizador entregam o filtro de frequência
 * (client-side, usando o percentual calculado pelo backend) e o fluxo de
 * salvamento em lote de presenças, sem o salvamento/reload por célula.
 */
class OrganizadorFrequenciaTemplateTest {

    @Test
    void inscritosOfereceFiltroDeFrequenciaComTodasAsOpcoes() throws IOException {
        String template = lerTemplate("templates/inscritos.html");

        assertThat(template).contains(
                "id=\"filtroFrequencia\"",
                "data-frequencia",
                "id=\"selecionarTodos\"",
                "class=\"seplag-checkbox selecao-inscrito\"",
                "form=\"formValidarLote\"",
                "name=\"inscricoesSelecionadas\"",
                "id=\"formValidarLote\"",
                "validar-participacao-lote"
        );

        for (int percentual = 50; percentual <= 100; percentual += 5) {
            assertThat(template).contains("value=\"" + percentual + "\"");
        }
    }

    @Test
    void filtroUsaSomentePercentuaisExibidosEEdgeCasesCorretos() throws IOException {
        String template = lerTemplate("templates/inscritos.html");

        // Maior ou igual ao mínimo, excluindo "Não aplicável" (valor vazio) sob filtro.
        assertThat(template).contains("percentual >= minimo");
        assertThat(template).contains("percentual !== null");
        assertThat(template).contains("!Number.isNaN(percentual)");
        assertThat(template).contains("valor === null || valor === '' ? null : Number(valor)");

        // "Selecionar todos" atua apenas sobre checkboxes das linhas visíveis.
        assertThat(template).contains("function checkboxesVisiveis()");
        assertThat(template).contains("linhasVisiveis().flatMap");
        assertThat(template).contains("checkbox.checked = selecionarTodos.checked");
    }

    @Test
    void selecionarTodosNaoMantemSelecaoEmLinhasOcultadasPeloFiltro() throws IOException {
        String template = lerTemplate("templates/inscritos.html");

        assertThat(template).contains("linha.hidden = !visivel");
        assertThat(template).contains("linha.querySelectorAll('input.selecao-inscrito').forEach");
        assertThat(template).contains("checkbox.checked = false");
    }

    @Test
    void frequenciaUsaSalvamentoEmLoteComJustificadaESemReloadPorCelula() throws IOException {
        String template = lerTemplate("templates/frequencia-aluno.html");

        assertThat(template).contains(
                "Salvar alterações",
                "id=\"btnSalvarPresencas\"",
                "alterar-status-lote",
                "data-original",
                "window.marcarAlteracao",
                "JUSTIFICADA",
                "beforeunload",
                "permanecem intactas",
                "seplag-status--justified"
        );

        assertThat(template).doesNotContain("window.location.reload");
        assertThat(template).doesNotContain("atualizarStatus(this)");
        assertThat(template).doesNotContain("/presencas/alterar-status?alunoId=");
        assertThat(template).doesNotContain("onchange=\"atualizarStatus");
    }

    @Test
    void frequenciaSoEnviaAoBackendQuandoUsuarioConfirmaSalvar() throws IOException {
        String template = lerTemplate("templates/frequencia-aluno.html");

        assertThat(template).contains("btnSalvar.addEventListener('click', salvarAlteracoes)");
        assertThat(template).contains("JSON.stringify(alteracoes)");
        assertThat(template).contains("alteracoesPendentes()");
        assertThat(template).contains("select.value !== select.getAttribute('data-original')");
    }

    private String lerTemplate(String caminho) throws IOException {
        try (InputStream stream = new ClassPathResource(caminho).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
