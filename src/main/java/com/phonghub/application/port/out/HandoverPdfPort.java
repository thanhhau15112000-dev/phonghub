package com.phonghub.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Dựng biên bản bàn giao thành PDF. Tầng ứng dụng chỉ cung cấp dữ liệu, không phụ thuộc thư viện PDF. */
public interface HandoverPdfPort {

    byte[] render(Report report);

    record Report(
        String title,
        String propertyName,
        String roomNumber,
        String tenantName,
        String contractId,
        String status,
        boolean checkOut,
        Instant sentAt,
        Instant tenantConfirmedAt,
        Instant approvedAt,
        String approvedBy,
        Instant generatedAt,
        BigDecimal electricMeter,
        BigDecimal waterMeter,
        String roomCondition,
        String keysProvided,
        List<Row> rows,
        BigDecimal finalDeduction,
        String contractTermsHash,
        String houseRulesHash,
        List<Photo> photos
    ) {}

    record Row(String assetName, int quantity, String checkInStatus, String checkInNote,
               String checkOutStatus, String checkOutNote, BigDecimal deduction, boolean changed) {}

    record Photo(String caption, byte[] content) {}
}
