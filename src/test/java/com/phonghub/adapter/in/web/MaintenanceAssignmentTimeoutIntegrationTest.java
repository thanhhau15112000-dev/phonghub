package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.service.MaintenanceAssignmentTimeoutService;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class MaintenanceAssignmentTimeoutIntegrationTest extends SeededWebTest {

    private static final UUID OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID;
    private static final UUID STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID;
    private static final UUID STAFF_WITHOUT_CONTRACT_PERMISSION =
        UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID UNASSIGNED_STAFF = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID INACTIVE_ASSIGNED_STAFF = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID;
    private static final UUID TENANT_1 = DataSeeder.TENANT_RECORD_ID;
    private static final UUID TECH_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired private MaintenanceAssignmentTimeoutService timeoutService;
    @Autowired private InMemoryMaintenanceTicketRepository tickets;
    @Autowired private InMemoryNotificationRepository notifications;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignments;
    @Autowired private InMemoryUserRepository users;
    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("At 72 hours an assigned or in-progress ticket is marked once and notifies tenant, owner and responsible staff")
    void notifiesAtAssignmentTimeoutExactlyOnce() throws Exception {
        Instant now = Instant.now();
        addTimeoutNotificationRecipients();
        MaintenanceTicket ticket = saveTicket(MaintenanceStatus.IN_PROGRESS, TECH_1,
            now.minus(Duration.ofHours(72)), null);
        int before = notifications.findAll().size();

        assertEquals(MaintenanceAssignmentTimeoutService.ProcessResult.MARKED_UNDERSTAFFED,
            timeoutService.processTicket(ticket.getId(), now));

        MaintenanceTicket marked = tickets.findById(ticket.getId()).orElseThrow();
        assertEquals(now, marked.getUnderstaffedNotifiedAt());
        assertEquals(now.minus(Duration.ofHours(72)), marked.getAssignedAt());
        assertEquals(before + 4, notifications.findAll().size());
        var shortageNotifications = notifications.findAll().stream()
            .filter(n -> n.title().equals("Phiếu bảo trì thiếu nhân lực")
                && n.link().equals("/maintenance/" + ticket.getId()))
            .toList();
        assertEquals(4, shortageNotifications.size());
        assertEquals(Set.of(OWNER_1, STAFF_1, STAFF_WITHOUT_CONTRACT_PERMISSION,
                LocalDemoAuthenticationAdapter.TENANT_1_ID),
            shortageNotifications.stream().map(n -> n.targetUserId()).collect(Collectors.toSet()));
        assertTrue(shortageNotifications.stream().anyMatch(n -> n.targetUserId().equals(OWNER_1)
            && n.message().contains("Kỹ thuật viên dự phòng")));
        assertTrue(tickets.findEvents(ticket.getId()).stream().anyMatch(event ->
            event.note().contains("thiếu nhân lực")));
        mockMvc.perform(get("/maintenance/" + ticket.getId()).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("sẽ tự hủy sau")));

        assertEquals(MaintenanceAssignmentTimeoutService.ProcessResult.NO_ACTION,
            timeoutService.processTicket(ticket.getId(), now.plusSeconds(1)));
        assertEquals(before + 4, notifications.findAll().size());
    }

    @Test
    @DisplayName("Tickets just before 72 hours are not marked; the deadline is inclusive")
    void assignmentDeadlineBoundary() {
        Instant now = Instant.now();
        MaintenanceTicket ticketBeforeDeadline = saveTicket(MaintenanceStatus.ASSIGNED, TECH_1,
            now.minus(Duration.ofHours(72)).plusNanos(1), null);
        MaintenanceTicket ticketAtDeadline = saveTicket(MaintenanceStatus.ASSIGNED, TECH_1,
            now.minus(Duration.ofHours(72)), null);

        assertEquals(MaintenanceAssignmentTimeoutService.ProcessResult.NO_ACTION,
            timeoutService.processTicket(ticketBeforeDeadline.getId(), now));
        assertEquals(MaintenanceAssignmentTimeoutService.ProcessResult.MARKED_UNDERSTAFFED,
            timeoutService.processTicket(ticketAtDeadline.getId(), now));
    }

    @Test
    @DisplayName("An understaffed ticket is auto-cancelled once after 24 hours with an audit timeline entry")
    void autoCancelsAfterTwentyFourHours() throws Exception {
        Instant now = Instant.now();
        addTimeoutNotificationRecipients();
        MaintenanceTicket ticket = saveTicket(MaintenanceStatus.ASSIGNED, TECH_1,
            now.minus(Duration.ofHours(96)), now.minus(Duration.ofHours(24)));
        int before = notifications.findAll().size();

        assertEquals(MaintenanceAssignmentTimeoutService.ProcessResult.AUTO_CANCELLED,
            timeoutService.processTicket(ticket.getId(), now));

        MaintenanceTicket cancelled = tickets.findById(ticket.getId()).orElseThrow();
        assertEquals(MaintenanceStatus.CANCELLED, cancelled.getStatus());

        assertEquals(before + 4, notifications.findAll().size());
        var cancellationNotifications = notifications.findAll().stream()
            .filter(n -> n.title().equals("Phiếu bảo trì bị hủy tự động")
                && n.link().equals("/maintenance/" + ticket.getId()))
            .toList();
        assertEquals(4, cancellationNotifications.size());
        assertEquals(Set.of(OWNER_1, STAFF_1, STAFF_WITHOUT_CONTRACT_PERMISSION,
                LocalDemoAuthenticationAdapter.TENANT_1_ID),
            cancellationNotifications.stream().map(n -> n.targetUserId()).collect(Collectors.toSet()));
        assertTrue(tickets.findEvents(ticket.getId()).stream().anyMatch(event ->
            event.toStatus() == MaintenanceStatus.CANCELLED && event.note().contains("Hủy tự động do thiếu nhân lực")));
        mockMvc.perform(get("/maintenance/" + ticket.getId()).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thời hạn xử lý đã dừng vì phiếu đã đóng.")))
            .andExpect(content().string(containsString("Thời hạn tự hủy đã dừng vì phiếu đã đóng")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("sẽ tự hủy sau"))));

        assertEquals(MaintenanceAssignmentTimeoutService.ProcessResult.NO_ACTION,
            timeoutService.processTicket(ticket.getId(), now.plus(Duration.ofHours(1))));
        assertEquals(before + 4, notifications.findAll().size());
    }

    @Test
    @DisplayName("A completed ticket is not auto-cancelled even when its understaffed timer has expired")
    void completionStopsAutoCancellation() throws Exception {
        Instant now = Instant.now();
        MaintenanceTicket ticket = saveTicket(MaintenanceStatus.RESOLVED, TECH_1,
            now.minus(Duration.ofDays(5)), now.minus(Duration.ofDays(2)));
        int before = notifications.findAll().size();

        assertEquals(MaintenanceAssignmentTimeoutService.ProcessResult.NO_ACTION,
            timeoutService.processTicket(ticket.getId(), now));
        assertEquals(MaintenanceStatus.RESOLVED, tickets.findById(ticket.getId()).orElseThrow().getStatus());
        assertEquals(before, notifications.findAll().size());
        mockMvc.perform(get("/maintenance/" + ticket.getId()).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thời hạn xử lý đã dừng vì phiếu đã đóng.")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("null đến mốc"))));
    }

    @Test
    @DisplayName("A manager can reassign an in-progress ticket with a reason; the old technician loses access")
    void reassignsInProgressTicketAndResetsTimeout() throws Exception {
        addSecondTechnician();
        Instant assignedAt = Instant.now().minus(Duration.ofHours(80));
        MaintenanceTicket ticket = saveTicket(MaintenanceStatus.IN_PROGRESS, TECH_1,
            assignedAt, Instant.now().minus(Duration.ofHours(1)));
        String endpoint = "/api/maintenance/" + ticket.getId() + "/assign";

        mockMvc.perform(post(endpoint).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicianId\":\"" + TECH_2 + "\"}"))
            .andExpect(status().isBadRequest());
        assertEquals(TECH_1, tickets.findById(ticket.getId()).orElseThrow().getAssignedTechnicianId());

        int before = notifications.findAll().size();
        mockMvc.perform(post(endpoint).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicianId\":\"" + TECH_2 + "\",\"reason\":\"Chuyển sang ca trực khác\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.assignedTechnicianId").value(TECH_2.toString()));

        MaintenanceTicket reassigned = tickets.findById(ticket.getId()).orElseThrow();
        assertEquals(TECH_2, reassigned.getAssignedTechnicianId());
        assertTrue(reassigned.getAssignedAt().isAfter(assignedAt));
        assertNull(reassigned.getUnderstaffedNotifiedAt());
        assertEquals(before + 2, notifications.findAll().size());
        var reassignmentNotifications = notifications.findAll().stream()
            .filter(n -> n.title().equals("Phiếu bảo trì đã được giao lại")
                || n.title().equals("Bạn được giao phiếu bảo trì"))
            .toList();
        assertEquals(2, reassignmentNotifications.size());
        assertEquals(Set.of(TECH_1, TECH_2),
            reassignmentNotifications.stream().map(n -> n.targetUserId()).collect(Collectors.toSet()));
        assertTrue(tickets.findEvents(ticket.getId()).stream().anyMatch(event ->
            event.note().contains("Chuyển sang ca trực khác")));

        mockMvc.perform(get("/api/maintenance/" + ticket.getId()).header("X-User-Id", TECH_1))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/maintenance/" + ticket.getId()).header("X-User-Id", TECH_2))
            .andExpect(status().isOk());
        mockMvc.perform(get("/maintenance/" + ticket.getId()).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Lý do giao lại")));
        assertNull(reassigned.getUnderstaffedNotifiedAt());
        assertNotNull(reassigned.getAssignedAt());
    }

    private void addSecondTechnician() {
        users.save(new User(TECH_2, "tech2", "tech2@phonghub.local", "Kỹ thuật viên dự phòng", "0900000012",
            UserRole.TECHNICIAN, User.UserStatus.ACTIVE, false, Instant.now()));
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), TECH_2, DataSeeder.PROP_1_ID,
            false, false, false, Instant.now()));
    }

    private void addTimeoutNotificationRecipients() {
        addSecondTechnician();
        users.save(new User(STAFF_WITHOUT_CONTRACT_PERMISSION, "staff-no-contract-permission",
            "staff-no-contract-permission@phonghub.local", "Nhân viên không có quyền hợp đồng", "0900000013",
            UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()));
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), STAFF_WITHOUT_CONTRACT_PERMISSION,
            DataSeeder.PROP_1_ID, false, false, false, Instant.now()));

        users.save(new User(UNASSIGNED_STAFF, "unassigned-staff", "unassigned-staff@phonghub.local",
            "Nhân viên chưa được phân công", "0900000014", UserRole.STAFF, User.UserStatus.ACTIVE, false,
            Instant.now()));

        users.save(new User(INACTIVE_ASSIGNED_STAFF, "inactive-assigned-staff",
            "inactive-assigned-staff@phonghub.local", "Nhân viên đã ngừng hoạt động", "0900000015",
            UserRole.STAFF, User.UserStatus.INACTIVE, false, Instant.now()));
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), INACTIVE_ASSIGNED_STAFF,
            DataSeeder.PROP_1_ID, false, false, false, Instant.now()));
    }

    private MaintenanceTicket saveTicket(
        MaintenanceStatus status,
        UUID technicianId,
        Instant assignedAt,
        Instant understaffedAt
    ) {
        Instant createdAt = assignedAt.minus(Duration.ofHours(1));
        MaintenanceTicket ticket = new MaintenanceTicket(UUID.randomUUID(), DataSeeder.ROOM_101_ID,
            DataSeeder.PROP_1_ID, TENANT_1, technicianId, "Máy bơm hỏng", "Không chạy",
            MaintenancePriority.MEDIUM, status, BigDecimal.ZERO, null, null,
            com.phonghub.domain.model.LiableParty.OWNER, null, null, null, createdAt, createdAt);
        ticket.restoreAssignmentTimeoutState(assignedAt, understaffedAt);
        return tickets.save(ticket);
    }
}
