package com.phonghub.adapter.out.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.phonghub.application.port.out.TableExportPort.Table;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #85: tệp Excel/PDF đọc lại được, giữ kiểu dữ liệu và dấu tiếng Việt. */
class TableExportersTest {

    private static Table table() {
        List<Object> first = new ArrayList<>(List.of("Nhà trọ Xanh", "P101", new BigDecimal("3500000")));
        first.add(LocalDate.of(2026, 10, 5));
        List<Object> second = new ArrayList<>();
        second.add("Khu trọ Tân Bình");
        second.add(null);
        second.add(new BigDecimal("1250000.50"));
        second.add(null);
        return new Table("Hóa đơn 10/2026", "2 nhà trọ trong phạm vi", List.of("Nhà trọ", "Phòng", "Tổng tiền", "Hạn"),
            List.of(first, second));
    }

    @Test
    @DisplayName("Excel: tiêu đề, dòng tiêu đề cột cố định, số là kiểu số, ngày là kiểu ngày, ô trống bỏ trống")
    void xlsx() throws Exception {
        XlsxTableExporter exporter = new XlsxTableExporter();
        byte[] bytes = exporter.render(table());

        assertEquals("xlsx", exporter.extension());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Hóa đơn 10-2026", sheet.getSheetName(), "tên sheet bỏ ký tự không hợp lệ");
            assertEquals("Hóa đơn 10/2026", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("2 nhà trọ trong phạm vi", sheet.getRow(1).getCell(0).getStringCellValue());
            Row header = sheet.getRow(3);
            assertEquals("Tổng tiền", header.getCell(2).getStringCellValue());
            Row first = sheet.getRow(4);
            assertEquals(CellType.NUMERIC, first.getCell(2).getCellType());
            assertEquals(3500000.0, first.getCell(2).getNumericCellValue());
            assertTrue(DateUtil.isCellDateFormatted(first.getCell(3)));
            assertEquals(LocalDate.of(2026, 10, 5), first.getCell(3).getLocalDateTimeCellValue().toLocalDate());
            Row second = sheet.getRow(5);
            assertEquals(null, second.getCell(1));
            assertEquals(1250000.5, second.getCell(2).getNumericCellValue());
            assertEquals(5, sheet.getLastRowNum());
            assertTrue(sheet.getPaneInformation().isFreezePane());
        }
    }

    @Test
    @DisplayName("PDF: tệp hợp lệ, có tiếng Việt, số định dạng hàng nghìn, ngày dd/MM/yyyy; bảng rỗng ghi rõ")
    void pdf() throws Exception {
        PdfTableExporter exporter = new PdfTableExporter();
        byte[] bytes = exporter.render(table());

        assertEquals("pdf", exporter.extension());
        assertTrue(new String(bytes, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
        String text = new PdfTextExtractor(new PdfReader(bytes)).getTextFromPage(1);
        assertTrue(text.contains("Hóa đơn 10/2026"), text);
        assertTrue(text.contains("Khu trọ Tân Bình"), text);
        assertTrue(text.contains("3,500,000"), text);
        assertTrue(text.contains("05/10/2026"), text);

        String empty = new PdfTextExtractor(new PdfReader(exporter.render(
            new Table("Công nợ", null, List.of("Nhà trọ"), List.of())))).getTextFromPage(1);
        assertTrue(empty.contains("Không có dữ liệu."), empty);
    }
}
