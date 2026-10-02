package com.phonghub.domain.model;

import com.phonghub.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Kỳ thanh toán tiền thuê hằng tháng của một hợp đồng.
 * Người thuê chuyển khoản với nội dung chứa paymentCode; webhook SePay khớp mã để ghi nhận tiền.
 */
public class Invoice {

    private final UUID id;
    private final UUID contractId;
    private final UUID roomId;
    private final UUID tenantId;
    private final int month;
    private final int year;
    private final BigDecimal rentAmount;
    private final BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private final LocalDate dueDate;
    private InvoiceStatus status;
    private final String paymentCode;
    private final InvoiceType type;
    private final UUID ticketId;
    private Instant paidAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public Invoice(
        UUID id,
        UUID contractId,
        UUID roomId,
        UUID tenantId,
        int month,
        int year,
        BigDecimal rentAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        LocalDate dueDate,
        InvoiceStatus status,
        String paymentCode,
        InvoiceType type,
        UUID ticketId,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        if (id == null || contractId == null || roomId == null || tenantId == null) {
            throw new IllegalArgumentException("Invoice id, contract, room and tenant cannot be null");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invoice month must be between 1 and 12");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invoice total amount cannot be negative");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("Invoice due date cannot be null");
        }
        this.id = id;
        this.contractId = contractId;
        this.roomId = roomId;
        this.tenantId = tenantId;
        this.month = month;
        this.year = year;
        this.rentAmount = rentAmount != null ? rentAmount : totalAmount;
        this.totalAmount = totalAmount;
        this.paidAmount = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        this.dueDate = dueDate;
        this.status = status != null ? status : InvoiceStatus.ISSUED;
        this.paymentCode = paymentCode;
        this.type = type != null ? type : InvoiceType.RENT;
        this.ticketId = ticketId;
        if (this.type == InvoiceType.MAINTENANCE && ticketId == null) {
            throw new IllegalArgumentException("Maintenance invoice requires a ticket id");
        }
        this.paidAt = paidAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Phát hành kỳ thanh toán tiền thuê cho tháng {@code period} của hợp đồng đang hiệu lực.
     * Hạn thanh toán = ngày thanh toán của hợp đồng, kẹp về ngày cuối tháng nếu tháng ngắn hơn.
     */
    public static Invoice issueMonthlyRent(Contract contract, YearMonth period, String paymentCode) {
        if (contract == null || period == null) {
            throw new IllegalArgumentException("Contract and period are required");
        }
        if (!contract.isActive()) {
            throw new DomainException("Chỉ có thể tạo kỳ thanh toán cho hợp đồng đang hiệu lực.");
        }
        YearMonth firstPeriod = YearMonth.from(contract.getStartDate());
        YearMonth lastPeriod = YearMonth.from(contract.getEndDate());
        if (period.isBefore(firstPeriod) || period.isAfter(lastPeriod)) {
            throw new DomainException(String.format(
                "Kỳ %02d/%d nằm ngoài thời hạn hợp đồng (%s - %s).",
                period.getMonthValue(), period.getYear(), contract.getStartDate(), contract.getEndDate()
            ));
        }
        if (paymentCode == null || paymentCode.isBlank()) {
            throw new IllegalArgumentException("Payment code is required");
        }
        int dueDay = Math.min(contract.getPaymentDay(), period.lengthOfMonth());
        Instant now = Instant.now();
        return new Invoice(
            UUID.randomUUID(),
            contract.getId(),
            contract.getRoomId(),
            contract.getPrimaryTenantId(),
            period.getMonthValue(),
            period.getYear(),
            contract.getRentAmount(),
            contract.getRentAmount(),
            BigDecimal.ZERO,
            period.atDay(dueDay),
            InvoiceStatus.ISSUED,
            paymentCode,
            InvoiceType.RENT,
            null,
            null,
            now,
            now
        );
    }

    /** Số ngày người thuê có để thanh toán phí sửa chữa kể từ ngày phát hành. */
    public static final int MAINTENANCE_FEE_DUE_DAYS = 7;

    /**
     * Khoản phí sửa chữa người thuê phải trả cho một phiếu bảo trì, gắn với hợp đồng ACTIVE của phòng.
     */
    public static Invoice issueMaintenanceFee(
        Contract contract, UUID ticketId, BigDecimal amount, String paymentCode, LocalDate today
    ) {
        if (contract == null || ticketId == null || today == null) {
            throw new IllegalArgumentException("Contract, ticket and date are required");
        }
        if (!contract.isActive()) {
            throw new DomainException("Chỉ có thể tạo khoản phí cho hợp đồng đang hiệu lực.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Fee amount must be greater than zero");
        }
        if (paymentCode == null || paymentCode.isBlank()) {
            throw new IllegalArgumentException("Payment code is required");
        }
        Instant now = Instant.now();
        return new Invoice(
            UUID.randomUUID(),
            contract.getId(),
            contract.getRoomId(),
            contract.getPrimaryTenantId(),
            today.getMonthValue(),
            today.getYear(),
            BigDecimal.ZERO,
            amount,
            BigDecimal.ZERO,
            today.plusDays(MAINTENANCE_FEE_DUE_DAYS),
            InvoiceStatus.ISSUED,
            paymentCode,
            InvoiceType.MAINTENANCE,
            ticketId,
            null,
            now,
            now
        );
    }

    /** Hủy hóa đơn chưa có tiền nào được ghi nhận. */
    public void voidInvoice() {
        if (!isPayable() && status != InvoiceStatus.DRAFT) {
            throw new DomainException("Không thể hủy hóa đơn ở trạng thái " + status);
        }
        if (paidAmount.signum() > 0) {
            throw new DomainException("Hóa đơn đã được thanh toán một phần, không thể hủy.");
        }
        this.status = InvoiceStatus.VOIDED;
        this.updatedAt = Instant.now();
    }

    public boolean isPayable() {
        return status == InvoiceStatus.ISSUED
            || status == InvoiceStatus.PARTIALLY_PAID
            || status == InvoiceStatus.OVERDUE;
    }

    /**
     * Cộng dồn số tiền nhận được. Đủ tổng tiền thì chuyển sang PAID, chưa đủ thì PARTIALLY_PAID.
     */
    public void applyPayment(BigDecimal amount, Instant receivedAt) {
        if (!isPayable()) {
            throw new DomainException("Kỳ thanh toán không ở trạng thái chờ thanh toán: " + status);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        Instant at = receivedAt != null ? receivedAt : Instant.now();
        this.paidAmount = this.paidAmount.add(amount);
        if (this.paidAmount.compareTo(totalAmount) >= 0) {
            this.status = InvoiceStatus.PAID;
            this.paidAt = at;
        } else {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
        this.updatedAt = at;
    }

    public BigDecimal remainingAmount() {
        BigDecimal remaining = totalAmount.subtract(paidAmount);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    /** Đã tới hoặc qua hạn và còn nợ. */
    public boolean isDue(LocalDate today) {
        return isPayable() && !today.isBefore(dueDate);
    }

    public boolean isOverdue(LocalDate today) {
        return isPayable() && today.isAfter(dueDate);
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public UUID getRoomId() { return roomId; }
    public UUID getTenantId() { return tenantId; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public BigDecimal getRentAmount() { return rentAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public LocalDate getDueDate() { return dueDate; }
    public InvoiceStatus getStatus() { return status; }
    public String getPaymentCode() { return paymentCode; }
    public InvoiceType getType() { return type; }
    public UUID getTicketId() { return ticketId; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
