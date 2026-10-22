package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.LateFeePolicy;
import com.phonghub.domain.model.LateFeePolicy.FeeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Issue #159 + #94: Kiểm thử trường hợp biên cho Tính toán Tiền tệ, Ngày tháng và Phạt nợ trễ hạn.
 */
class BillingAndLateFeeEdgeCaseUnitTest {

    private final UUID propertyId = UUID.randomUUID();

    private Contract createContract(int paymentDay, LocalDate start, LocalDate end) {
        return new Contract(
            UUID.randomUUID(), propertyId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("5000000"), new BigDecimal("5000000"),
            start, end, paymentDay,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()
        );
    }

    // =========================================================================
    // 1. DATE CLAMPING (NĂM NHUẬN, THÁNG 2, THÁNG 30 NGÀY)
    // =========================================================================

    @ParameterizedTest(name = "Kỳ {0}-{1} với ngày thanh toán {2} -> dueDate: {3}")
    @CsvSource({
        // Năm nhuận 2024: Tháng 2 có 29 ngày
        "2024, 2, 31, 2024-02-29",
        "2024, 2, 30, 2024-02-29",
        "2024, 2, 29, 2024-02-29",
        "2024, 2, 28, 2024-02-28",
        // Năm thường 2026: Tháng 2 có 28 ngày
        "2026, 2, 31, 2026-02-28",
        "2026, 2, 30, 2026-02-28",
        "2026, 2, 29, 2026-02-28",
        // Các tháng có 30 ngày: Tháng 4, 6, 9, 11
        "2026, 4, 31, 2026-04-30",
        "2026, 6, 31, 2026-06-30",
        "2026, 9, 31, 2026-09-30",
        "2026, 11, 31, 2026-11-30",
        // Tháng có 31 ngày: Tháng 1, 3, 5, 7, 8, 10, 12
        "2026, 1, 31, 2026-01-31",
        "2026, 3, 31, 2026-03-31",
        "2026, 7, 31, 2026-07-31",
        "2026, 12, 31, 2026-12-31"
    })
    @DisplayName("Due Date: Ngày thanh toán được co giãn (clamp) chính xác theo độ dài tháng")
    void testDueDateClampingLeapYearAndShortMonths(int year, int month, int paymentDay, String expectedDueDateStr) {
        Contract contract = createContract(paymentDay, LocalDate.of(year - 1, 1, 1), LocalDate.of(year + 1, 12, 31));
        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.of(year, month), "PHEDGE001");

        assertEquals(LocalDate.parse(expectedDueDateStr), invoice.getDueDate());
    }

    // =========================================================================
    // 2. TÍNH TIỀN SỐ LỚN VÀ LÀM TRÒN VND
    // =========================================================================

    @Test
    @DisplayName("Hóa đơn với số tiền rất lớn (hàng tỷ đồng) và làm tròn không mất mát")
    void testVeryLargeMoneyAmounts() {
        BigDecimal largeRent = new BigDecimal("10000000000"); // 10 tỷ VNĐ
        Contract contract = new Contract(
            UUID.randomUUID(), propertyId, UUID.randomUUID(), UUID.randomUUID(),
            largeRent, largeRent,
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()
        );

        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 5), "PHBILLION1");
        assertEquals(0, largeRent.compareTo(invoice.getTotalAmount()));

        // Thanh toán một phần 4 tỷ
        BigDecimal partial = new BigDecimal("4000000000");
        invoice.applyPayment(partial, Instant.now());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        assertEquals(0, new BigDecimal("6000000000").compareTo(invoice.getTotalAmount().subtract(invoice.getPaidAmount())));

        // Thanh toán tiếp 6 tỷ
        BigDecimal remainder = new BigDecimal("6000000000");
        invoice.applyPayment(remainder, Instant.now());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getTotalAmount().subtract(invoice.getPaidAmount())));
    }

    // =========================================================================
    // 3. LATE FEE POLICY EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("Late Fee: Chính sách bị tắt (enabled=false) luôn tính phạt bằng 0")
    void testDisabledPolicyReturnsZeroFee() {
        LateFeePolicy disabledPolicy = new LateFeePolicy(
            propertyId, false, FeeType.PERCENT, new BigDecimal("5"), 0, null, Instant.now(), null
        );

        BigDecimal fee = disabledPolicy.feeFor(new BigDecimal("10000000"), 10);
        assertEquals(0, BigDecimal.ZERO.compareTo(fee));
    }

    @Test
    @DisplayName("Late Fee: Ân hạn (Grace period) - trong thời gian ân hạn không bị phạt")
    void testGracePeriodNoFee() {
        // Ân hạn 3 ngày
        LateFeePolicy policy = new LateFeePolicy(
            propertyId, true, FeeType.FIXED, new BigDecimal("50000"), 3, null, Instant.now(), null
        );

        // Quá hạn 1, 2, 3 ngày -> 0 đồng
        assertEquals(0, BigDecimal.ZERO.compareTo(policy.feeFor(new BigDecimal("3000000"), 1)));
        assertEquals(0, BigDecimal.ZERO.compareTo(policy.feeFor(new BigDecimal("3000000"), 2)));
        assertEquals(0, BigDecimal.ZERO.compareTo(policy.feeFor(new BigDecimal("3000000"), 3)));

        // Quá hạn 4 ngày -> phạt
        assertTrue(policy.feeFor(new BigDecimal("3000000"), 4).compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Late Fee: Chạm trần tối đa (Cap) khi tính phí theo phần trăm")
    void testLateFeeCap() {
        // Phạt 10% tiền phòng (3.000.000 * 10% = 300.000đ), nhưng trần tối đa (Cap) là 150.000đ
        LateFeePolicy policy = new LateFeePolicy(
            propertyId, true, FeeType.PERCENT, new BigDecimal("10"), 0, new BigDecimal("150000"), Instant.now(), null
        );

        // Số tiền phạt bị chặn ở mức 150.000đ thay vì 300.000đ
        assertEquals(0, new BigDecimal("150000").compareTo(policy.feeFor(new BigDecimal("3000000"), 5)));
        assertEquals(0, new BigDecimal("150000").compareTo(policy.feeFor(new BigDecimal("3000000"), 30)));
    }

    @Test
    @DisplayName("Late Fee: Tính phạt lặp lại nhiều lần (idempotent) không cộng dồn trùng lặp")
    void testApplyLateFeeIdempotency() {
        Contract contract = createContract(5, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 3), "PHLATE009");
        BigDecimal originalTotal = invoice.getTotalAmount();

        // Áp dụng lần 1: 150.000đ
        boolean appliedFirst = invoice.applyLateFee(new BigDecimal("150000"));
        assertTrue(appliedFirst);
        assertEquals(0, originalTotal.add(new BigDecimal("150000")).compareTo(invoice.getTotalAmount()));

        // Chạy lại lần 2 với cùng số tiền phạt 150.000đ: không đổi gì (idempotent)
        boolean appliedSecond = invoice.applyLateFee(new BigDecimal("150000"));
        assertFalse(appliedSecond, "Chạy lại cùng số tiền không làm thay đổi hóa đơn");
        assertEquals(0, originalTotal.add(new BigDecimal("150000")).compareTo(invoice.getTotalAmount()));

        // Tăng phạt lên 200.000đ khi trễ thêm ngày: thay thế dòng cũ chứ không cộng dồn thành 350.000đ
        boolean updatedFee = invoice.applyLateFee(new BigDecimal("200000"));
        assertTrue(updatedFee);
        assertEquals(0, originalTotal.add(new BigDecimal("200000")).compareTo(invoice.getTotalAmount()));
    }
}
