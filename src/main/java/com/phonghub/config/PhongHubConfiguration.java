package com.phonghub.config;

import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
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
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.service.AuthService;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.application.service.MaintenanceService;
import com.phonghub.application.service.PropertyService;
import com.phonghub.application.service.RoomService;
import com.phonghub.application.service.UserService;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.service.NotificationService;
import com.phonghub.application.port.in.SepayWebhookUseCase;
import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.application.service.SepayPaymentService;
import com.phonghub.application.port.in.InvoiceUseCase;
import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.application.service.InvoiceService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SepayProperties.class)
public class PhongHubConfiguration {

    @Bean
    public AuthorizationService authorizationService(
        StaffPropertyAssignmentPort assignmentRepo,
        PropertyRepositoryPort propertyRepo,
        TenantRepositoryPort tenantRepo,
        ContractRepositoryPort contractRepo
    ) {
        return new AuthorizationService(assignmentRepo, propertyRepo, tenantRepo, contractRepo);
    }

    @Bean
    public PropertyUseCase propertyUseCase(
        PropertyRepositoryPort propertyRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        return new PropertyService(propertyRepo, currentUserPort, authorizationService, auditPort);
    }

    @Bean
    public RoomUseCase roomUseCase(
        RoomRepositoryPort roomRepo,
        PropertyRepositoryPort propertyRepo,
        MaintenanceTicketRepositoryPort ticketRepo,
        ContractRepositoryPort contractRepo,
        TenantRepositoryPort tenantRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        return new RoomService(
            roomRepo, propertyRepo, ticketRepo, contractRepo, tenantRepo, currentUserPort, authorizationService
        );
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
    public MaintenanceUseCase maintenanceUseCase(
        MaintenanceTicketRepositoryPort ticketRepo,
        RoomRepositoryPort roomRepo,
        TenantRepositoryPort tenantRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        MaintenanceService service = new MaintenanceService(
            ticketRepo, roomRepo, tenantRepo, currentUserPort, authorizationService
        );
        return new TransactionalMaintenanceUseCase(service);
    }

    @Bean
    public UserUseCase userUseCase(
        UserRepositoryPort userRepo,
        StaffPropertyAssignmentPort assignmentRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        return new UserService(
            userRepo, assignmentRepo, currentUserPort, authorizationService
        );
    }

    @Bean
    public AuthUseCase authUseCase(
        UserRepositoryPort userRepo,
        IdentityProviderPort identityProviderPort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        return new AuthService(userRepo, identityProviderPort, currentUserPort, authorizationService, auditPort);
    }

    @Bean
    public TenantUseCase tenantUseCase(
        TenantRepositoryPort tenantRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort
    ) {
        TenantService service = new TenantService(tenantRepo, currentUserPort, authorizationService, auditPort);
        return new TransactionalTenantUseCase(service);
    }

    @Bean
    public NotificationRepositoryPort notificationRepository() {
        return new InMemoryNotificationRepository();
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
        ContractRepositoryPort contractRepository,
        PropertyRepositoryPort propertyRepository,
        NotificationRepositoryPort notificationRepository,
        UserRepositoryPort userRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        SepayProperties sepayProperties
    ) {
        SepayPaymentService service = new SepayPaymentService(
            paymentRepository,
            invoiceRepository,
            contractRepository,
            propertyRepository,
            notificationRepository,
            userRepository,
            currentUserPort,
            authorizationService,
            sepayProperties
        );
        return new TransactionalSepayWebhookUseCase(service);
    }

    @Bean
    public InvoiceUseCase invoiceUseCase(
        InvoiceRepositoryPort invoiceRepository,
        ContractUseCase contractUseCase,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        InvoiceService service = new InvoiceService(
            invoiceRepository, contractUseCase, currentUserPort, authorizationService
        );
        return new TransactionalInvoiceUseCase(service);
    }
}
