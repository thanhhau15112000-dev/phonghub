package com.phonghub.domain.model;

import com.phonghub.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private BigDecimal rentAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private LocalDate dueDate;
    private InvoiceStatus status;
    private final String paymentCode;
    private InvoiceType type;
    private final UUID ticketId;
    private UUID consolidatedIntoInvoiceId;
    private LocalDate cycleStartDate;
    private LocalDate cycleEndDate;
    private int cycleNumber;
    private BigDecimal depositPaidAmount;
    private BigDecimal settlementCreditAmount;
    private final List<InvoiceItem> items = new ArrayList<>();
    private Instant paidAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

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
        this(id, contractId, roomId, tenantId, month, year, rentAmount, totalAmount, paidAmount, dueDate, status,
            paymentCode, type, ticketId, null, List.of(), paidAt, createdAt, updatedAt, null, null, 0, BigDecimal.ZERO);
    }

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
        UUID consolidatedIntoInvoiceId,
        List<InvoiceItem> items,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, contractId, roomId, tenantId, month, year, rentAmount, totalAmount, paidAmount, dueDate, status,
            paymentCode, type, ticketId, consolidatedIntoInvoiceId, items, paidAt, createdAt, updatedAt,
            null, null, 0, BigDecimal.ZERO);
    }

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
        UUID consolidatedIntoInvoiceId,
        List<InvoiceItem> items,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        int cycleNumber,
        BigDecimal depositPaidAmount
    ) {
        this(id, contractId, roomId, tenantId, month, year, rentAmount, totalAmount, paidAmount, dueDate, status,
            paymentCode, type, ticketId, consolidatedIntoInvoiceId, items, paidAt, createdAt, updatedAt,
            cycleStartDate, cycleEndDate, cycleNumber, depositPaidAmount, BigDecimal.ZERO);
    }

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
        UUID consolidatedIntoInvoiceId,
        List<InvoiceItem> items,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        int cycleNumber,
        BigDecimal depositPaidAmount,
        BigDecimal settlementCreditAmount
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
        this.consolidatedIntoInvoiceId = consolidatedIntoInvoiceId;
        YearMonth invoicePeriod = YearMonth.of(year, month);
        this.cycleStartDate = cycleStartDate != null ? cycleStartDate
            : (this.type == InvoiceType.RENT ? invoicePeriod.atDay(1) : null);
        this.cycleEndDate = cycleEndDate != null ? cycleEndDate
            : (this.type == InvoiceType.RENT ? invoicePeriod.atEndOfMonth() : null);
        this.cycleNumber = cycleNumber;
        this.depositPaidAmount = depositPaidAmount != null ? depositPaidAmount : BigDecimal.ZERO;
        this.settlementCreditAmount = settlementCreditAmount != null ? settlementCreditAmount : BigDecimal.ZERO;
        if (this.depositPaidAmount.signum() < 0 || this.settlementCreditAmount.signum() < 0) {
            throw new IllegalArgumentException("Số tiền cọc và tiền hoàn/đối trừ không được âm.");
        }
        if (items != null) {
            this.items.addAll(items);
        }
        if (this.type == InvoiceType.MAINTENANCE && ticketId == null) {
            throw new IllegalArgumentException("Maintenance invoice requires a ticket id");
        }
        this.paidAt = paidAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    /**
     * Phát hành kỳ thanh toán tiền thuê có ngày bắt đầu trong tháng {@code period} của hợp đồng đang ở.
     * Hạn thanh toán là ngày bắt đầu kỳ cộng 7 ngày, nhưng không sớm hơn ngày tạo hóa đơn cộng 3 ngày.
     */
    public static Invoice issueMonthlyRent(Contract contract, YearMonth period, String paymentCode) {
        return issueMonthlyRent(contract, period, paymentCode, List.of());
    }

    /** Khoản phí sửa chữa chưa trả được gộp vào hóa đơn tháng, kèm tên phiếu để ghi dòng chi tiết. */
    public record RolledFee(Invoice fee, String ticketTitle) {}

    /** Mức sàn số ngày tối thiểu người thuê có để thanh toán, tính từ ngày tạo hóa đơn tiền thuê. */
    public static final int MIN_DAYS_TO_PAY = 3;

    /**
     * Như {@link #issueMonthlyRent(Contract, YearMonth, String)} nhưng gộp thêm các khoản phí sửa chữa chưa
     * thanh toán của hợp đồng. Mỗi khoản là một dòng chi tiết; hóa đơn phí gốc được đánh dấu đã gộp (không còn
     * thanh toán riêng). Khoản đã thanh toán hoặc đã trả một phần không được gộp.
     */
    public static Invoice issueMonthlyRent(
        Contract contract, YearMonth period, String paymentCode, List<RolledFee> rolledFees
    ) {
        return issueMonthlyRent(contract, period, paymentCode, rolledFees, null);
    }

    /**
     * Như {@link #issueMonthlyRent(Contract, YearMonth, String, List)}, phát hành vào ngày {@code issuedOn}.
     * Hạn thanh toán là ngày bắt đầu kỳ + 7 ngày, tối thiểu {@link #MIN_DAYS_TO_PAY} ngày sau khi tạo.
     */
    public static Invoice issueMonthlyRent(
        Contract contract, YearMonth period, String paymentCode, List<RolledFee> rolledFees, LocalDate issuedOn
    ) {
        if (contract == null || period == null) {
            throw new IllegalArgumentException("Contract and period are required");
        }
        // Hợp đồng đã hết hạn mà chưa trả phòng (#87) vẫn lập được hóa đơn các kỳ trong thời hạn hợp đồng.
        if (!contract.isResiding()) {
            throw new DomainException("Chỉ có thể tạo kỳ thanh toán cho hợp đồng đang hiệu lực hoặc chưa trả phòng.");
        }
        BillingCycle cycle = contract.billingCycle(period).orElse(null);
        if (cycle == null) {
            throw new DomainException(String.format(
                "Kỳ %02d/%d nằm ngoài thời hạn hợp đồng (%s - %s).",
                period.getMonthValue(), period.getYear(), contract.getStartDate(), contract.getEndDate()
            ));
        }
        if (paymentCode == null || paymentCode.isBlank()) {
            throw new IllegalArgumentException("Payment code is required");
        }
        LocalDate dueDate = cycle.startDate().plusDays(7);
        if (issuedOn != null && dueDate.isBefore(issuedOn.plusDays(MIN_DAYS_TO_PAY))) {
            dueDate = issuedOn.plusDays(MIN_DAYS_TO_PAY);
        }
        Instant now = Instant.now();
        UUID invoiceId = UUID.randomUUID();

        List<InvoiceItem> lines = new ArrayList<>();
        lines.add(InvoiceItem.of(
            cycleLabel("Tiền thuê", cycle),
            contract.getRentAmount(), null
        ));
        BigDecimal total = contract.getRentAmount();
        for (RolledFee rolled : rolledFees == null ? List.<RolledFee>of() : rolledFees) {
            Invoice fee = rolled.fee();
            if (!fee.isRollableFee() || !fee.getContractId().equals(contract.getId())) {
                throw new DomainException("Khoản phí sửa chữa không thể gộp vào hóa đơn tháng.");
            }
            String title = rolled.ticketTitle() != null && !rolled.ticketTitle().isBlank()
                ? rolled.ticketTitle().trim() : "Phiếu bảo trì";
            lines.add(InvoiceItem.of("Phí sửa chữa: " + title, fee.remainingAmount(), fee.getId()));
            total = total.add(fee.remainingAmount());
        }

        Invoice invoice = new Invoice(
            invoiceId,
            contract.getId(),
            contract.getRoomId(),
            contract.getPrimaryTenantId(),
            period.getMonthValue(),
            period.getYear(),
            contract.getRentAmount(),
            total,
            BigDecimal.ZERO,
            dueDate,
            InvoiceStatus.ISSUED,
            paymentCode,
            InvoiceType.RENT,
            null,
            null,
            lines,
            null,
            now,
            now,
            cycle.startDate(),
            cycle.endDate(),
            cycle.number(),
            BigDecimal.ZERO
        );
        if (rolledFees != null) {
            rolledFees.forEach(rolled -> rolled.fee().consolidateInto(invoiceId));
        }
        return invoice;
    }

    /** Hóa đơn thay thế kỳ thuê khi hủy sớm; tiền thuê đã trả trước được khấu trừ vào khoản phí mới. */
    public static Invoice issueCancellationFee(
        Contract contract, BillingCycle cycle, BigDecimal rentFee, List<InvoiceItem> utilityCharges,
        String paymentCode, LocalDate issuedOn
    ) {
        if (contract == null || cycle == null || issuedOn == null) {
            throw new IllegalArgumentException("Contract, cycle and issue date are required");
        }
        if (paymentCode == null || paymentCode.isBlank()) {
            throw new IllegalArgumentException("Payment code is required");
        }
        if (rentFee == null || rentFee.signum() < 0) {
            throw new IllegalArgumentException("Phí thuê hủy hợp đồng không được âm.");
        }
        List<InvoiceItem> lines = new ArrayList<>();
        lines.add(InvoiceItem.of(cycleLabel("Phí hủy hợp đồng", cycle), rentFee, null));
        if (utilityCharges != null) {
            lines.addAll(utilityCharges);
        }
        BigDecimal total = lines.stream().map(InvoiceItem::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant now = Instant.now();
        return new Invoice(UUID.randomUUID(), contract.getId(), contract.getRoomId(), contract.getPrimaryTenantId(),
            cycle.period().getMonthValue(), cycle.period().getYear(), rentFee, total, BigDecimal.ZERO,
            issuedOn.plusDays(7), InvoiceStatus.ISSUED, paymentCode, InvoiceType.CANCELLATION, null, null, lines,
            null, now, now, cycle.startDate(), cycle.endDate(), cycle.number(), BigDecimal.ZERO);
    }

    /** Reclassifies the current rent invoice as the cancellation invoice and returns any refundable overpayment. */
    public BigDecimal replaceWithCancellationFee(
        BillingCycle cycle, BigDecimal rentFee, List<InvoiceItem> utilityCharges, LocalDate issuedOn
    ) {
        if (type != InvoiceType.RENT || status == InvoiceStatus.VOIDED || status == InvoiceStatus.DRAFT
            || cycle == null || issuedOn == null || rentFee == null || rentFee.signum() < 0) {
            throw new DomainException("Hóa đơn tiền thuê không thể thay bằng phí hủy hợp đồng.");
        }
        BigDecimal previouslyPaid = paidAmount;
        List<InvoiceItem> preserved = items.stream()
            .filter(item -> item.isUtilityCharge() || item.sourceInvoiceId() != null || item.isLateFee())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (utilityCharges != null) {
            for (InvoiceItem charge : utilityCharges) {
                boolean duplicate = preserved.stream().anyMatch(item -> item.readingId() != null
                    && item.readingId().equals(charge.readingId()) && item.rateId().equals(charge.rateId()));
                if (!duplicate) {
                    preserved.add(charge);
                }
            }
        }
        items.clear();
        items.add(InvoiceItem.of(cycleLabel("Phí hủy hợp đồng", cycle), rentFee, null));
        items.addAll(preserved);
        rentAmount = rentFee;
        totalAmount = items.stream().map(InvoiceItem::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overpayment = previouslyPaid.subtract(totalAmount).max(BigDecimal.ZERO);
        settlementCreditAmount = overpayment;
        paidAmount = previouslyPaid.min(totalAmount);
        depositPaidAmount = depositPaidAmount.min(paidAmount);
        type = InvoiceType.CANCELLATION;
        dueDate = issuedOn.plusDays(7);
        cycleStartDate = cycle.startDate();
        cycleEndDate = cycle.endDate();
        cycleNumber = cycle.number();
        if (paidAmount.signum() == 0) {
            status = InvoiceStatus.ISSUED;
            paidAt = null;
        } else if (paidAmount.compareTo(totalAmount) >= 0) {
            status = InvoiceStatus.PAID;
        } else {
            status = InvoiceStatus.PARTIALLY_PAID;
            paidAt = null;
        }
        updatedAt = Instant.now();
        return overpayment;
    }

    /** Voids future rent when a contract ends; prior receipts remain as a settlement credit. */
    public BigDecimal voidForEarlyTermination() {
        if (type != InvoiceType.RENT || status == InvoiceStatus.VOIDED || status == InvoiceStatus.DRAFT) {
            return BigDecimal.ZERO;
        }
        BigDecimal refundable = paidAmount;
        settlementCreditAmount = refundable;
        status = InvoiceStatus.VOIDED;
        updatedAt = Instant.now();
        return refundable;
    }

    private static String cycleLabel(String prefix, BillingCycle cycle) {
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return String.format("%s kỳ %d (%s – %s)", prefix, cycle.number(),
            cycle.startDate().format(date), cycle.endDate().format(date));
    }

    /**
     * Còn thêm được dòng điện/nước/dịch vụ: hóa đơn tiền thuê chưa ai trả đồng nào (tổng tiền chưa bị người thuê
     * chuyển khoản theo), chưa gộp, chưa hủy.
     */
    public boolean canAddCharges() {
        return type == InvoiceType.RENT
            && consolidatedIntoInvoiceId == null
            && paidAmount.signum() == 0
            && (status == InvoiceStatus.ISSUED || status == InvoiceStatus.OVERDUE);
    }

    /** Đã có dòng tính từ chỉ số {@code readingId} theo đơn giá {@code rateId}. */
    public boolean hasCharge(UUID readingId, UUID rateId) {
        return items.stream().anyMatch(item -> rateId.equals(item.rateId())
            && java.util.Objects.equals(readingId, item.readingId()));
    }

    /** Tổng tiền điện, nước, dịch vụ (các dòng tính theo đơn giá). */
    public BigDecimal utilityAmount() {
        return items.stream().filter(InvoiceItem::isUtilityCharge).map(InvoiceItem::totalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Thêm các dòng điện/nước/dịch vụ và cộng vào tổng tiền. Dòng đã có (cùng chỉ số, cùng đơn giá) bị bỏ qua nên
     * gọi lặp lại không tính tiền hai lần. Trả về số dòng thực sự được thêm.
     */
    public int addCharges(List<InvoiceItem> charges) {
        List<InvoiceItem> fresh = charges.stream().filter(InvoiceItem::isUtilityCharge)
            .filter(item -> !hasCharge(item.readingId(), item.rateId())).toList();
        if (fresh.isEmpty()) {
            return 0;
        }
        if (!canAddCharges()) {
            throw new DomainException("Hóa đơn đã có thanh toán hoặc đã đóng, không thể thêm khoản mới.");
        }
        for (InvoiceItem item : fresh) {
            items.add(item);
            totalAmount = totalAmount.add(item.totalAmount());
        }
        this.updatedAt = Instant.now();
        return fresh.size();
    }

    /** Dòng phạt trả trễ hiện có (tối đa một dòng). */
    public java.util.Optional<InvoiceItem> lateFeeItem() {
        return items.stream().filter(InvoiceItem::isLateFee).findFirst();
    }

    /** Tiền hóa đơn chưa tính phạt trả trễ; phạt theo phần trăm được tính trên số này. */
    public BigDecimal principalAmount() {
        return totalAmount.subtract(lateFeeItem().map(InvoiceItem::totalAmount).orElse(BigDecimal.ZERO));
    }

    /**
     * Đặt dòng phạt trả trễ bằng đúng {@code fee} (thay chứ không cộng dồn nên gọi lặp lại cho cùng số tiền không
     * đổi gì). Chỉ áp dụng cho hóa đơn tiền thuê còn phải thanh toán; không bao giờ xóa phạt đã tính.
     * Trả về true nếu hóa đơn thực sự thay đổi.
     */
    public boolean applyLateFee(BigDecimal fee) {
        return applyLateFee(fee, 0);
    }

    public boolean applyLateFee(BigDecimal fee, long penaltyDays) {
        if (fee == null || fee.signum() <= 0 || type != InvoiceType.RENT || !isPayable()) {
            return false;
        }
        java.util.Optional<InvoiceItem> existing = lateFeeItem();
        if (existing.isPresent() && existing.get().totalAmount().compareTo(fee) == 0
            && existing.get().name().equals(InvoiceItem.lateFee(fee, penaltyDays).name())) {
            return false;
        }
        existing.ifPresent(item -> {
            items.remove(item);
            totalAmount = totalAmount.subtract(item.totalAmount());
        });
        items.add(InvoiceItem.lateFee(fee, penaltyDays));
        totalAmount = totalAmount.add(fee);
        this.updatedAt = Instant.now();
        return true;
    }

    /** Khoản phí sửa chữa chưa trả đồng nào và chưa được gộp: đủ điều kiện gộp vào hóa đơn tháng. */
    public boolean isRollableFee() {
        return type == InvoiceType.MAINTENANCE
            && consolidatedIntoInvoiceId == null
            && isPayable()
            && paidAmount.signum() == 0;
    }

    /** Khoản phí được gộp vào hóa đơn tháng: từ đây chỉ thanh toán qua hóa đơn tháng. */
    public void consolidateInto(UUID monthlyInvoiceId) {
        if (monthlyInvoiceId == null) {
            throw new IllegalArgumentException("Monthly invoice id is required");
        }
        if (!isRollableFee()) {
            throw new DomainException("Khoản phí sửa chữa không thể gộp vào hóa đơn tháng.");
        }
        this.consolidatedIntoInvoiceId = monthlyInvoiceId;
        this.updatedAt = Instant.now();
    }

    public boolean isConsolidated() {
        return consolidatedIntoInvoiceId != null;
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
        if (!contract.isResiding()) {
            throw new DomainException("Chỉ có thể tạo khoản phí cho hợp đồng đang hiệu lực hoặc chưa trả phòng.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Phí sửa chữa phải lớn hơn 0.");
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

    /** Hóa đơn chốt điện/nước từ chỉ số cuối đã người thuê xác nhận trong biên bản check-out. */
    public static Invoice issueCheckOutUtilities(
        Contract contract, YearMonth period, String paymentCode, LocalDate dueDate, List<InvoiceItem> charges
    ) {
        if (contract == null || period == null || dueDate == null || charges == null || charges.isEmpty()) {
            throw new IllegalArgumentException("Contract, period, due date and utility charges are required");
        }
        BigDecimal total = charges.stream().map(InvoiceItem::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("Checkout utility invoice must be greater than zero");
        }
        Instant now = Instant.now();
        return new Invoice(UUID.randomUUID(), contract.getId(), contract.getRoomId(), contract.getPrimaryTenantId(),
            period.getMonthValue(), period.getYear(), BigDecimal.ZERO, total, BigDecimal.ZERO, dueDate,
            InvoiceStatus.ISSUED, paymentCode, InvoiceType.CHECK_OUT, null, null, charges, null, now, now);
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
        return consolidatedIntoInvoiceId == null && (status == InvoiceStatus.ISSUED
            || status == InvoiceStatus.PARTIALLY_PAID
            || status == InvoiceStatus.OVERDUE);
    }

    /**
     * Cộng dồn số tiền nhận được. Đủ tổng tiền thì chuyển sang PAID, chưa đủ thì PARTIALLY_PAID.
     */
    public void applyPayment(BigDecimal amount, Instant receivedAt) {
        if (!isPayable()) {
            throw new DomainException("Kỳ thanh toán không ở trạng thái chờ thanh toán: " + status);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0.");
        }
        if (amount.compareTo(remainingAmount()) > 0) {
            throw new DomainException("Số tiền vượt quá số còn nợ của hóa đơn; phần dư phải được tách ra trước khi ghi nhận.");
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

    /** Cọc được thanh toán như một khoản tiền vào hóa đơn, có nguồn riêng để hiện trong biên lai. */
    public void applyPaymentFromDeposit(BigDecimal amount, Instant receivedAt) {
        applyPayment(amount, receivedAt);
        depositPaidAmount = depositPaidAmount.add(amount);
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
    public UUID getConsolidatedIntoInvoiceId() { return consolidatedIntoInvoiceId; }
    public LocalDate getCycleStartDate() { return cycleStartDate; }
    public LocalDate getCycleEndDate() { return cycleEndDate; }
    public int getCycleNumber() { return cycleNumber; }
    public BigDecimal getDepositPaidAmount() { return depositPaidAmount; }
    public BigDecimal getSettlementCreditAmount() { return settlementCreditAmount; }
    public List<InvoiceItem> getItems() { return Collections.unmodifiableList(items); }

    /** Dùng khi nạp từ cơ sở dữ liệu. */
    public void loadItems(List<InvoiceItem> loaded) {
        items.clear();
        if (loaded != null) {
            items.addAll(loaded);
        }
    }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Phiên bản dùng cho khóa lạc quan; do tầng lưu trữ gán sau khi đọc/ghi. */
    public long getVersion() {
        return version;
    }

    public void restoreVersion(long version) {
        this.version = version;
    }
}
