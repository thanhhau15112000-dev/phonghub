package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một phiên bản báo giá sửa chữa gửi người thuê phê duyệt (#203). Giữ lại mọi phiên bản để thương lượng có lịch sử.
 */
public record MaintenanceQuoteVersion(
    UUID id,
    UUID ticketId,
    int version,
    List<Line> lines,
    BigDecimal laborAmount,
    BigDecimal totalAmount,
    String note,
    UUID quotedBy,
    Instant quotedAt,
    Status status,
    String tenantNote,
    Instant decidedAt
) {

    public enum Status {
        /** Đang chờ người thuê phê duyệt hoặc thương lượng. */
        PENDING,
        APPROVED,
        /** Người thuê đề nghị điều chỉnh; người lập báo giá gửi phiên bản mới. */
        NEGOTIATED,
        /** Quá hạn phê duyệt, phiếu bị hủy tự động. */
        EXPIRED
    }

    /** Dòng linh kiện: tên, số lượng, đơn giá. */
    public record Line(String name, int quantity, BigDecimal unitPrice) {
        public Line {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Tên linh kiện không được để trống.");
            }
            if (name.trim().length() > 200) {
                throw new IllegalArgumentException("Tên linh kiện tối đa 200 ký tự.");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Số lượng linh kiện phải lớn hơn 0.");
            }
            if (unitPrice == null || unitPrice.signum() < 0) {
                throw new IllegalArgumentException("Đơn giá linh kiện không được âm.");
            }
            name = name.trim();
        }

        public BigDecimal amount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public MaintenanceQuoteVersion {
        lines = lines == null ? List.of() : List.copyOf(lines);
        laborAmount = laborAmount == null ? BigDecimal.ZERO : laborAmount;
    }

    /** Tổng = Σ(số lượng × đơn giá) + công sửa. */
    public static BigDecimal total(List<Line> lines, BigDecimal laborAmount) {
        BigDecimal sum = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.add(laborAmount == null ? BigDecimal.ZERO : laborAmount);
    }

    public MaintenanceQuoteVersion decided(Status newStatus, String tenantNote, Instant at) {
        return new MaintenanceQuoteVersion(id, ticketId, version, lines, laborAmount, totalAmount, note, quotedBy,
            quotedAt, newStatus, tenantNote, at);
    }
}
