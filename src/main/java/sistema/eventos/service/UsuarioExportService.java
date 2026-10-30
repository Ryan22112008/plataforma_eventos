package sistema.eventos.service;

import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.IndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import sistema.eventos.dto.UsuarioEventoTituloProjection;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@AllArgsConstructor
public class UsuarioExportService {

    private static final String SHEET_NAME = "Usuários";
    private static final String SEM_EVENTOS = "Nenhum";
    private static final String[] COLUNAS = {
            "Nome", "E-mail", "Perfil", "Status", "Data de cadastro",
            "Eventos Participados", "Datas dos Eventos Participados",
            "Eventos Organizados", "Datas dos Eventos Organizados"
    };

    private static final DateTimeFormatter FORMATO_DATA_EVENTO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Paleta da planilha: cabeçalho verde-claro com texto verde-escuro, corpo neutro e discreto.
    private static final byte[] COR_CABECALHO_FUNDO = {(byte) 0xE2, (byte) 0xEF, (byte) 0xDA};
    private static final byte[] COR_CABECALHO_TEXTO = {(byte) 0x1E, (byte) 0x5B, (byte) 0x34};
    private static final byte[] COR_TEXTO = {(byte) 0x33, (byte) 0x3F, (byte) 0x38};
    private static final byte[] COR_BORDA = {(byte) 0xD6, (byte) 0xE0, (byte) 0xD6};
    private static final byte[] COR_ZEBRA = {(byte) 0xF5, (byte) 0xFA, (byte) 0xF5};

    private static final IndexedColorMap COLOR_MAP = new DefaultIndexedColorMap();

    private static final float ALTURA_CABECALHO = 26f;
    private static final float ALTURA_LINHA = 20f;
    private static final int MAX_LINHAS_EVENTOS = 20;
    private static final int[] LARGURA_MINIMA = {18, 30, 14, 12, 20, 24, 24, 24, 24};
    private static final int[] LARGURA_MAXIMA = {55, 60, 22, 14, 20, 45, 45, 45, 45};

    private final UsuarioRepository usuarioRepository;
    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;

