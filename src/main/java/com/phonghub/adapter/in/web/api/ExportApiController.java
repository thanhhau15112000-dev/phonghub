package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.ExportUseCase;
import com.phonghub.application.port.in.ExportUseCase.Dataset;
import com.phonghub.application.port.in.ExportUseCase.ExportFile;
import com.phonghub.application.port.in.ExportUseCase.ExportRequest;
import com.phonghub.application.port.in.ExportUseCase.Format;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tải tệp Excel/PDF hóa đơn, công nợ, hợp đồng (#85). Phân quyền nằm ở {@link ExportUseCase}. */
@RestController
public class ExportApiController {

    private final ExportUseCase exportUseCase;

    public ExportApiController(ExportUseCase exportUseCase) {
        this.exportUseCase = exportUseCase;
    }

    /** {@code dataset}: invoices | debts | contracts; {@code format}: xlsx (mặc định) | pdf. */
    @GetMapping("/api/exports/{dataset}")
    public ResponseEntity<byte[]> export(
        @PathVariable String dataset,
        @RequestParam(defaultValue = "xlsx") String format,
        @RequestParam(required = false) UUID propertyId,
        @RequestParam(required = false) YearMonth from,
        @RequestParam(required = false) YearMonth to
    ) {
        return download(exportUseCase.export(new ExportRequest(Dataset.parse(dataset), Format.parse(format), propertyId,
            from, to)));
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
