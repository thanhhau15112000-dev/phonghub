package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.ExportParams;
import com.phonghub.application.port.in.ExportUseCase;
import com.phonghub.application.port.in.ExportUseCase.ExportFile;
import com.phonghub.application.port.in.ExportUseCase.Preview;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tải tệp Excel/PDF hóa đơn, công nợ, hợp đồng (#85, #227). Phân quyền nằm ở {@link ExportUseCase}. */
@RestController
public class ExportApiController {

    private final ExportUseCase exportUseCase;

    public ExportApiController(ExportUseCase exportUseCase) {
        this.exportUseCase = exportUseCase;
    }

    /**
     * {@code dataset}: invoices | debts | contracts; {@code format}: xlsx (mặc định) | pdf. Xuất mọi dòng khớp bộ lọc
     * (nhà trọ, phòng, người thuê, trạng thái, loại, hạn thanh toán, ngày hợp đồng…).
     */
    @GetMapping("/api/exports/{dataset}")
    public ResponseEntity<byte[]> export(@PathVariable String dataset, ExportParams params) {
        return download(exportUseCase.export(params.toRequest(dataset), params.toFilter(), null));
    }

    /** Xem trước một trang kết quả lọc; mỗi dòng có {@code id} để chọn xuất. */
    @GetMapping("/api/exports/{dataset}/preview")
    public Preview preview(
        @PathVariable String dataset,
        ExportParams params,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return exportUseCase.preview(params.toRequest(dataset), params.toFilter(), page, size);
    }

    /** Xuất đúng các dòng đã chọn; danh sách id có thể dài nên gửi trong thân yêu cầu. */
    @PostMapping("/api/exports/{dataset}")
    public ResponseEntity<byte[]> exportSelected(@PathVariable String dataset, @RequestBody Selection body) {
        ExportParams params = body.params() == null ? emptyParams() : body.params();
        return download(exportUseCase.export(params.toRequest(dataset), params.toFilter(), body.ids()));
    }

    public record Selection(ExportParams params, Set<String> ids) {}

    private static ExportParams emptyParams() {
        return new ExportParams(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null);
    }

    private static ResponseEntity<byte[]> download(ExportFile file) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build()
                .toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(file.content());
    }
}
