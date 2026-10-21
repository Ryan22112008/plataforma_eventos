package sistema.eventos.service;

import lombok.AllArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.UsuarioRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@AllArgsConstructor
public class UsuarioExportService {

    private static final String SHEET_NAME = "Usuários";
    private static final String[] COLUNAS = {"Nome", "E-mail", "Perfil", "Status", "Data de cadastro"};
    private static final DateTimeFormatter DATA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            // Estilo do cabeçalho
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Estilo das células de data
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/MM/yyyy HH:mm"));

            // Linha de cabeçalho
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < COLUNAS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(COLUNAS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Linhas de dados
            int rowNum = 1;
            for (Usuario usuario : usuarios) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(usuario.getNome());
                row.createCell(1).setCellValue(usuario.getEmail());
                row.createCell(2).setCellValue(formatarPerfil(usuario.getPerfil()));
                row.createCell(3).setCellValue(usuario.isAtivo() ? "ATIVO" : "INATIVO");

                Cell celulaData = row.createCell(4);
                LocalDateTime dataCriacao = usuario.getDataCriacao();
                if (dataCriacao != null) {
                    celulaData.setCellValue(dataCriacao.format(DATA_FORMATTER));
                    celulaData.setCellStyle(dateStyle);
                } else {
                    celulaData.setCellValue("");
                }
            }

            // Largura adequada das colunas
            sheet.setColumnWidth(0, 40 * 256); // Nome
            sheet.setColumnWidth(1, 40 * 256); // E-mail
            sheet.setColumnWidth(2, 20 * 256); // Perfil
            sheet.setColumnWidth(3, 15 * 256); // Status
            sheet.setColumnWidth(4, 22 * 256); // Data de cadastro

            // Congelar a primeira linha (cabeçalho)
            sheet.createFreezePane(0, 1);

            // Filtro automático no cabeçalho
            sheet.setAutoFilter(CellRangeAddress.valueOf("A1:E1"));

            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                workbook.write(baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            throw new ExportacaoException("Erro ao gerar arquivo Excel de exportação de usuários.", e);
        }
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
