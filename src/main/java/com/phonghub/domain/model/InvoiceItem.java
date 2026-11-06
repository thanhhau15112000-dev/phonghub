package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng chi tiết của hóa đơn. {@code sourceInvoiceId} khác null khi dòng này là khoản phí sửa chữa
 * được gộp từ một hóa đơn phí riêng vào hóa đơn tháng. {@code readingId} và {@code rateId} khác null khi dòng
 * là tiền điện/nước tính từ một chỉ số công tơ theo một đơn giá; {@code rateId} một mình là dịch vụ cố định.
 */
public record InvoiceItem(
    UUID id,
    String name,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal totalAmount,
    UUID sourceInvoiceId,
    UUID readingId,
    UUID rateId
) {
    public InvoiceItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên khoản thu không được để trống.");
        }
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (quantity == null || quantity.signum() <= 0) {
            quantity = BigDecimal.ONE;
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Đơn giá khoản thu không được âm.");
        }
        if (totalAmount == null) {
            totalAmount = unitPrice.multiply(quantity);
        }
    }

    public InvoiceItem(UUID id, String name, BigDecimal quantity, BigDecimal unitPrice, BigDecimal totalAmount,
                       UUID sourceInvoiceId) {
        this(id, name, quantity, unitPrice, totalAmount, sourceInvoiceId, null, null);
    }

    /** Dòng dịch vụ tính theo số lượng: thành tiền làm tròn đến đồng. */
    public static InvoiceItem charge(String name, BigDecimal quantity, BigDecimal unitPrice, UUID readingId,
                                     UUID rateId) {
        BigDecimal total = quantity.multiply(unitPrice).setScale(0, java.math.RoundingMode.HALF_UP);
        return new InvoiceItem(null, name, quantity, unitPrice, total, null, readingId, rateId);
    }

    public boolean isUtilityCharge() {
        return rateId != null;
    }

    private static final String CHECK_OUT_CHARGE_MARKER = " chốt check-out (";

    public static InvoiceItem checkOutCharge(String name, BigDecimal quantity, BigDecimal unitPrice,
                                             String unit, UUID rateId) {
        return charge(name + CHECK_OUT_CHARGE_MARKER + quantity.stripTrailingZeros().toPlainString()
            + " " + unit + ")", quantity, unitPrice, null, rateId);
    }

    /** Recognizes final-meter lines, including invoices issued before checkout revisions were supported. */
    public boolean isCheckOutCharge() {
        return isUtilityCharge() && readingId == null && name.contains(CHECK_OUT_CHARGE_MARKER);
    }

    public static final String LATE_FEE_NAME = "Phạt trả trễ";

    /** Dòng phạt trả trễ (#80): một dòng riêng, không gắn chỉ số, đơn giá hay hóa đơn nguồn. */
    /** Dòng phạt trễ hạn phí sửa chữa người thuê chịu (10% tổng tiền sửa chữa). */
    public static final String MAINTENANCE_LATE_FEE_NAME = "Phạt trễ hạn phí sửa chữa (10%)";

    public static InvoiceItem maintenanceLateFee(BigDecimal amount) {
        return new InvoiceItem(null, MAINTENANCE_LATE_FEE_NAME, BigDecimal.ONE, amount, amount, null, null, null);
    }

    public static InvoiceItem lateFee(BigDecimal amount) {
        return new InvoiceItem(null, LATE_FEE_NAME, BigDecimal.ONE, amount, amount, null, null, null);
    }

    public static InvoiceItem lateFee(BigDecimal amount, long days) {
        String name = days > 0 ? LATE_FEE_NAME + " (" + days + " ngày)" : LATE_FEE_NAME;
        return new InvoiceItem(null, name, BigDecimal.ONE, amount, amount, null, null, null);
    }

    public boolean isLateFee() {
        return (LATE_FEE_NAME.equals(name) || name.startsWith(LATE_FEE_NAME + " (")
                || MAINTENANCE_LATE_FEE_NAME.equals(name))
            && rateId == null && readingId == null && sourceInvoiceId == null;
    }

    public static InvoiceItem of(String name, BigDecimal amount, UUID sourceInvoiceId) {
        return new InvoiceItem(null, name, BigDecimal.ONE, amount, amount, sourceInvoiceId, null, null);
    }
}
