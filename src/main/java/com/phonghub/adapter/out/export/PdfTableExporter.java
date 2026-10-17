package com.phonghub.adapter.out.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.phonghub.application.port.out.TableExportPort;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Xuất bảng ra PDF khổ A4 ngang; nhúng font DejaVu Sans để hiển thị đủ dấu tiếng Việt. */
public class PdfTableExporter implements TableExportPort {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final byte[] regular;
    private final byte[] bold;

    public PdfTableExporter() {
        this.regular = load("/fonts/DejaVuSans.ttf");
        this.bold = load("/fonts/DejaVuSans-Bold.ttf");
    }

    private static byte[] load(String path) {
        try (InputStream in = PdfTableExporter.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing font resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read font " + path, ex);
        }
    }

    @Override
    public byte[] render(Table table) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            BaseFont regularBase = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                true, regular, null);
            BaseFont boldBase = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                true, bold, null);
            Font titleFont = new Font(boldBase, 14);
            Font subtitleFont = new Font(regularBase, 9, Font.NORMAL, Color.DARK_GRAY);
            Font headerFont = new Font(boldBase, 8);
            Font bodyFont = new Font(regularBase, 8);

            document.add(new Paragraph(table.title(), titleFont));
            if (table.subtitle() != null) {
                document.add(new Paragraph(table.subtitle(), subtitleFont));
            }
            document.add(new Paragraph(" ", bodyFont));

            if (table.headers().isEmpty()) {
                document.close();
                return out.toByteArray();
            }
            PdfPTable pdfTable = new PdfPTable(table.headers().size());
            pdfTable.setWidthPercentage(100);
            pdfTable.setHeaderRows(1);
            for (String header : table.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(new Color(229, 231, 235));
                cell.setPadding(4);
                pdfTable.addCell(cell);
            }
            if (table.rows().isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("Không có dữ liệu.", bodyFont));
                empty.setColspan(table.headers().size());
                empty.setPadding(4);
                pdfTable.addCell(empty);
            }
            DecimalFormat money = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
            for (List<Object> values : table.rows()) {
                for (int c = 0; c < table.headers().size(); c++) {
                    Object value = c < values.size() ? values.get(c) : null;
                    PdfPCell cell = new PdfPCell(new Phrase(format(value, money), bodyFont));
                    cell.setPadding(3);
                    if (value instanceof Number) {
                        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    }
                    pdfTable.addCell(cell);
                }
            }
            document.add(pdfTable);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Không tạo được tệp PDF", ex);
        }
    }

    private static String format(Object value, DecimalFormat money) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal || value instanceof Number) {
            return money.format(value);
        }
        if (value instanceof LocalDate date) {
            return DATE.format(date);
        }
        return value.toString();
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public String extension() {
        return "pdf";
    }
}
