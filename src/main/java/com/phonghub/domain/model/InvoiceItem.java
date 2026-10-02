package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng chi tiết của hóa đơn. {@code sourceInvoiceId} khác null khi dòng này là khoản phí sửa chữa
 * được gộp từ một hóa đơn phí riêng vào hóa đơn tháng.
 */
public record InvoiceItem(
    UUID id,
    String name,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal totalAmount,
    UUID sourceInvoiceId
) {
    public InvoiceItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Invoice item name cannot be blank");
        }
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (quantity == null || quantity.signum() <= 0) {
            quantity = BigDecimal.ONE;
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Invoice item unit price cannot be negative");
        }
        if (totalAmount == null) {
            totalAmount = unitPrice.multiply(quantity);
        }
    }

    public static InvoiceItem of(String name, BigDecimal amount, UUID sourceInvoiceId) {
        return new InvoiceItem(null, name, BigDecimal.ONE, amount, amount, sourceInvoiceId);
    }
}
