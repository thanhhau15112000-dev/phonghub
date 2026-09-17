package com.phonghub.config;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.application.port.in.ContractUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.ContractService;
import com.phonghub.application.service.MaintenanceService;
import com.phonghub.application.service.PropertyService;
import com.phonghub.application.service.RoomService;
import com.phonghub.application.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PhongHubConfiguration {

    @Bean
    public LocalDemoAuthenticationAdapter currentUserPort() {
        return new LocalDemoAuthenticationAdapter();
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

    @Bean
    public PropertyUseCase propertyUseCase(
        PropertyRepositoryPort propertyRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        return new PropertyService(propertyRepo, currentUserPort, authorizationService);
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
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        return new ContractService(
            contractRepo, roomRepo, propertyRepo, tenantRepo, currentUserPort, authorizationService
        );
    }

    @Bean
    public MaintenanceUseCase maintenanceUseCase(
        MaintenanceTicketRepositoryPort ticketRepo,
        RoomRepositoryPort roomRepo,
        TenantRepositoryPort tenantRepo,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService
    ) {
        return new MaintenanceService(
            ticketRepo, roomRepo, tenantRepo, currentUserPort, authorizationService
        );
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
}
