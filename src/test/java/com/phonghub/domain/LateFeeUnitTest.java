package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
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

/** #80: chính sách phạt trả trễ và dòng phạt idempotent trên hóa đơn. */
class LateFeeUnitTest {

    private final UUID propertyId = UUID.randomUUID();

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private LateFeePolicy policy(boolean enabled, FeeType type, String value, int grace, String cap) {
        return new LateFeePolicy(propertyId, enabled, type, money(value), grace, cap == null ? null : money(cap),
            Instant.now(), null);
    }

    private Invoice rentInvoice() {
        Contract contract = new Contract(
            UUID.randomUUID(), propertyId, UUID.randomUUID(), UUID.randomUUID(),
            money("3000000"), money("3500000"), LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now());
        return Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 3), "PHLATE0001");
    }

    @Test
    @DisplayName("phạt theo phần trăm tính trên tiền hóa đơn và làm tròn đến đồng")
    void percentFee() {
        LateFeePolicy p = policy(true, FeeType.PERCENT, "2", 0, null);
        assertEquals(0, money("70000").compareTo(p.feeFor(money("3500000"), 1)));
        assertEquals(0, money("67").compareTo(policy(true, FeeType.PERCENT, "2", 0, null).feeFor(money("3333"), 1)));
    }

    @Test
    @DisplayName("phạt cố định và trần tối đa")
    void fixedFeeAndCap() {
        assertEquals(0, money("100000").compareTo(policy(true, FeeType.FIXED, "100000", 0, null).feeFor(money("3500000"), 5)));
        assertEquals(0, money("50000").compareTo(policy(true, FeeType.FIXED, "100000", 0, "50000").feeFor(money("3500000"), 5)));
        assertEquals(0, money("150000").compareTo(policy(true, FeeType.PERCENT, "10", 0, "150000").feeFor(money("3500000"), 5)));
        assertEquals(0, money("350000").compareTo(policy(true, FeeType.PERCENT, "10", 0, "999999").feeFor(money("3500000"), 5)));
    }

    @Test
    @DisplayName("không phạt khi tắt, chưa quá ngày ân hạn hoặc không có gốc")
    void noFeeCases() {
        assertEquals(0, policy(false, FeeType.PERCENT, "2", 0, null).feeFor(money("3500000"), 30).signum());
        LateFeePolicy grace = policy(true, FeeType.PERCENT, "2", 3, null);
        assertEquals(0, grace.feeFor(money("3500000"), 3).signum(), "đúng ngày ân hạn cuối vẫn chưa phạt");
        assertEquals(0, money("70000").compareTo(grace.feeFor(money("3500000"), 4)));
        assertEquals(0, grace.feeFor(BigDecimal.ZERO, 10).signum());
    }

    @Test
    @DisplayName("cấu hình sai bị từ chối")
    void validation() {
        assertThrows(DomainException.class, () -> policy(true, FeeType.PERCENT, "101", 0, null));
        assertThrows(DomainException.class, () -> policy(true, FeeType.FIXED, "-1", 0, null));
        assertThrows(DomainException.class, () -> policy(true, FeeType.FIXED, "1000", -1, null));
        assertThrows(DomainException.class, () -> policy(true, FeeType.FIXED, "1000", 366, null));
        assertThrows(DomainException.class, () -> policy(true, FeeType.FIXED, "1000", 0, "-5"));
        assertThrows(DomainException.class, () -> policy(true, FeeType.PERCENT, "0", 0, null), "bật mà mức phạt 0");
        assertEquals(0, policy(false, FeeType.PERCENT, "0", 0, null).feeValue().signum(), "tắt thì cho phép mức 0");
    }

    @Test
    @DisplayName("applyLateFee thêm một dòng phạt riêng và cộng vào tổng tiền")
    void feeIsAddedAsSeparateLine() {
        Invoice invoice = rentInvoice();

        assertTrue(invoice.applyLateFee(money("70000")));

        assertEquals(0, money("3570000").compareTo(invoice.getTotalAmount()));
        assertEquals(1, invoice.getItems().stream().filter(InvoiceItem::isLateFee).count());
        assertEquals(0, money("3500000").compareTo(invoice.principalAmount()));
        assertEquals(0, invoice.utilityAmount().signum(), "phạt không bị tính vào tiền điện nước");
    }

    @Test
    @DisplayName("áp dụng lặp lại cùng số tiền không cộng dồn")
    void idempotent() {
        Invoice invoice = rentInvoice();
        assertTrue(invoice.applyLateFee(money("70000")));

        assertFalse(invoice.applyLateFee(money("70000")));
        assertFalse(invoice.applyLateFee(money("70000")));

        assertEquals(0, money("3570000").compareTo(invoice.getTotalAmount()));
        assertEquals(1, invoice.getItems().stream().filter(InvoiceItem::isLateFee).count());
    }

    @Test
    @DisplayName("số phạt khác thì thay thế dòng cũ, không cộng thêm")
    void differentFeeReplaces() {
        Invoice invoice = rentInvoice();
        invoice.applyLateFee(money("70000"));

        assertTrue(invoice.applyLateFee(money("105000")));

        assertEquals(0, money("3605000").compareTo(invoice.getTotalAmount()));
        assertEquals(1, invoice.getItems().stream().filter(InvoiceItem::isLateFee).count());
        assertEquals(0, money("3500000").compareTo(invoice.principalAmount()));
    }

    @Test
    @DisplayName("hóa đơn đã thanh toán đủ hoặc phạt bằng 0 thì không đổi")
    void notAppliedWhenNotPayable() {
        Invoice paid = rentInvoice();
        paid.applyPayment(money("3500000"), Instant.now());
        assertEquals(InvoiceStatus.PAID, paid.getStatus());
        assertFalse(paid.applyLateFee(money("70000")));
        assertEquals(0, money("3500000").compareTo(paid.getTotalAmount()));

        Invoice invoice = rentInvoice();
        assertFalse(invoice.applyLateFee(BigDecimal.ZERO));
        assertFalse(invoice.applyLateFee(null));
        assertTrue(invoice.lateFeeItem().isEmpty());
    }

    @Test
    @DisplayName("hóa đơn thanh toán một phần vẫn bị phạt và giữ số đã trả")
    void partiallyPaidInvoiceStillCharged() {
        Invoice invoice = rentInvoice();
        invoice.applyPayment(money("1000000"), Instant.now());

        assertTrue(invoice.applyLateFee(money("70000")));

        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        assertEquals(0, money("1000000").compareTo(invoice.getPaidAmount()));
        assertEquals(0, money("2570000").compareTo(invoice.remainingAmount()));
    }
}
