package com.phonghub.config;

import com.phonghub.adapter.in.security.SecurityCurrentUserAdapter;
import com.phonghub.adapter.out.audit.PostgresAuditAdapter;
import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPropertyRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresUserRepository;
import com.phonghub.adapter.out.supabase.auth.SupabaseIdentityProviderAdapter;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Profile("prod")
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
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();
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
    public ContractRepositoryPort contractRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresContractRepository(jdbcTemplate);
    }

    @Bean
    public TenantRepositoryPort tenantRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresTenantRepository(jdbcTemplate);
    }

    @Bean
    public MaintenanceTicketRepositoryPort maintenanceTicketRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresMaintenanceTicketRepository(jdbcTemplate);
    }

    @Bean
    public UserRepositoryPort userRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresUserRepository(jdbcTemplate);
    }

    @Bean
    public StaffPropertyAssignmentPort staffPropertyAssignmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresStaffPropertyAssignmentRepository(jdbcTemplate);
    }

    @Bean
    public AuditPort auditPort(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresAuditAdapter(jdbcTemplate, new ObjectMapper());
    }

    @Bean
    public CurrentUserPort currentUserPort() {
        return new SecurityCurrentUserAdapter();
    }

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }

    @Bean
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
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
