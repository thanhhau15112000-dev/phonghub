package com.phonghub.adapter.in.web;

import com.phonghub.application.port.in.ExportUseCase.Dataset;
import com.phonghub.application.port.in.ExportUseCase.ExportFilter;
import com.phonghub.application.port.in.ExportUseCase.ExportRequest;
import com.phonghub.application.port.in.ExportUseCase.Format;
import com.phonghub.application.port.in.ExportUseCase.InvoiceState;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.InvoiceType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/** Tham số xuất dữ liệu dùng chung cho API và giao diện (#227); tiêu chí nào để trống thì không lọc. */
public record ExportParams(
    String format,
    UUID propertyId,
    YearMonth from,
    YearMonth to,
    Set<UUID> propertyIds,
    String roomNumber,
    String tenantName,
    Set<InvoiceState> states,
    Set<InvoiceType> types,
    @DateTimeFormat(iso = ISO.DATE) LocalDate dueFrom,
    @DateTimeFormat(iso = ISO.DATE) LocalDate dueTo,
    Boolean onlyOutstanding,
    Set<ContractStatus> contractStatuses,
    @DateTimeFormat(iso = ISO.DATE) LocalDate startFrom,
    @DateTimeFormat(iso = ISO.DATE) LocalDate startTo,
    @DateTimeFormat(iso = ISO.DATE) LocalDate endFrom,
    @DateTimeFormat(iso = ISO.DATE) LocalDate endTo
) {

    public ExportRequest toRequest(String dataset) {
        return new ExportRequest(Dataset.parse(dataset), Format.parse(format == null || format.isBlank() ? "xlsx" : format),
            propertyId, from, to);
    }

    public ExportFilter toFilter() {
        return new ExportFilter(propertyIds, roomNumber, tenantName, states, types, dueFrom, dueTo,
            Boolean.TRUE.equals(onlyOutstanding), contractStatuses, startFrom, startTo, endFrom, endTo);
    }
}
