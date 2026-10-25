package com.phonghub.application.port.in;

import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quyết toán tiền cọc khi trả phòng: tiền cọc trừ khoản khấu trừ tài sản, hóa đơn còn nợ và tiền điện/nước từ lần ghi
 * chỉ số gần nhất tới chỉ số trong biên bản check-out (ước tính, chưa lập hóa đơn). Chỉ người xem được số liệu tiền của
 * hợp đồng (chủ trọ, nhân viên có quyền, người thuê của hợp đồng) mới có kết quả.
 */
public interface CheckOutSettlementUseCase {

    /** Rỗng khi hợp đồng chưa có biên bản check-out hoặc người xem không có quyền xem số liệu tiền. */
    Optional<Settlement> settlement(UUID contractId);

    /** Điện/nước tiêu thụ chưa tính tiền: từ {@code fromValue} (lần ghi gần nhất) tới {@code toValue} (biên bản). */
    record UtilityEstimate(MeterType meterType, BigDecimal fromValue, BigDecimal toValue, BigDecimal consumption,
                           BigDecimal unitPrice, BigDecimal amount) {}

    record OutstandingInvoice(UUID id, InvoiceType type, int month, int year, BigDecimal remainingAmount,
                              LocalDate dueDate) {}

    record Settlement(
        BigDecimal deposit,
        BigDecimal deduction,
        /** true khi khoản khấu trừ đã được chủ trọ chốt lúc duyệt; false là tổng đề xuất trên từng tài sản. */
        boolean deductionFinal,
        BigDecimal outstanding,
        int unpaidInvoices,
        List<UtilityEstimate> utilities,
        BigDecimal depositOffset,
        List<OutstandingInvoice> openInvoices
    ) {
        public Settlement(BigDecimal deposit, BigDecimal deduction, boolean deductionFinal, BigDecimal outstanding,
                          int unpaidInvoices, List<UtilityEstimate> utilities) {
            this(deposit, deduction, deductionFinal, outstanding, unpaidInvoices, utilities, BigDecimal.ZERO, List.of());
        }

        public BigDecimal utilityTotal() {
            return utilities.stream().map(UtilityEstimate::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Dương: số tiền hoàn lại người thuê; âm: người thuê phải trả thêm. */
        public BigDecimal balance() {
            return deposit.subtract(deduction).subtract(depositOffset).subtract(outstanding).subtract(utilityTotal());
        }

        public BigDecimal usableDeposit() {
            return deposit.subtract(deduction).subtract(depositOffset).max(BigDecimal.ZERO);
        }
    }
}
