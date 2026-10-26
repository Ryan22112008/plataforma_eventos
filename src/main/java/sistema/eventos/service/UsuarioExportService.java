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
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.UsuarioRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class UsuarioExportService {

    private static final String SHEET_NAME = "Usuários";
    private static final String[] COLUNAS = {"Nome", "E-mail", "Perfil", "Status", "Data de cadastro"};

    // Paleta da planilha: cabeçalho verde-claro com texto verde-escuro, corpo neutro e discreto.
    private static final byte[] COR_CABECALHO_FUNDO = {(byte) 0xE2, (byte) 0xEF, (byte) 0xDA};
    private static final byte[] COR_CABECALHO_TEXTO = {(byte) 0x1E, (byte) 0x5B, (byte) 0x34};
    private static final byte[] COR_TEXTO = {(byte) 0x33, (byte) 0x3F, (byte) 0x38};
    private static final byte[] COR_BORDA = {(byte) 0xD6, (byte) 0xE0, (byte) 0xD6};
    private static final byte[] COR_ZEBRA = {(byte) 0xF5, (byte) 0xFA, (byte) 0xF5};

    private static final IndexedColorMap COLOR_MAP = new DefaultIndexedColorMap();

    private static final float ALTURA_CABECALHO = 26f;
    private static final float ALTURA_LINHA = 20f;
    private static final int[] LARGURA_MINIMA = {18, 30, 14, 12, 20};
    private static final int[] LARGURA_MAXIMA = {55, 60, 22, 14, 20};

    private final UsuarioRepository usuarioRepository;

    /**
     * Gera um arquivo .xlsx com os usuários filtrados (sem paginação).
     *
     * Apenas dados não sensíveis são incluídos: nome, e-mail, perfil, status e data de cadastro.
     * Senha, hash e tokens nunca são exportados.
     *
     * @param nome  filtro parcial por nome (ignorado se nulo/vazio)
     * @param email filtro parcial por e-mail (ignorado se nulo/vazio)
     * @param perfil filtro exato por perfil (ignorado se null)
     * @param ativo  filtro exato por status ativo/inativo (ignorado se null)
     * @return bytes do arquivo .xlsx gerado
     */
    public byte[] exportarUsuarios(String nome, String email, Perfil perfil, Boolean ativo) {
        List<Usuario> usuarios = usuarioRepository.findFiltrosSemPaginacao(nome, email, perfil, ativo);

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
                cell.setCellStyle(i >= 3 ? cabecalhoCentro : cabecalhoEsquerda);
            }

            // Linhas de dados
            int rowNum = 1;
            for (Usuario usuario : usuarios) {
                Row row = sheet.createRow(rowNum);
                row.setHeightInPoints(ALTURA_LINHA);
                boolean zebra = rowNum % 2 == 0;

                String nomeUsuario = valorOuVazio(usuario.getNome());
                String emailUsuario = valorOuVazio(usuario.getEmail());
                String perfilUsuario = formatarPerfil(usuario.getPerfil());
                String statusUsuario = usuario.isAtivo() ? "ATIVO" : "INATIVO";

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

                maioresConteudos[0] = Math.max(maioresConteudos[0], nomeUsuario.length());
                maioresConteudos[1] = Math.max(maioresConteudos[1], emailUsuario.length());
                maioresConteudos[2] = Math.max(maioresConteudos[2], perfilUsuario.length());
                maioresConteudos[3] = Math.max(maioresConteudos[3], statusUsuario.length());
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

    public static final class ExportacaoException extends RuntimeException {
        public ExportacaoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
