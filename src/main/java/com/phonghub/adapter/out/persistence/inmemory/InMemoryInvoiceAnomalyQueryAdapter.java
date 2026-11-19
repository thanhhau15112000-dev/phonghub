package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.InvoiceAnomalyQueryPort;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Hồ sơ in-memory (demo/test giao diện) không lưu chỉ số công tơ theo kỳ theo dạng truy vấn được, nên trả rỗng.
 * Quy tắc được kiểm thử bằng dữ liệu giả ở lớp service và bằng PostgreSQL thật ở test tích hợp.
 */
public class InMemoryInvoiceAnomalyQueryAdapter implements InvoiceAnomalyQueryPort {

    @Override
    public List<UtilityReading> utilityReadings(Collection<UUID> propertyIds, YearMonth from, YearMonth to) {
        return List.of();
    }

    @Override
    public List<DuplicateRentInvoices> duplicateRentInvoices(Collection<UUID> propertyIds, YearMonth period) {
        return List.of();
    }

    @Override
    public List<MissingReading> missingReadings(Collection<UUID> propertyIds, YearMonth period) {
        return List.of();
    }
}
