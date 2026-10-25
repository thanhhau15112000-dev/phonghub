package com.phonghub.config;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.application.port.in.TenantDocumentUseCase;
import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.service.TenantService;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomAssetEventRepositoryPort;
import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.application.port.out.MeterOcrPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.PropertyHouseRulesRepositoryPort;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.service.AuthService;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.application.service.MaintenanceService;
import com.phonghub.application.service.PropertyService;
import com.phonghub.application.service.HandoverService;
import com.phonghub.application.service.RoomAssetService;
import com.phonghub.application.service.TenantDocumentService;
import com.phonghub.application.service.UtilityService;
import com.phonghub.application.service.RoomService;
import com.phonghub.application.service.UserService;
import com.phonghub.application.port.in.InvoiceDueReminderUseCase;
import com.phonghub.application.port.in.NotificationInboxUseCase;
import com.phonghub.application.port.out.InvoiceReminderPort;
import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.service.InvoiceDueReminderService;
import com.phonghub.application.service.NotificationDispatcher;
import com.phonghub.application.service.NotificationInboxService;
import com.phonghub.application.service.NotificationService;
import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.application.port.out.SepayGatewayOrderRepositoryPort;
import com.phonghub.application.service.SepayPaymentService;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.in.FileStorageUseCase;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.in.MonthlyInvoiceUseCase;
import com.phonghub.application.service.InvoiceService;
import com.phonghub.application.service.PrivateFileStorageService;
import com.phonghub.application.service.MonthlyInvoiceService;
import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.application.service.StatisticsService;
import com.phonghub.adapter.in.web.FixedWindowRateLimiter;
import com.phonghub.adapter.in.web.RateLimitFilter;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SepayProperties.class, TenantDataProtectionProperties.class, RateLimitProperties.class})
public class PhongHubConfiguration {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties props) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(
            new RateLimitFilter(props, new FixedWindowRateLimiter(Clock.systemUTC())));
        bean.setOrder(-150);
        return bean;
    }

    @Bean
    public AuthorizationService authorizationService(
        StaffPropertyAssignmentPort assignmentRepo,
        PropertyRepositoryPort propertyRepo,
        TenantRepositoryPort tenantRepo,
        ContractRepositoryPort contractRepo
    ) {
        return new AuthorizationService(assignmentRepo, propertyRepo, tenantRepo, contractRepo);
    }

    /** Cache danh sách nhà trọ/phòng và dashboard owner (#88); tắt trong profile test (test bật riêng khi cần). */
    @Bean
    public PhongHubCaches phongHubCaches(
        @org.springframework.beans.factory.annotation.Value("${phonghub.cache.enabled:true}") boolean enabled,
        @org.springframework.beans.factory.annotation.Value("${phonghub.cache.ttl-seconds:300}") long ttlSeconds,
        @org.springframework.beans.factory.annotation.Value("${phonghub.cache.max-size:1000}") long maxSize
    ) {
        return new PhongHubCaches(enabled, java.time.Duration.ofSeconds(ttlSeconds), maxSize);
    }

    @Bean
    public CacheEvictingTransactionListener cacheEvictingTransactionListener(PhongHubCaches caches) {
        return new CacheEvictingTransactionListener(caches);
    }

    @Bean
    public PropertyUseCase propertyUseCase(
        PropertyRepositoryPort propertyRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        PhongHubCaches caches,
        ContractRepositoryPort contractRepo,
        InvoiceRepositoryPort invoiceRepo,
        RoomRepositoryPort roomRepo
    ) {
        return new CachingPropertyUseCase(
            new PropertyService(propertyRepo, currentUserPort, authorizationService, auditPort, contractRepo, invoiceRepo, roomRepo),
            caches, currentUserPort);
    }

    @Bean
    public RoomUseCase roomUseCase(
        RoomRepositoryPort roomRepo,
        PropertyRepositoryPort propertyRepo,
        MaintenanceTicketRepositoryPort ticketRepo,
        ContractRepositoryPort contractRepo,
        TenantRepositoryPort tenantRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        PhongHubCaches caches,
        InvoiceRepositoryPort invoiceRepo
    ) {
        return new CachingRoomUseCase(new RoomService(
            roomRepo, propertyRepo, ticketRepo, contractRepo, tenantRepo, currentUserPort, authorizationService,
            auditPort, invoiceRepo
        ), caches, currentUserPort);
    }

    @Bean
    public RoomAssetUseCase roomAssetUseCase(
        RoomAssetRepositoryPort assetRepo,
        RoomAssetEventRepositoryPort eventRepo,
        RoomRepositoryPort roomRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        MaintenanceTicketRepositoryPort ticketRepo,
        FileStoragePort fileStoragePort
    ) {
        RoomAssetService service = new RoomAssetService(
            assetRepo, eventRepo, roomRepo, currentUserPort, authorizationService, auditPort,
            ticketRepo, fileStoragePort
        );
        return new TransactionalRoomAssetUseCase(service);
    }

    /**
     * OCR chỉ số đồng hồ (#67): {@code phonghub.ocr.engine=tesseract} dùng lệnh tesseract (image Docker đã cài). Chọn
     * tesseract mà máy không có lệnh thì ghi cảnh báo và quay về nhập tay, không làm app lỗi khi khởi động.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(MeterOcrPort.class)
    public MeterOcrPort meterOcrPort(
        @org.springframework.beans.factory.annotation.Value("${phonghub.ocr.engine:disabled}") String engine,
        @org.springframework.beans.factory.annotation.Value("${phonghub.ocr.tesseract.command:tesseract}") String command,
        @org.springframework.beans.factory.annotation.Value("${phonghub.ocr.tesseract.timeout-seconds:15}") long timeoutSeconds,
        @org.springframework.beans.factory.annotation.Value("${phonghub.ocr.tesseract.max-concurrent:1}") int maxConcurrent
    ) {
        if (!com.phonghub.adapter.out.ocr.TesseractMeterOcrAdapter.ENGINE.equalsIgnoreCase(engine.trim())) {
            return new com.phonghub.adapter.out.ocr.DisabledMeterOcrAdapter();
        }
        java.time.Duration timeout = java.time.Duration.ofSeconds(timeoutSeconds);
        if (!com.phonghub.adapter.out.ocr.TesseractMeterOcrAdapter.isAvailable(command, timeout)) {
            org.slf4j.LoggerFactory.getLogger(PhongHubConfiguration.class).warn(
                "phonghub.ocr.engine=tesseract nhưng không chạy được lệnh '{}'; OCR chỉ số tắt, người dùng nhập tay", command);
            return new com.phonghub.adapter.out.ocr.DisabledMeterOcrAdapter();
        }
        return new com.phonghub.adapter.out.ocr.TesseractMeterOcrAdapter(command, timeout, maxConcurrent);
    }

    @Bean
    public UtilityUseCase utilityUseCase(
        MeterReadingRepositoryPort meterRepo,
        ServiceRateRepositoryPort rateRepo,
        RoomRepositoryPort roomRepo,
        ContractRepositoryPort contractRepo,
        HandoverRepositoryPort handoverRepo,
        FileStoragePort fileStoragePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        MeterOcrPort meterOcrPort,
        @org.springframework.beans.factory.annotation.Value("${phonghub.ocr.min-confidence:0.85}")
        java.math.BigDecimal ocrMinConfidence,
        StorageProperties storageProperties
    ) {
        UtilityService service = new UtilityService(meterRepo, rateRepo, roomRepo, contractRepo, handoverRepo,
            fileStoragePort, currentUserPort, authorizationService, auditPort, meterOcrPort, ocrMinConfidence,
            storageProperties.getMaxFileSizeBytes());
        return new TransactionalUtilityUseCase(service);
    }

    @Bean
    public HandoverUseCase handoverUseCase(
        HandoverRepositoryPort handoverRepo,
        HandoverPhotoRepositoryPort photoRepo,
        HandoverItemRepositoryPort itemRepo,
        PropertyHouseRulesRepositoryPort rulesRepo,
        ContractRepositoryPort contractRepo,
        RoomAssetRepositoryPort assetRepo,
        TenantRepositoryPort tenantRepo,
        TenantDocumentRepositoryPort documentRepo,
        ContractUseCase contractUseCase,
        FileStoragePort fileStoragePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        StorageProperties storageProperties,
        NotificationDispatcher notificationDispatcher
    ) {
        HandoverService service = new HandoverService(
            handoverRepo, photoRepo, itemRepo, rulesRepo, contractRepo, assetRepo, tenantRepo, documentRepo,
            contractUseCase, fileStoragePort, currentUserPort, authorizationService, auditPort, storageProperties.getMaxFileSizeBytes(),
            notificationDispatcher
        );
        return new TransactionalHandoverUseCase(service);
    }

    @Bean
    public com.phonghub.application.port.out.HandoverPdfPort handoverPdfPort() {
        return new com.phonghub.adapter.out.pdf.OpenPdfHandoverRenderer();
    }

    @Bean
    public com.phonghub.application.port.in.HandoverReportUseCase handoverReportUseCase(
        HandoverRepositoryPort handoverRepo,
        HandoverItemRepositoryPort itemRepo,
        HandoverPhotoRepositoryPort photoRepo,
        ContractRepositoryPort contractRepo,
        RoomRepositoryPort roomRepo,
        PropertyRepositoryPort propertyRepo,
        TenantRepositoryPort tenantRepo,
        UserRepositoryPort userRepo,
        FileStoragePort fileStoragePort,
        com.phonghub.application.port.out.HandoverPdfPort pdfPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        return new TransactionalHandoverReportUseCase(new com.phonghub.application.service.HandoverReportService(
            handoverRepo, itemRepo, photoRepo, contractRepo, roomRepo, propertyRepo, tenantRepo, userRepo,
            fileStoragePort, pdfPort, currentUserPort, authorizationService, auditPort));
    }

    @Bean
    public com.phonghub.application.port.in.MaintenanceAttachmentUseCase maintenanceAttachmentUseCase(
        com.phonghub.application.port.out.MaintenanceAttachmentRepositoryPort attachmentRepo,
        MaintenanceTicketRepositoryPort ticketRepo,
        TenantRepositoryPort tenantRepo,
        FileStoragePort fileStoragePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        StorageProperties storageProperties,
        @org.springframework.beans.factory.annotation.Value("${phonghub.maintenance.max-images:5}") int maxImages,
        @org.springframework.beans.factory.annotation.Value("${phonghub.maintenance.max-video-bytes:10485760}") long maxVideoBytes
    ) {
        return new TransactionalMaintenanceAttachmentUseCase(
            new com.phonghub.application.service.MaintenanceAttachmentService(attachmentRepo, ticketRepo, tenantRepo,
                fileStoragePort, currentUserPort, authorizationService, auditPort, maxImages,
                storageProperties.getMaxFileSizeBytes(), Math.min(maxVideoBytes, storageProperties.getMaxFileSizeBytes())));
    }

    @Bean
    public com.phonghub.application.port.in.MeterSubmissionUseCase meterSubmissionUseCase(
        com.phonghub.application.port.out.MeterSubmissionRepositoryPort submissionRepo,
        RoomRepositoryPort roomRepo,
        PropertyRepositoryPort propertyRepo,
        UserRepositoryPort userRepo,
        NotificationRepositoryPort notificationRepo,
        UtilityUseCase utilityUseCase,
        MeterOcrPort meterOcrPort,
        FileStoragePort fileStoragePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        StorageProperties storageProperties,
        @org.springframework.beans.factory.annotation.Value("${phonghub.ocr.min-confidence:0.85}")
        java.math.BigDecimal minConfidence,
        @org.springframework.beans.factory.annotation.Value("${phonghub.ocr.match-tolerance:0.5}")
        java.math.BigDecimal tolerance
    ) {
        return new TransactionalMeterSubmissionUseCase(new com.phonghub.application.service.MeterSubmissionService(
            submissionRepo, roomRepo, propertyRepo, userRepo, notificationRepo, utilityUseCase, meterOcrPort,
            fileStoragePort, currentUserPort, authorizationService, auditPort, minConfidence, tolerance,
            storageProperties.getMaxFileSizeBytes()));
    }

    @Bean
    public com.phonghub.application.service.TenantLockPolicy tenantLockPolicy(
        ContractRepositoryPort contractRepo,
        @org.springframework.beans.factory.annotation.Value("${phonghub.privacy.retention-days:90}") long retentionDays
    ) {
        return new com.phonghub.application.service.TenantLockPolicy(contractRepo,
            java.time.Duration.ofDays(Math.max(retentionDays, 1)));
    }

    @Bean
    public com.phonghub.application.port.in.TenantPrivacyUseCase tenantPrivacyUseCase(
        com.phonghub.application.port.out.TenantPrivacyRepositoryPort privacyRepo,
        TenantRepositoryPort tenantRepo,
        TenantDocumentRepositoryPort documentRepo,
        ContractRepositoryPort contractRepo,
        FileStoragePort fileStoragePort,
        com.phonghub.application.service.TenantLockPolicy tenantLockPolicy,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        IdentityProviderPort identityProvider,
        PropertyRepositoryPort propertyRepo,
        UserRepositoryPort userRepo,
        NotificationRepositoryPort notificationRepo,
        @org.springframework.beans.factory.annotation.Value("${phonghub.privacy.purge-mode:APPROVAL}") String purgeMode
    ) {
        return new TransactionalTenantPrivacyUseCase(new com.phonghub.application.service.TenantPrivacyService(
            privacyRepo, tenantRepo, documentRepo, contractRepo, fileStoragePort, tenantLockPolicy, currentUserPort,
            authorizationService, auditPort, java.time.Clock.systemUTC(),
            com.phonghub.application.port.in.TenantPrivacyUseCase.PurgeMode.valueOf(purgeMode.trim().toUpperCase()),
            identityProvider, propertyRepo, userRepo, notificationRepo));
    }

    @Bean
    public TenantDocumentUseCase tenantDocumentUseCase(
        TenantDocumentRepositoryPort documentRepo,
        TenantRepositoryPort tenantRepo,
        TenantDataProtectionPort protector,
        FileStoragePort fileStoragePort,
        IdentityProviderPort identityProvider,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        StorageProperties storageProperties,
        com.phonghub.application.service.TenantLockPolicy tenantLockPolicy
    ) {
        TenantDocumentService service = new TenantDocumentService(
            documentRepo, tenantRepo, protector, fileStoragePort, identityProvider, currentUserPort,
            authorizationService, auditPort, storageProperties.getMaxFileSizeBytes(), java.time.Clock.systemUTC(),
            tenantLockPolicy
        );
        return new TransactionalTenantDocumentUseCase(service);
    }

    @Bean
    public ContractUseCase contractUseCase(
        ContractRepositoryPort contractRepo,
        RoomRepositoryPort roomRepo,
        PropertyRepositoryPort propertyRepo,
        TenantRepositoryPort tenantRepo,
        UserRepositoryPort userRepo,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        ContractService service = new ContractService(
            contractRepo, roomRepo, propertyRepo, tenantRepo, userRepo, identityProviderPort, currentUserPort, authorizationService, auditPort
        );
        return new TransactionalContractUseCase(service);
    }

    @Bean
    public com.phonghub.application.service.MaintenanceSlaPolicy maintenanceSlaPolicy(
        @org.springframework.beans.factory.annotation.Value("${phonghub.maintenance.sla-hours.urgent:4}") long urgent,
        @org.springframework.beans.factory.annotation.Value("${phonghub.maintenance.sla-hours.high:24}") long high,
        @org.springframework.beans.factory.annotation.Value("${phonghub.maintenance.sla-hours.medium:72}") long medium,
        @org.springframework.beans.factory.annotation.Value("${phonghub.maintenance.sla-hours.low:168}") long low
    ) {
        return new com.phonghub.application.service.MaintenanceSlaPolicy(urgent, high, medium, low);
    }

    @Bean
    public com.phonghub.application.service.MaintenanceSlaService maintenanceSlaService(
        MaintenanceTicketRepositoryPort ticketRepo,
        PropertyRepositoryPort propertyRepo,
        UserRepositoryPort userRepo,
        NotificationRepositoryPort notificationRepo,
        AuditPort auditPort
    ) {
        return new com.phonghub.application.service.MaintenanceSlaService(
            ticketRepo, propertyRepo, userRepo, notificationRepo, auditPort);
    }

    @Bean
    public MaintenanceUseCase maintenanceUseCase(
        MaintenanceTicketRepositoryPort ticketRepo,
        RoomRepositoryPort roomRepo,
        TenantRepositoryPort tenantRepo,
        ContractRepositoryPort contractRepo,
        InvoiceRepositoryPort invoiceRepo,
        AuditPort auditPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        UserRepositoryPort userRepo,
        StaffPropertyAssignmentPort assignmentRepo,
        NotificationRepositoryPort notificationRepo,
        com.phonghub.application.service.MaintenanceSlaPolicy slaPolicy
    ) {
        MaintenanceService service = new MaintenanceService(
            ticketRepo, roomRepo, tenantRepo, contractRepo, invoiceRepo, auditPort,
            currentUserPort, authorizationService, userRepo, assignmentRepo, notificationRepo, slaPolicy
        );
        return new TransactionalMaintenanceUseCase(service);
    }

    @Bean
    public com.phonghub.application.port.in.AuditLogUseCase auditLogUseCase(
        com.phonghub.application.port.out.AuditQueryPort auditQueryPort,
        PropertyRepositoryPort propertyRepo,
        CurrentUserPort currentUserPort
    ) {
        return new com.phonghub.application.service.AuditLogService(auditQueryPort, propertyRepo, currentUserPort);
    }

    @Bean
    public com.phonghub.application.port.in.PropertyStaffUseCase propertyStaffUseCase(
        StaffPropertyAssignmentPort assignmentRepo,
        UserRepositoryPort userRepo,
        MaintenanceTicketRepositoryPort ticketRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        return new TransactionalPropertyStaffUseCase(new com.phonghub.application.service.PropertyStaffService(
            assignmentRepo, userRepo, ticketRepo, currentUserPort, authorizationService, auditPort));
    }

    @Bean
    public UserUseCase userUseCase(
        UserRepositoryPort userRepo,
        StaffPropertyAssignmentPort assignmentRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        PropertyRepositoryPort propertyRepo,
        TenantRepositoryPort tenantRepo,
        AuditPort auditPort
    ) {
        return new UserService(
            userRepo, assignmentRepo, currentUserPort, authorizationService, propertyRepo, tenantRepo, auditPort
        );
    }

    @Bean
    public AuthUseCase authUseCase(
        UserRepositoryPort userRepo,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        TenantRepositoryPort tenantRepo
    ) {
        return new TransactionalAuthUseCase(
            new AuthService(userRepo, identityProviderPort, currentUserPort, authorizationService, auditPort, tenantRepo));
    }

    @Bean
    public TenantUseCase tenantUseCase(
        TenantRepositoryPort tenantRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        ContractRepositoryPort contractRepo,
        com.phonghub.application.service.TenantLockPolicy tenantLockPolicy,
        UserRepositoryPort userRepo,
        IdentityProviderPort identityProviderPort
    ) {
        TenantService service = new TenantService(tenantRepo, currentUserPort, authorizationService, auditPort,
            contractRepo, tenantLockPolicy, userRepo, identityProviderPort);
        return new TransactionalTenantUseCase(service);
    }

    @Bean
    public NotificationDispatcher notificationDispatcher(
        NotificationRepositoryPort notificationRepository,
        PropertyRepositoryPort propertyRepository,
        StaffPropertyAssignmentPort assignmentRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository
    ) {
        return new NotificationDispatcher(notificationRepository, propertyRepository, assignmentRepository,
            tenantRepository, userRepository);
    }

    @Bean
    public NotificationInboxUseCase notificationInboxUseCase(
        NotificationRepositoryPort notificationRepository,
        CurrentUserPort currentUserPort
    ) {
        return new NotificationInboxService(notificationRepository, currentUserPort);
    }

    @Bean
    public InvoiceDueReminderUseCase invoiceDueReminderUseCase(
        InvoiceRepositoryPort invoiceRepository,
        InvoiceReminderPort invoiceReminderRepository,
        ContractRepositoryPort contractRepository,
        RoomRepositoryPort roomRepository,
        PropertyRepositoryPort propertyRepository,
        NotificationDispatcher notificationDispatcher,
        @org.springframework.beans.factory.annotation.Value("${phonghub.invoicing.due-reminder-days:3}") int reminderDays
    ) {
        return new InvoiceDueReminderService(invoiceRepository, invoiceReminderRepository, contractRepository,
            roomRepository, propertyRepository, notificationDispatcher, reminderDays);
    }

    @Bean
    public com.phonghub.application.port.in.ContractLifecycleUseCase contractLifecycleUseCase(
        ContractRepositoryPort contractRepository,
        com.phonghub.application.port.out.ContractReminderPort reminderPort,
        PropertyRepositoryPort propertyRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        StaffPropertyAssignmentPort assignmentRepository,
        NotificationRepositoryPort notificationRepository,
        AuditPort auditPort,
        @org.springframework.beans.factory.annotation.Value("${phonghub.contracts.expiry-reminder-days:30}") int reminderDays
    ) {
        return new com.phonghub.application.service.ContractLifecycleService(contractRepository, reminderPort,
            propertyRepository, roomRepository, tenantRepository, userRepository, assignmentRepository,
            notificationRepository, auditPort, reminderDays);
    }

    @Bean
    public StatisticsUseCase statisticsUseCase(
        StatisticsQueryPort statisticsQueryPort,
        PropertyRepositoryPort propertyRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        PhongHubCaches caches
    ) {
        return new CachingStatisticsUseCase(new StatisticsService(statisticsQueryPort, propertyRepo, currentUserPort,
            authorizationService, java.time.Clock.systemUTC()), caches, currentUserPort);
    }

    @Bean
    public NotificationUseCase notificationUseCase(
        NotificationRepositoryPort notificationRepository,
        AuthUseCase authUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        return new NotificationService(notificationRepository, authUseCase, currentUserPort, authorizationService);
    }

    @Bean
    public SepayWebhookUseCase sepayWebhookUseCase(
        PaymentRepositoryPort paymentRepository,
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        ContractRepositoryPort contractRepository,
        PropertyRepositoryPort propertyRepository,
        NotificationRepositoryPort notificationRepository,
        UserRepositoryPort userRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        SepayProperties sepayProperties,
        SepayGatewayOrderRepositoryPort gatewayOrderRepository
    ) {
        SepayPaymentService service = new SepayPaymentService(
            paymentRepository,
            invoiceRepository,
            ticketRepository,
            contractRepository,
            propertyRepository,
            notificationRepository,
            userRepository,
            currentUserPort,
            authorizationService,
            sepayProperties,
            gatewayOrderRepository
        );
        return new TransactionalSepayWebhookUseCase(service);
    }

    @Bean
    public com.phonghub.application.port.in.ExportUseCase exportUseCase(
        PropertyRepositoryPort propertyRepository,
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        StaffPropertyAssignmentPort assignmentRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        return new com.phonghub.application.service.ExportService(propertyRepository, contractRepository,
            invoiceRepository, roomRepository, tenantRepository, assignmentRepository, currentUserPort,
            authorizationService, auditPort, new com.phonghub.adapter.out.export.XlsxTableExporter(),
            new com.phonghub.adapter.out.export.PdfTableExporter(), java.time.Clock.systemUTC());
    }

    @Bean
    public com.phonghub.application.port.in.PaymentReconciliationUseCase paymentReconciliationUseCase(
        PaymentRepositoryPort paymentRepository,
        InvoiceRepositoryPort invoiceRepository,
        ContractRepositoryPort contractRepository,
        PropertyRepositoryPort propertyRepository,
        RoomRepositoryPort roomRepository,
        TenantRepositoryPort tenantRepository,
        StaffPropertyAssignmentPort assignmentRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        return new com.phonghub.application.service.PaymentReconciliationService(paymentRepository, invoiceRepository,
            contractRepository, propertyRepository, roomRepository, tenantRepository, assignmentRepository,
            currentUserPort, authorizationService);
    }

    @Bean
    public com.phonghub.application.port.in.LateFeePolicyUseCase lateFeePolicyUseCase(
        com.phonghub.application.port.out.LateFeePolicyRepositoryPort policyRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        return new TransactionalLateFeeUseCases.Policy(new com.phonghub.application.service.LateFeePolicyService(
            policyRepository, currentUserPort, authorizationService, auditPort));
    }

    @Bean
    public com.phonghub.application.port.in.LateFeeAssessmentUseCase lateFeeAssessmentUseCase(
        InvoiceRepositoryPort invoiceRepository,
        ContractRepositoryPort contractRepository,
        com.phonghub.application.port.out.LateFeePolicyRepositoryPort policyRepository,
        AuditPort auditPort
    ) {
        return new TransactionalLateFeeUseCases.Assessment(new com.phonghub.application.service.LateFeeAssessmentService(
            invoiceRepository, contractRepository, policyRepository, auditPort));
    }

    @Bean
    public MonthlyInvoiceUseCase monthlyInvoiceUseCase(
        ContractRepositoryPort contractRepository,
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        MeterReadingRepositoryPort meterReadingRepository,
        ServiceRateRepositoryPort serviceRateRepository
    ) {
        return new TransactionalMonthlyInvoiceUseCase(
            new MonthlyInvoiceService(contractRepository, invoiceRepository, ticketRepository,
                meterReadingRepository, serviceRateRepository)
        );
    }

    @Bean
    public InvoiceUseCase invoiceUseCase(
        InvoiceRepositoryPort invoiceRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        ContractUseCase contractUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        MeterReadingRepositoryPort meterReadingRepository,
        ServiceRateRepositoryPort serviceRateRepository,
        AuditPort auditPort
    ) {
        InvoiceService service = new InvoiceService(
            invoiceRepository, ticketRepository, contractUseCase, currentUserPort, authorizationService,
            meterReadingRepository, serviceRateRepository, auditPort
        );
        return new TransactionalInvoiceUseCase(service);
    }

    /** Quyết toán tiền cọc khi trả phòng: chỉ đọc, quyền do ContractUseCase/InvoiceUseCase kiểm tra. */
    @Bean
    public com.phonghub.application.port.in.CheckOutSettlementUseCase checkOutSettlementUseCase(
        ContractUseCase contractUseCase,
        InvoiceUseCase invoiceUseCase,
        HandoverRepositoryPort handoverRepo,
        HandoverItemRepositoryPort itemRepo,
        MeterReadingRepositoryPort meterReadingRepository,
        ServiceRateRepositoryPort serviceRateRepository
    ) {
        return new com.phonghub.application.service.CheckOutSettlementService(contractUseCase, invoiceUseCase,
            handoverRepo, itemRepo, meterReadingRepository, serviceRateRepository);
    }

    @Bean
    public FileStorageUseCase fileStorageUseCase(
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        StorageProperties storageProperties
    ) {
        return new PrivateFileStorageService(
            storagePort,
            currentUserPort,
            authorizationService,
            storageProperties.getMaxFileSizeBytes()
        );
    }

    @Bean
    public com.phonghub.adapter.out.event.SseChatEventPublisher sseChatEventPublisher() {
        return new com.phonghub.adapter.out.event.SseChatEventPublisher();
    }

    @Bean
    public com.phonghub.application.service.ChatService chatService(
        com.phonghub.application.port.out.ChatRoomRepositoryPort chatRoomRepository,
        com.phonghub.application.port.out.ChatMessageRepositoryPort chatMessageRepository,
        UserRepositoryPort userRepository,
        com.phonghub.adapter.out.event.SseChatEventPublisher sseChatEventPublisher
    ) {
        return new com.phonghub.application.service.ChatService(
            chatRoomRepository, chatMessageRepository, userRepository, sseChatEventPublisher,
            java.time.Clock.systemUTC()
        );
    }

    @Bean
    public com.phonghub.application.port.in.ChatRoomUseCase chatRoomUseCase(com.phonghub.application.service.ChatService chatService) {
        return chatService;
    }

    @Bean
    public com.phonghub.application.port.in.ChatMessageUseCase chatMessageUseCase(com.phonghub.application.service.ChatService chatService) {
        return chatService;
    }
}
