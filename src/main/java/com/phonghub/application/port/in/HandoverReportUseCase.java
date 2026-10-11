package com.phonghub.application.port.in;

import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Đối chiếu tài sản giữa check-in và check-out, và xuất biên bản đã duyệt thành PDF. */
public interface HandoverReportUseCase {

    Comparison compare(UUID contractId);

    /** Chỉ biên bản đã duyệt (APPROVED) mới xuất được; có dấu thời gian gửi, xác nhận, duyệt. */
    PdfDocument exportPdf(UUID contractId, HandoverType type);

    record ItemRow(
        String assetName,
        int quantity,
        AssetStatus checkInStatus,
        String checkInNote,
        AssetStatus checkOutStatus,
        String checkOutNote,
        BigDecimal deduction,
        boolean changed
    ) {}

    /** {@code checkOut} có thể null khi chưa tạo; ảnh phòng của hai biên bản để xem cạnh nhau. */
    record Comparison(
        Handover checkIn,
        Handover checkOut,
        List<ItemRow> rows,
        List<HandoverPhoto> checkInPhotos,
        List<HandoverPhoto> checkOutPhotos,
        BigDecimal electricConsumption,
        BigDecimal waterConsumption
    ) {}

    record PdfDocument(String filename, byte[] content) {}
}
