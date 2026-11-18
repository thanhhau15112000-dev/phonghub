package com.phonghub.application.port.out;

import com.phonghub.domain.model.MeterType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Truy vấn dữ liệu thô để phát hiện bất thường hóa đơn (#156). Mỗi phương thức là một truy vấn cho cả phạm vi nhà
 * trọ (không lặp theo phòng/hóa đơn). Danh sách {@code propertyIds} rỗng trả kết quả rỗng.
 */
public interface InvoiceAnomalyQueryPort {

    /** Chỉ số điện/nước ghi nhận trong [from, to] theo kỳ ghi chỉ số, của các phòng thuộc phạm vi. */
    List<UtilityReading> utilityReadings(Collection<UUID> propertyIds, YearMonth from, YearMonth to);

    /**
     * Phòng có từ hai hóa đơn tiền thuê chưa hủy trong cùng một kỳ. Cùng hợp đồng đã bị chặn bởi chỉ mục duy nhất
     * {@code uk_invoices_rent_period}, nên trường hợp còn lại là hai hợp đồng khác nhau cùng thu tiền một phòng.
     */
    List<DuplicateRentInvoices> duplicateRentInvoices(Collection<UUID> propertyIds, YearMonth period);

    /**
     * Phòng có hợp đồng đang hiệu lực trong kỳ, nhà trọ có đơn giá theo đơn vị cho loại công tơ đó (PER_KWH cho điện,
     * PER_M3 cho nước), nhưng chưa có chỉ số của kỳ.
     */
    List<MissingReading> missingReadings(Collection<UUID> propertyIds, YearMonth period);

    record UtilityReading(
        UUID propertyId, UUID roomId, String roomNumber, MeterType type, YearMonth period,
        BigDecimal previousValue, BigDecimal currentValue, BigDecimal consumption, boolean replaced
    ) {}

    record DuplicateRentInvoices(
        UUID propertyId, UUID roomId, String roomNumber, YearMonth period, List<UUID> invoiceIds
    ) {}

    record MissingReading(UUID propertyId, UUID roomId, String roomNumber, MeterType type) {}
}
