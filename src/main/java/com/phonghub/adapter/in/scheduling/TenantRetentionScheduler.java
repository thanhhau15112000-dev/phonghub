package com.phonghub.adapter.in.scheduling;

import com.phonghub.application.port.in.TenantPrivacyUseCase;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Xóa ảnh giấy tờ và số CCCD/GPLX của tenant đã check-out quá thời hạn lưu trữ (mặc định 90 ngày, cấu hình qua
 * {@code phonghub.privacy.retention-days}). Chạy mỗi giờ và lúc khởi động vì instance miễn phí có thể ngủ; chạy lặp lại an toàn.
 */
@Component
@ConditionalOnProperty(name = "phonghub.privacy.purge-enabled", havingValue = "true", matchIfMissing = true)
public class TenantRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(TenantRetentionScheduler.class);

    private final TenantPrivacyUseCase privacyUseCase;

    public TenantRetentionScheduler(TenantPrivacyUseCase privacyUseCase) {
        this.privacyUseCase = privacyUseCase;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 20 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void hourly() {
        run();
    }

    public int run() {
        try {
            int purged = privacyUseCase.purgeExpired(Instant.now());
            if (purged > 0) {
                log.info("Đã xóa dữ liệu giấy tờ quá hạn lưu trữ của {} người thuê", purged);
            }
            return purged;
        } catch (RuntimeException ex) {
            log.error("Job xóa dữ liệu giấy tờ quá hạn bị lỗi", ex);
            return 0;
        }
    }
}