    /**
     * Gera um arquivo .xlsx com os usuários filtrados (sem paginação).
     *
     * Apenas dados não sensíveis são incluídos: nome, e-mail, perfil, status,
     * data de cadastro, títulos e datas dos eventos participados/organizados.
     * Senha, hash e tokens nunca são exportados.
     *
     * A participação usa a regra já existente no sistema (inscrição com status
     * UTILIZADO) e a organização usa o relacionamento real Evento.organizador.
     * Os eventos são carregados em lote, sem consultas por usuário.
     *
     * @param nome  filtro parcial por nome (ignorado se nulo/vazio)
     * @param email filtro parcial por e-mail (ignorado se nulo/vazio)
     * @param perfil filtro exato por perfil (ignorado se null)
     * @param ativo  filtro exato por status ativo/inativo (ignorado se null)
     * @return bytes do arquivo .xlsx gerado
     */
    public byte[] exportarUsuarios(String nome, String email, Perfil perfil, Boolean ativo) {
        List<Usuario> usuarios = usuarioRepository.findFiltrosSemPaginacao(nome, email, perfil, ativo);

        Map<Long, List<EventoExportacao>> eventosParticipados = carregarEventosParticipados(usuarios);
        Map<Long, List<EventoExportacao>> eventosOrganizados = carregarEventosOrganizados(usuarios);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            XSSFFont fonteCabecalho = criarFonte(workbook, true, COR_CABECALHO_TEXTO);
            XSSFFont fonteCorpo = criarFonte(workbook, false, COR_TEXTO);

            XSSFCellStyle cabecalhoEsquerda = criarEstiloCabecalho(workbook, fonteCabecalho, HorizontalAlignment.LEFT);
            XSSFCellStyle cabecalhoCentro = criarEstiloCabecalho(workbook, fonteCabecalho, HorizontalAlignment.CENTER);
            XSSFCellStyle textoEsquerda = criarEstiloTexto(workbook, fonteCorpo, HorizontalAlignment.LEFT, false);
            XSSFCellStyle textoEsquerdaZebra = criarEstiloTexto(workbook, fonteCorpo, HorizontalAlignment.LEFT, true);
            XSSFCellStyle textoCentro = criarEstiloTexto(workbook, fonteCorpo, HorizontalAlignment.CENTER, false);
            XSSFCellStyle textoCentroZebra = criarEstiloTexto(workbook, fonteCorpo, HorizontalAlignment.CENTER, true);
            XSSFCellStyle dataCentro = criarEstiloData(workbook, fonteCorpo, false);
            XSSFCellStyle dataCentroZebra = criarEstiloData(workbook, fonteCorpo, true);
            XSSFCellStyle textoEventos = criarEstiloTextoMultilinha(workbook, fonteCorpo, false);
            XSSFCellStyle textoEventosZebra = criarEstiloTextoMultilinha(workbook, fonteCorpo, true);

            // Larguras calculadas a partir do maior conteúdo de cada coluna.
            int[] maioresConteudos = new int[COLUNAS.length];
            for (int i = 0; i < COLUNAS.length; i++) {
                maioresConteudos[i] = COLUNAS[i].length();
            }

            // Linha de cabeçalho
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(ALTURA_CABECALHO);
            for (int i = 0; i < COLUNAS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(COLUNAS[i]);
                cell.setCellStyle(i == 3 || i == 4 ? cabecalhoCentro : cabecalhoEsquerda);
            }

            // Linhas de dados
            int rowNum = 1;
            for (Usuario usuario : usuarios) {
                Row row = sheet.createRow(rowNum);
                boolean zebra = rowNum % 2 == 0;

                String nomeUsuario = valorOuVazio(usuario.getNome());
                String emailUsuario = valorOuVazio(usuario.getEmail());
                String perfilUsuario = formatarPerfil(usuario.getPerfil());
                String statusUsuario = usuario.isAtivo() ? "ATIVO" : "INATIVO";
                String titulosParticipados = formatarEventos(eventosParticipados.get(usuario.getId()));
                String datasParticipados = formatarDatasEventos(eventosParticipados.get(usuario.getId()));
                String titulosOrganizados = formatarEventos(eventosOrganizados.get(usuario.getId()));
                String datasOrganizados = formatarDatasEventos(eventosOrganizados.get(usuario.getId()));

                row.setHeightInPoints(ALTURA_LINHA * Math.min(
                        Math.max(
                                Math.max(contarLinhas(titulosParticipados), contarLinhas(datasParticipados)),
                                Math.max(contarLinhas(titulosOrganizados), contarLinhas(datasOrganizados))),
                        MAX_LINHAS_EVENTOS));

                definirTexto(row, 0, nomeUsuario, zebra ? textoEsquerdaZebra : textoEsquerda);
                definirTexto(row, 1, emailUsuario, zebra ? textoEsquerdaZebra : textoEsquerda);
                definirTexto(row, 2, perfilUsuario, zebra ? textoEsquerdaZebra : textoEsquerda);
                definirTexto(row, 3, statusUsuario, zebra ? textoCentroZebra : textoCentro);

                Cell celulaData = row.createCell(4);
                LocalDateTime dataCriacao = usuario.getDataCriacao();
                if (dataCriacao != null) {
                    // Valor de data real: o Excel ordena/filtra corretamente e exibe no formato definido.
                    celulaData.setCellValue(dataCriacao);
                    celulaData.setCellStyle(zebra ? dataCentroZebra : dataCentro);
                } else {
                    celulaData.setCellValue("");
                    celulaData.setCellStyle(zebra ? textoCentroZebra : textoCentro);
                }

                definirTexto(row, 5, titulosParticipados, zebra ? textoEventosZebra : textoEventos);
                definirTexto(row, 6, datasParticipados, zebra ? textoEventosZebra : textoEventos);
                definirTexto(row, 7, titulosOrganizados, zebra ? textoEventosZebra : textoEventos);
                definirTexto(row, 8, datasOrganizados, zebra ? textoEventosZebra : textoEventos);

                maioresConteudos[0] = Math.max(maioresConteudos[0], nomeUsuario.length());
                maioresConteudos[1] = Math.max(maioresConteudos[1], emailUsuario.length());
                maioresConteudos[2] = Math.max(maioresConteudos[2], perfilUsuario.length());
                maioresConteudos[3] = Math.max(maioresConteudos[3], statusUsuario.length());
                maioresConteudos[5] = Math.max(maioresConteudos[5], maiorLinha(titulosParticipados));
                maioresConteudos[6] = Math.max(maioresConteudos[6], maiorLinha(datasParticipados));
                maioresConteudos[7] = Math.max(maioresConteudos[7], maiorLinha(titulosOrganizados));
                maioresConteudos[8] = Math.max(maioresConteudos[8], maiorLinha(datasOrganizados));
                rowNum++;
            }

            // Largura adequada das colunas (sem cortes desnecessários e sem colunas exageradas)
            for (int i = 0; i < COLUNAS.length; i++) {
                int largura = Math.max(LARGURA_MINIMA[i], Math.min(LARGURA_MAXIMA[i], maioresConteudos[i] + 3));
                sheet.setColumnWidth(i, largura * 256);
            }

            // Congelar a primeira linha (cabeçalho)
            sheet.createFreezePane(0, 1);

            // Filtro automático no cabeçalho, cobrindo todas as linhas exportadas
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowNum - 1, 0), 0, COLUNAS.length - 1));

            // A grade padrão foi substituída pelas bordas da tabela
            sheet.setDisplayGridlines(false);

            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                workbook.write(baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            throw new ExportacaoException("Erro ao gerar arquivo Excel de exportação de usuários.", e);
        }
    }

    private XSSFFont criarFonte(XSSFWorkbook workbook, boolean negrito, byte[] cor) {
        XSSFFont fonte = workbook.createFont();
        fonte.setFontName("Calibri");
        fonte.setFontHeightInPoints((short) 11);
        fonte.setBold(negrito);
        fonte.setColor(cor(cor));
        return fonte;
    }

    private XSSFCellStyle criarEstiloCabecalho(XSSFWorkbook workbook, XSSFFont fonte, HorizontalAlignment alinhamento) {
        XSSFCellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fonte);
        estilo.setFillForegroundColor(cor(COR_CABECALHO_FUNDO));
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setAlignment(alinhamento);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setWrapText(false);
        aplicarBordas(estilo);
        // Linha inferior do cabeçalho levemente mais marcada para separar do corpo
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBottomBorderColor(cor(COR_CABECALHO_TEXTO));
        return estilo;
    }

    private XSSFCellStyle criarEstiloTexto(XSSFWorkbook workbook, XSSFFont fonte,
                                           HorizontalAlignment alinhamento, boolean zebra) {
        XSSFCellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fonte);
        estilo.setAlignment(alinhamento);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setWrapText(false);
        aplicarBordas(estilo);
        if (zebra) {
            estilo.setFillForegroundColor(cor(COR_ZEBRA));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return estilo;
    }

    private XSSFCellStyle criarEstiloData(XSSFWorkbook workbook, XSSFFont fonte, boolean zebra) {
        XSSFCellStyle estilo = criarEstiloTexto(workbook, fonte, HorizontalAlignment.CENTER, zebra);
        estilo.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy HH:mm"));
        return estilo;
    }

    /**
     * Estilo das células de eventos: quebra de linha habilitada e alinhamento
     * superior para que múltiplos títulos fiquem legíveis na mesma célula.
     */
    private XSSFCellStyle criarEstiloTextoMultilinha(XSSFWorkbook workbook, XSSFFont fonte, boolean zebra) {
        XSSFCellStyle estilo = criarEstiloTexto(workbook, fonte, HorizontalAlignment.LEFT, zebra);
        estilo.setWrapText(true);
        estilo.setVerticalAlignment(VerticalAlignment.TOP);
        return estilo;
    }

    private void aplicarBordas(XSSFCellStyle estilo) {
        XSSFColor corBorda = cor(COR_BORDA);
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
        estilo.setTopBorderColor(corBorda);
        estilo.setBottomBorderColor(corBorda);
        estilo.setLeftBorderColor(corBorda);
        estilo.setRightBorderColor(corBorda);
    }

    /**
     * Carrega, em uma única consulta, os títulos e as datas dos eventos com
     * participação validada (inscrição com status UTILIZADO) dos usuários
     * exportados.
     */
    private Map<Long, List<EventoExportacao>> carregarEventosParticipados(List<Usuario> usuarios) {
        List<Long> ids = idsDosUsuarios(usuarios);
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return agruparPorUsuario(inscricaoRepository
                .findTitulosEventosPorParticipantes(ids, StatusInscricao.UTILIZADO));
    }

    /**
     * Carrega, em uma única consulta, os títulos e as datas dos eventos
     * organizados pelos usuários exportados, usando o relacionamento real
     * Evento.organizador.
     */
    private Map<Long, List<EventoExportacao>> carregarEventosOrganizados(List<Usuario> usuarios) {
        List<Long> ids = idsDosUsuarios(usuarios);
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return agruparPorUsuario(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(ids));
    }

    private List<Long> idsDosUsuarios(List<Usuario> usuarios) {
        return usuarios.stream()
                .map(Usuario::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Map<Long, List<EventoExportacao>> agruparPorUsuario(List<UsuarioEventoTituloProjection> projecoes) {
        Map<Long, List<EventoExportacao>> porUsuario = new LinkedHashMap<>();
        if (projecoes == null) {
            return porUsuario;
        }
        for (UsuarioEventoTituloProjection projecao : projecoes) {
            if (projecao == null || projecao.getUsuarioId() == null) {
                continue;
            }
            String titulo = projecao.getTitulo();
            if (titulo == null || titulo.isBlank()) {
                continue;
            }
            List<EventoExportacao> eventos = porUsuario.computeIfAbsent(
                    projecao.getUsuarioId(), id -> new ArrayList<>());
            boolean jaAdicionado = eventos.stream().anyMatch(evento -> evento.titulo().equals(titulo));
            if (!jaAdicionado) {
                eventos.add(new EventoExportacao(titulo, projecao.getDataInicio(), projecao.getDataTermino()));
            }
        }
        return porUsuario;
    }

    private String formatarEventos(List<EventoExportacao> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return SEM_EVENTOS;
        }
        return String.join("\n", eventos.stream().map(EventoExportacao::titulo).toList());
    }

    /**
     * Mantém a mesma ordem dos títulos e usa exatamente as datas existentes no
     * modelo do evento, sem preencher informações ausentes.
     */
    private String formatarDatasEventos(List<EventoExportacao> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return SEM_EVENTOS;
        }
        return String.join("\n", eventos.stream().map(this::formatarPeriodo).toList());
    }

    private String formatarPeriodo(EventoExportacao evento) {
        String inicio = formatarData(evento.dataInicio());
        String termino = formatarData(evento.dataTermino());
        if (!inicio.isEmpty() && !termino.isEmpty()) {
            return inicio + " - " + termino;
        }
        if (!inicio.isEmpty()) {
            return inicio;
        }
        return termino;
    }

    private String formatarData(LocalDate data) {
        return data == null ? "" : FORMATO_DATA_EVENTO.format(data);
    }

    private int contarLinhas(String texto) {
        if (texto == null || texto.isEmpty()) {
            return 1;
        }
        return texto.split("\n", -1).length;
    }

    private int maiorLinha(String texto) {
        if (texto == null || texto.isEmpty()) {
            return 0;
        }
        int maior = 0;
        for (String linha : texto.split("\n", -1)) {
            maior = Math.max(maior, linha.length());
        }
        return maior;
    }

    private void definirTexto(Row row, int coluna, String valor, XSSFCellStyle estilo) {
        Cell celula = row.createCell(coluna);
        celula.setCellValue(valor);
        celula.setCellStyle(estilo);
    }

    private String valorOuVazio(String valor) {
        return valor == null ? "" : valor;
    }

    private static XSSFColor cor(byte[] rgb) {
        return new XSSFColor(rgb, COLOR_MAP);
    }

    private String formatarPerfil(Perfil perfil) {
        if (perfil == null) {
            return "";
        }
        String nome = perfil.name();
        // Remove o prefixo "ROLE_" para exibição legível
        if (nome.startsWith("ROLE_")) {
            nome = nome.substring("ROLE_".length());
        }
        return nome;
    }

    private record EventoExportacao(String titulo, LocalDate dataInicio, LocalDate dataTermino) {
    }

    public static final class ExportacaoException extends RuntimeException {
        public ExportacaoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
