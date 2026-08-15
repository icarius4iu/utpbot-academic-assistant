package com.utpbot.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Equivalente a gemini_service.py: _extraer_texto_docx() y _extraer_texto_xlsx().
 * (El extractor PDF de Python, _extraer_texto_pdf con PyMuPDF, existe pero NUNCA se
 * invoca en el flujo real de chat — los PDF se envían nativos a Gemini como bytes
 * inline — así que ese código muerto no se porta aquí; ver GeminiService.)
 */
@ApplicationScoped
public class DocumentExtractionService {

    private static final Logger LOG = Logger.getLogger(DocumentExtractionService.class);
    private static final int MAX_FILAS_XLSX = 200;

    /** Párrafos + filas de tablas unidas con " | " — idéntico a _extraer_texto_docx. */
    public String extraerTextoDocx(byte[] fileBytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            List<String> partes = new ArrayList<>();

            for (XWPFParagraph p : doc.getParagraphs()) {
                String texto = p.getText();
                if (texto != null && !texto.strip().isEmpty()) {
                    partes.add(texto);
                }
            }

            for (XWPFTable table : doc.getTables()) {
                for (var row : table.getRows()) {
                    String fila = row.getTableCells().stream()
                            .map(c -> c.getText() == null ? "" : c.getText().strip())
                            .filter(s -> !s.isEmpty())
                            .reduce((a, b) -> a + " | " + b)
                            .orElse("");
                    if (!fila.isEmpty()) {
                        partes.add(fila);
                    }
                }
            }

            return String.join("\n", partes);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Error extrayendo DOCX", e);
            return "(No se pudo extraer el contenido del documento Word)";
        }
    }

    /** Todas las hojas, filas unidas con " | ", tope de 200 filas por hoja — idéntico a _extraer_texto_xlsx. */
    public String extraerTextoXlsx(byte[] fileBytes) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            DataFormatter formatter = new DataFormatter();
            List<String> resultado = new ArrayList<>();

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                XSSFSheet sheet = wb.getSheetAt(s);
                resultado.add("### Hoja: " + wb.getSheetName(s));

                int filasConDatos = 0;
                for (Row row : sheet) {
                    List<String> valores = new ArrayList<>();
                    boolean tieneDatos = false;
                    short lastCol = row.getLastCellNum();
                    for (int c = 0; c < Math.max(lastCol, 0); c++) {
                        var cell = row.getCell(c);
                        String valor = cell == null ? "" : formatter.formatCellValue(cell).strip();
                        valores.add(valor);
                        if (!valor.isEmpty()) tieneDatos = true;
                    }
                    if (!tieneDatos) continue;

                    resultado.add(String.join(" | ", valores));
                    filasConDatos++;
                    if (filasConDatos > MAX_FILAS_XLSX) {
                        resultado.add("... (más filas omitidas por longitud)");
                        break;
                    }
                }
            }

            return String.join("\n", resultado);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Error extrayendo XLSX", e);
            return "(No se pudo extraer el contenido del archivo Excel)";
        }
    }
}
