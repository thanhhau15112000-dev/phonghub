package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.domain.model.MeterType;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * #67: đọc ảnh chỉ số ghi audit nên không được chạy trong transaction chỉ đọc. Trước đây {@code readOnly = true} làm
 * Postgres từ chối INSERT ("cannot execute INSERT in a read-only transaction") và endpoint OCR luôn trả 500.
 */
@Testcontainers(disabledWithoutDocker = true)
class MeterOcrTransactionPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static DataSource dataSource;

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionalUtilityUseCase utilityUseCase() {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            // Giả lập UtilityService.readMeterPhoto: ghi một dòng audit rồi trả gợi ý.
            UtilityUseCase delegate = (UtilityUseCase) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {UtilityUseCase.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("readMeterPhoto")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                        "không giữ transaction trong lúc chạy OCR");
                    jdbc.update("INSERT INTO ocr_audit (action) VALUES ('METER_OCR_REQUESTED')");
                    return new UtilityUseCase.OcrSuggestion(new BigDecimal("42445"), new BigDecimal("0.961"), false,
                        "tesseract");
                });
            return new TransactionalUtilityUseCase(delegate);
        }
    }

    @Test
    @DisplayName("readMeterPhoto ghi audit được trên Postgres và không chạy trong transaction")
    void readMeterPhotoCanWriteAudit() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        new JdbcTemplate(ds).execute("CREATE TABLE ocr_audit (action TEXT NOT NULL)");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            UtilityUseCase useCase = context.getBean(UtilityUseCase.class);
            UtilityUseCase.OcrSuggestion suggestion = useCase.readMeterPhoto(
                java.util.UUID.randomUUID(), MeterType.ELECTRIC, "image/png", new byte[] {1});
            assertEquals(new BigDecimal("42445"), suggestion.value());
        }
        assertEquals(1, new JdbcTemplate(ds).queryForObject("SELECT count(*) FROM ocr_audit", Integer.class));
    }
}
