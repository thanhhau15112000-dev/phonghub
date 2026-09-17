package com.phonghub.config;

import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class InMemoryPersistenceConfig {

    @Bean
    public InMemoryPropertyRepository propertyRepository() {
        return new InMemoryPropertyRepository();
    }

    @Bean
    public InMemoryRoomRepository roomRepository() {
        return new InMemoryRoomRepository();
    }

    @Bean
    public InMemoryContractRepository contractRepository() {
        return new InMemoryContractRepository();
    }

    @Bean
    public InMemoryTenantRepository tenantRepository() {
        return new InMemoryTenantRepository();
    }

    @Bean
    public InMemoryMaintenanceTicketRepository maintenanceTicketRepository() {
        return new InMemoryMaintenanceTicketRepository();
    }

    @Bean
    public InMemoryUserRepository userRepository() {
        return new InMemoryUserRepository();
    }

    @Bean
    public InMemoryStaffPropertyAssignmentRepository staffPropertyAssignmentRepository() {
        return new InMemoryStaffPropertyAssignmentRepository();
    }

    @Bean
    public Boolean seedInitialData(
        InMemoryUserRepository userRepo,
        InMemoryPropertyRepository propertyRepo,
        InMemoryStaffPropertyAssignmentRepository assignmentRepo,
        InMemoryRoomRepository roomRepo,
        InMemoryTenantRepository tenantRepo,
        InMemoryContractRepository contractRepo,
        InMemoryMaintenanceTicketRepository ticketRepo
    ) {
        DataSeeder.seedAll(
            userRepo, propertyRepo, assignmentRepo, roomRepo, tenantRepo, contractRepo, ticketRepo
        );
        return Boolean.TRUE;
    }
}
