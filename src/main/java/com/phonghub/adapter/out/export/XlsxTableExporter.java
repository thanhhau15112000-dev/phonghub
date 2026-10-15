package com.phonghub.adapter.out.export;

import com.phonghub.application.port.out.TableExportPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Xuất bảng ra Excel (.xlsx): số giữ kiểu số để cộng/lọc được, ngày giữ kiểu ngày. */
public class XlsxTableExporter implements TableExportPort {

    private static final int HEADER_ROW = 3;
    private static final int MAX_COLUMN_CHARS = 60;

    @Override
    public byte[] render(Table table) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(table.title(), '-'));

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));

            Cell title = sheet.createRow(0).createCell(0);
            title.setCellValue(table.title());
            title.setCellStyle(titleStyle);
            if (table.subtitle() != null) {
                sheet.createRow(1).createCell(0).setCellValue(table.subtitle());
            }

            int[] widths = new int[table.headers().size()];
            Row header = sheet.createRow(HEADER_ROW);
            for (int c = 0; c < table.headers().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(table.headers().get(c));
                cell.setCellStyle(headerStyle);
                widths[c] = table.headers().get(c).length();
            }

            int r = HEADER_ROW + 1;
            for (List<Object> values : table.rows()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < values.size() && c < widths.length; c++) {
                    Object value = values.get(c);
                    if (value == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (value instanceof BigDecimal decimal) {
                        cell.setCellValue(decimal.doubleValue());
                        cell.setCellStyle(numberStyle);
                        widths[c] = Math.max(widths[c], decimal.toBigInteger().toString().length() + 4);
                    } else if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                        cell.setCellStyle(numberStyle);
                        widths[c] = Math.max(widths[c], number.toString().length() + 2);
                    } else if (value instanceof LocalDate date) {
                        cell.setCellValue(date);
                        cell.setCellStyle(dateStyle);
                        widths[c] = Math.max(widths[c], 10);
                    } else {
                        String text = value.toString();
                        cell.setCellValue(text);
                        widths[c] = Math.max(widths[c], text.length());
                    }
                }
            }

            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, (Math.min(widths[c], MAX_COLUMN_CHARS) + 2) * 256);
            }
            sheet.createFreezePane(0, HEADER_ROW + 1);
            if (!table.headers().isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, Math.max(HEADER_ROW, r - 1), 0,
                    table.headers().size() - 1));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được tệp Excel", ex);
        }
    }

    @Override
    public String contentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public String extension() {
        return "xlsx";
    }
}
