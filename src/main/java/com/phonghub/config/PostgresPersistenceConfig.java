package com.phonghub.config;

import com.phonghub.adapter.in.security.SecurityCurrentUserAdapter;
import com.phonghub.adapter.out.audit.PostgresAuditAdapter;
import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPaymentRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresSepayGatewayOrderRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPropertyRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomAssetEventRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverItemRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresMeterReadingRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresServiceRateRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresHandoverRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPropertyHouseRulesRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomAssetRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomConditionRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantDocumentRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresStatisticsQueryAdapter;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresUserRepository;
import com.phonghub.adapter.out.supabase.auth.SupabaseIdentityProviderAdapter;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.application.port.out.SepayGatewayOrderRepositoryPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomAssetEventRepositoryPort;
import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.PropertyHouseRulesRepositoryPort;
import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Profile({"prod", "docker"})
public class PostgresPersistenceConfig {

    @Bean
    public DataSource dataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password
    ) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(
        DataSource dataSource,
        @Value("${spring.flyway.locations:classpath:db/migration}") String flywayLocations,
        Environment environment
    ) {
        // V14/V17 are historical Java migrations with legacy key fallbacks. Never let
        // production run Flyway unless the configured data-protection keys are explicit.
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            requireEnvironmentVariable("TENANT_DATA_KEY_ID");
            requireEnvironmentVariable("TENANT_DATA_ENCRYPTION_KEY");
            requireEnvironmentVariable("TENANT_DATA_HMAC_KEY");
        }
        return Flyway.configure()
            .dataSource(dataSource)
            .locations(Arrays.stream(flywayLocations.split(","))
                .map(String::trim)
                .filter(location -> !location.isEmpty())
                .toArray(String[]::new))
            .baselineOnMigrate(true)
            .outOfOrder(true)
            .load();
    }

    private static void requireEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Production configuration failure: required environment variable '" + name
                    + "' is missing or blank; refusing to run Flyway migrations."
            );
        }
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public PropertyRepositoryPort propertyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresPropertyRepository(jdbcTemplate);
    }

    @Bean
    public RoomRepositoryPort roomRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresRoomRepository(jdbcTemplate);
    }

    @Bean
    public MeterReadingRepositoryPort meterReadingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresMeterReadingRepository(jdbcTemplate);
    }

    @Bean
    public ServiceRateRepositoryPort serviceRateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresServiceRateRepository(jdbcTemplate);
    }

    @Bean
    public HandoverRepositoryPort handoverRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresHandoverRepository(jdbcTemplate);
    }

    @Bean
    public HandoverPhotoRepositoryPort handoverPhotoRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresHandoverPhotoRepository(jdbcTemplate);
    }

    @Bean
    public HandoverItemRepositoryPort handoverItemRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresHandoverItemRepository(jdbcTemplate);
    }

    @Bean
    public PropertyHouseRulesRepositoryPort propertyHouseRulesRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresPropertyHouseRulesRepository(jdbcTemplate);
    }

    @Bean
    public com.phonghub.application.port.out.TenantPrivacyRepositoryPort tenantPrivacyRepository(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresTenantPrivacyRepository(jdbcTemplate);
    }

    @Bean
    public com.phonghub.application.port.out.MaintenanceAttachmentRepositoryPort maintenanceAttachmentRepository(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceAttachmentRepository(jdbcTemplate);
    }

    @Bean
    public com.phonghub.application.port.out.MeterSubmissionRepositoryPort meterSubmissionRepository(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresMeterSubmissionRepository(jdbcTemplate);
    }

    @Bean
    public TenantDocumentRepositoryPort tenantDocumentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresTenantDocumentRepository(jdbcTemplate);
    }

    @Bean
    public RoomAssetRepositoryPort roomAssetRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresRoomAssetRepository(jdbcTemplate);
    }

    @Bean
    public RoomConditionRepositoryPort roomConditionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresRoomConditionRepository(jdbcTemplate);
    }

    @Bean
    public RoomAssetEventRepositoryPort roomAssetEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresRoomAssetEventRepository(jdbcTemplate);
    }

    @Bean
    public ContractRepositoryPort contractRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresContractRepository(jdbcTemplate);
    }

    @Bean
    public TenantRepositoryPort tenantRepository(
        NamedParameterJdbcTemplate jdbcTemplate,
        TenantDataProtectionPort tenantDataProtection
    ) {
        return new PostgresTenantRepository(jdbcTemplate, tenantDataProtection);
    }

    @Bean
    public TenantDataProtectionPort tenantDataProtection(
        TenantDataProtectionProperties properties
    ) {
        return new AesGcmTenantDataProtector(
            properties.getKeyId(),
            properties.getEncryptionKey(),
            properties.getHmacKey()
        );
    }

    @Bean
    public MaintenanceTicketRepositoryPort maintenanceTicketRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresMaintenanceTicketRepository(jdbcTemplate);
    }

    @Bean
    public UserRepositoryPort userRepository(
        NamedParameterJdbcTemplate jdbcTemplate,
        TenantDataProtectionPort tenantDataProtection
    ) {
        return new PostgresUserRepository(jdbcTemplate, tenantDataProtection);
    }

    @Bean
    public StaffPropertyAssignmentPort staffPropertyAssignmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresStaffPropertyAssignmentRepository(jdbcTemplate);
    }

    @Bean
    public PaymentRepositoryPort paymentRepository(
        NamedParameterJdbcTemplate jdbcTemplate,
        TenantDataProtectionPort tenantDataProtection
    ) {
        return new PostgresPaymentRepository(jdbcTemplate, tenantDataProtection);
    }

    @Bean
    public com.phonghub.application.port.out.InvoicePaymentRecordRepositoryPort invoicePaymentRecordRepository(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresInvoicePaymentRecordRepository(jdbcTemplate);
    }

    @Bean
    public com.phonghub.application.port.out.ContractReminderPort contractReminderRepository(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresContractReminderRepository(jdbcTemplate);
    }

    @Bean
    public SepayGatewayOrderRepositoryPort sepayGatewayOrderRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresSepayGatewayOrderRepository(jdbcTemplate);
    }

    @Bean
    public InvoiceRepositoryPort invoiceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresInvoiceRepository(jdbcTemplate);
    }

    @Bean
    public com.phonghub.application.port.out.NotificationRepositoryPort notificationRepository(
        NamedParameterJdbcTemplate jdbcTemplate,
        TenantDataProtectionPort tenantDataProtection
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresNotificationRepository(jdbcTemplate,
            tenantDataProtection);
    }

    @Bean
    public com.phonghub.application.port.out.InvoiceReminderPort invoiceReminderRepository(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceReminderRepository(jdbcTemplate);
    }

    @Bean
    public StatisticsQueryPort statisticsQueryPort(
        NamedParameterJdbcTemplate jdbcTemplate,
        TenantDataProtectionPort tenantDataProtection
    ) {
        return new PostgresStatisticsQueryAdapter(jdbcTemplate, tenantDataProtection);
    }

    @Bean
    public com.phonghub.application.port.out.LateFeePolicyRepositoryPort lateFeePolicyRepository(
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresLateFeePolicyRepository(jdbcTemplate);
    }

    @Bean
    public com.phonghub.application.port.out.ChatRoomRepositoryPort chatRoomRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresChatRoomRepository(jdbcTemplate);
    }

    @Bean
    public com.phonghub.application.port.out.ChatMessageRepositoryPort chatMessageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresChatMessageRepository(jdbcTemplate);
    }

    @Bean
    public PostgresAuditAdapter auditPort(
        NamedParameterJdbcTemplate jdbcTemplate,
        TenantDataProtectionPort tenantDataProtection
    ) {
        return new PostgresAuditAdapter(jdbcTemplate, new ObjectMapper(), tenantDataProtection);
    }

    @Bean
    @Profile("prod")
    public CurrentUserPort currentUserPort() {
        return new SecurityCurrentUserAdapter();
    }

    @Bean
    @Profile("prod")
    public RestClient restClient() {
        return RestClient.create();
    }

    @Bean
    @Profile("prod")
    public IdentityProviderPort identityProviderPort(
        RestClient restClient,
        SupabaseProperties supabaseProperties
    ) {
        return new SupabaseIdentityProviderAdapter(
            restClient,
            supabaseProperties.getUrl(),
            supabaseProperties.getAnonKey(),
            supabaseProperties.getServiceRoleKey(),
            new ObjectMapper()
        );
    }

    @Bean
    public PlatformTransactionManager transactionManager(
        DataSource dataSource,
        CacheEvictingTransactionListener cacheEvictingTransactionListener
    ) {
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        // Transaction ghi commit xong thì xóa cache danh sách/dashboard (#88).
        manager.addListener(cacheEvictingTransactionListener);
        return manager;
    }
}
