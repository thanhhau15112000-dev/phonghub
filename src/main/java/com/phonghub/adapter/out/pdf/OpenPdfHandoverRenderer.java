package com.phonghub.adapter.out.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.phonghub.application.port.out.HandoverPdfPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dựng biên bản bàn giao bằng OpenPDF; nhúng font DejaVu Sans để hiển thị đủ dấu tiếng Việt. */
public class OpenPdfHandoverRenderer implements HandoverPdfPort {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final byte[] regular;
    private final byte[] bold;

    public OpenPdfHandoverRenderer() {
        this.regular = load("/fonts/DejaVuSans.ttf");
        this.bold = load("/fonts/DejaVuSans-Bold.ttf");
    }

    private static byte[] load(String path) {
        try (InputStream in = OpenPdfHandoverRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing font resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read font " + path, ex);
        }
    }

    @Override
    public byte[] render(Report report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle(report.title());
            document.addCreator("PhongHub");
            document.open();
            BaseFont regularBase = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                true, regular, null);
            BaseFont boldBase = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                true, bold, null);
            Font title = new Font(boldBase, 16);
            Font heading = new Font(boldBase, 11);
            Font body = new Font(regularBase, 9);
            Font bodyBold = new Font(boldBase, 9);
            Font small = new Font(regularBase, 7);

            Paragraph heading1 = new Paragraph(report.title(), title);
            heading1.setAlignment(Element.ALIGN_CENTER);
            heading1.setSpacingAfter(10);
            document.add(heading1);

            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100);
            info.setWidths(new float[]{30, 70});
            addInfo(info, "Nhà trọ", report.propertyName(), bodyBold, body);
            addInfo(info, "Phòng", report.roomNumber(), bodyBold, body);
            addInfo(info, "Người thuê chính", report.tenantName(), bodyBold, body);
            addInfo(info, "Mã hợp đồng", report.contractId(), bodyBold, body);
            addInfo(info, "Chỉ số điện", number(report.electricMeter()), bodyBold, body);
            addInfo(info, "Chỉ số nước", number(report.waterMeter()), bodyBold, body);
            addInfo(info, "Tình trạng phòng", report.roomCondition(), bodyBold, body);
            addInfo(info, "Chìa khóa / thiết bị bàn giao", report.keysProvided(), bodyBold, body);
            document.add(info);

            document.add(spaced(new Paragraph("Dấu thời gian xác nhận", heading), 12));
            PdfPTable stamps = new PdfPTable(2);
            stamps.setWidthPercentage(100);
            stamps.setWidths(new float[]{30, 70});
            addInfo(stamps, "Gửi cho người thuê", stamp(report.sentAt()), bodyBold, body);
            addInfo(stamps, "Người thuê xác nhận", stamp(report.tenantConfirmedAt()), bodyBold, body);
            addInfo(stamps, "Chủ trọ/quản lý duyệt", stamp(report.approvedAt())
                + (report.approvedBy() == null ? "" : " — " + report.approvedBy()), bodyBold, body);
            document.add(stamps);

            document.add(spaced(new Paragraph("Đối chiếu tài sản", heading), 12));
            PdfPTable items = new PdfPTable(report.checkOut() ? 6 : 4);
            items.setWidthPercentage(100);
            if (report.checkOut()) {
                items.setWidths(new float[]{22, 6, 18, 18, 18, 18});
                header(items, bodyBold, "Tài sản", "SL", "Lúc nhận", "Lúc trả", "Ghi chú lúc trả", "Khấu trừ (đ)");
            } else {
                items.setWidths(new float[]{30, 8, 22, 40});
                header(items, bodyBold, "Tài sản", "SL", "Tình trạng", "Ghi chú");
            }
            for (Row row : report.rows()) {
                if (report.checkOut()) {
                    cell(items, row.assetName() + (row.changed() ? "  *" : ""), body);
                    cell(items, String.valueOf(row.quantity()), body);
                    cell(items, status(row.checkInStatus()) + note(row.checkInNote()), body);
                    cell(items, status(row.checkOutStatus()), body);
                    cell(items, row.checkOutNote() == null ? "" : row.checkOutNote(), body);
                    cell(items, money(row.deduction()), body);
                } else {
                    cell(items, row.assetName(), body);
                    cell(items, String.valueOf(row.quantity()), body);
                    cell(items, status(row.checkInStatus()), body);
                    cell(items, row.checkInNote() == null ? "" : row.checkInNote(), body);
                }
            }
            document.add(items);
            if (report.checkOut()) {
                Paragraph legend = new Paragraph("* Tình trạng hoặc ghi chú khác với lúc nhận phòng.", small);
                legend.setSpacingBefore(3);
                document.add(legend);
                Paragraph deduction = new Paragraph("Khoản khấu trừ chốt: " + money(report.finalDeduction()) + " đ", bodyBold);
                deduction.setSpacingBefore(8);
                document.add(deduction);
            }

            if (!report.photos().isEmpty()) {
                document.add(spaced(new Paragraph("Ảnh phòng", heading), 12));
                PdfPTable photos = new PdfPTable(3);
                photos.setWidthPercentage(100);
                for (Photo photo : report.photos()) {
                    PdfPCell cell = new PdfPCell();
                    cell.setBorder(Rectangle.NO_BORDER);
                    cell.setPadding(3);
                    try {
                        Image image = Image.getInstance(photo.content());
                        image.scaleToFit(160, 120);
                        cell.addElement(image);
                        cell.addElement(new Phrase(photo.caption(), small));
                    } catch (Exception ex) {
                        cell.addElement(new Phrase("(không hiển thị được ảnh)", small));
                    }
                    photos.addCell(cell);
                }
                int remainder = report.photos().size() % 3;
                for (int i = 0; remainder != 0 && i < 3 - remainder; i++) {
                    PdfPCell empty = new PdfPCell();
                    empty.setBorder(Rectangle.NO_BORDER);
                    photos.addCell(empty);
                }
                document.add(photos);
            }

            Paragraph footer = new Paragraph("Xuất lúc " + stamp(report.generatedAt()) + " (giờ Việt Nam)."
                + (report.contractTermsHash() == null ? "" : "\nMã băm điều khoản hợp đồng: " + report.contractTermsHash())
                + (report.houseRulesHash() == null ? "" : "\nMã băm nội quy: " + report.houseRulesHash()), small);
            footer.setSpacingBefore(14);
            document.add(footer);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Không dựng được PDF biên bản", ex);
        }
    }

    private static Paragraph spaced(Paragraph paragraph, float before) {
        paragraph.setSpacingBefore(before);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private static void addInfo(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setPadding(4);
        table.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "" : value, valueFont));
        v.setPadding(4);
        table.addCell(v);
    }

    private static void header(PdfPTable table, Font font, String... labels) {
        for (String label : labels) {
            PdfPCell cell = new PdfPCell(new Phrase(label, font));
            cell.setBackgroundColor(new java.awt.Color(235, 235, 235));
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private static void cell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static String stamp(Instant instant) {
        return instant == null ? "—" : STAMP.format(instant.atZone(ZONE));
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(value == null ? BigDecimal.ZERO : value);
    }

    private static String note(String note) {
        return note == null || note.isBlank() ? "" : " (" + note.trim() + ")";
    }

    private static String status(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case "GOOD" -> "Tốt";
            case "DAMAGED" -> "Hư hỏng";
            case "LOST" -> "Mất";
            case "RETIRED" -> "Ngừng sử dụng";
            default -> status;
        };
    }
}
