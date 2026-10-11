package com.phonghub.application.port.in;

import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterSubmission;
import com.phonghub.domain.model.MeterType;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Người thuê chụp ảnh đồng hồ và khai số; server đối chiếu số khai với OCR (nếu có engine) rồi owner/staff duyệt
 * thì mới ghi thành chỉ số chính thức.
 */
public interface MeterSubmissionUseCase {

    /** Chỉ người thuê đang ở phòng. Mỗi đồng hồ chỉ có một lượt gửi đang chờ duyệt. */
    MeterSubmission submit(UUID roomId, MeterType meterType, BigDecimal declaredValue, String contentType, byte[] content);

    /** Người thuê của phòng hoặc người quản lý nhà trọ. */
    List<MeterSubmission> listForRoom(UUID roomId);

    /** Người quản lý nhà trọ; {@code status} null là tất cả. */
    List<MeterSubmission> listForProperty(UUID propertyId, MeterSubmission.Status status);

    /**
     * Duyệt: ghi chỉ số chính thức. Lệch với OCR (MISMATCH) thì phải xác nhận rõ hoặc nhập số chốt. {@code finalValue}
     * null nghĩa là lấy số khai; {@code baselineValue} chỉ cần khi phòng chưa có chỉ số kỳ trước.
     */
    MeterReading accept(UUID submissionId, AcceptCommand command);

    MeterSubmission reject(UUID submissionId, String reason);

    URI photoSignedUrl(UUID submissionId);

    record AcceptCommand(BigDecimal finalValue, BigDecimal baselineValue, boolean acknowledgeMismatch) {}
}
