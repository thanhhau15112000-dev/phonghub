package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.application.service.MaintenanceSlaService;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class MaintenanceSlaIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMaintenanceTicketRepository tickets;
    @Autowired private InMemoryNotificationRepository notifications;
    @Autowired private MaintenanceSlaService slaService;

    private String report(String priority) throws Exception {
        String body = "{\"roomId\":\"" + DataSeeder.ROOM_101_ID + "\",\"title\":\"Hỏng\",\"description\":\"Mô tả\","
            + "\"priority\":\"" + priority + "\",\"setRoomMaintenance\":false}";
        return mockMvc.perform(post("/api/maintenance").header("X-User-Id", TENANT_1)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private static long hoursUntilDue(String json) {
        Instant created = Instant.parse(JsonPath.<String>read(json, "$.createdAt"));
        Instant due = Instant.parse(JsonPath.<String>read(json, "$.dueAt"));
        return Duration.between(created, due).toHours();
    }

    /** Phiếu mở, hạn đã qua một giờ trước. */
    private MaintenanceTicket overdueTicket(MaintenanceStatus status, UUID technician) {
        Instant created = Instant.now().minus(Duration.ofDays(5));
        MaintenanceTicket ticket = new MaintenanceTicket(UUID.randomUUID(), DataSeeder.ROOM_101_ID, DataSeeder.PROP_1_ID,
            null, technician, "Quá hạn", "Chậm xử lý", MaintenancePriority.HIGH, status, BigDecimal.ZERO, null, null,
            LiableParty.OWNER, null, Instant.now().minus(Duration.ofHours(1)), null, created, created);
        return tickets.save(ticket);
    }

    @Test
    @DisplayName("The due date comes from the priority; the defaults are configurable per priority")
    void dueDateFollowsPriority() throws Exception {
        assertEquals(4, hoursUntilDue(report("URGENT")));
        assertEquals(24, hoursUntilDue(report("HIGH")));
        assertEquals(72, hoursUntilDue(report("MEDIUM")));
        assertEquals(168, hoursUntilDue(report("LOW")));
    }

    @Test
    @DisplayName("Timeline records each status change with its actor, and is limited to those who can see the ticket")
    void timeline() throws Exception {
        String id = JsonPath.read(report("MEDIUM"), "$.id");
        MaintenanceTestFlow.wearReady(mockMvc, id, OWNER_1);
        mockMvc.perform(post("/api/maintenance/" + id + "/assign").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"technicianId\":\"" + TECH_1 + "\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/maintenance/" + id + "/accept").header("X-User-Id", TECH_1))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/maintenance/" + id + "/resolve").header("X-User-Id", TECH_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"resolutionNotes\":\"Xong\",\"releaseRoomToAvailable\":false}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/maintenance/" + id + "/timeline").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].to").value("REPORTED"))
            .andExpect(jsonPath("$[0].actorId").value(TENANT_1))
            .andExpect(jsonPath("$[1].to").value("PENDING_OWNER_APPROVAL"))
            .andExpect(jsonPath("$[1].actorId").value(OWNER_1))
            .andExpect(jsonPath("$[2].to").value("REPORTED"))
            .andExpect(jsonPath("$[3].to").value("ASSIGNED"))
            .andExpect(jsonPath("$[3].actorId").value(OWNER_1))
            .andExpect(jsonPath("$[4].to").value("IN_PROGRESS"))
            .andExpect(jsonPath("$[5].to").value("RESOLVED"));
        mockMvc.perform(get("/api/maintenance/" + id + "/timeline").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/maintenance/" + id + "/timeline").header("X-User-Id", TECH_1))
            .andExpect(status().isOk());
        for (String denied : new String[]{OWNER_2, STAFF_2}) {
            mockMvc.perform(get("/api/maintenance/" + id + "/timeline").header("X-User-Id", denied))
                .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/maintenance/" + UUID.randomUUID() + "/timeline").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Only open tickets past their due date are overdue; finished ones never are")
    void overdueFlag() throws Exception {
        MaintenanceTicket open = overdueTicket(MaintenanceStatus.IN_PROGRESS, TECH_1_ID());
        MaintenanceTicket done = overdueTicket(MaintenanceStatus.RESOLVED, TECH_1_ID());
        assertTrue(open.isOverdue(Instant.now()));
        assertEquals(false, done.isOverdue(Instant.now()));

        mockMvc.perform(get("/api/maintenance/" + open.getId()).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overdue").value(true));
        mockMvc.perform(get("/api/maintenance/" + done.getId()).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    @DisplayName("Overdue tickets notify the owner and assigned technician exactly once")
    void overdueNotification() throws Exception {
        overdueTicket(MaintenanceStatus.ASSIGNED, TECH_1_ID());
        overdueTicket(MaintenanceStatus.RESOLVED, TECH_1_ID());
        int before = notifications.findAll().size();

        // Phiếu mẫu IN_PROGRESS chưa có hạn nên không tính; chỉ phiếu quá hạn đang mở được báo.
        assertEquals(1, slaService.notifyOverdue(Instant.now()));
        assertEquals(before + 2, notifications.findAll().size());
        assertTrue(notifications.findAll().stream().anyMatch(n -> n.title().equals("Phiếu bảo trì quá hạn")
            && TECH_1.equals(String.valueOf(n.targetUserId()))));
        assertTrue(notifications.findAll().stream().anyMatch(n -> n.title().equals("Phiếu bảo trì quá hạn")
            && OWNER_1.equals(String.valueOf(n.targetUserId()))));

        assertEquals(0, slaService.notifyOverdue(Instant.now()));
        assertEquals(before + 2, notifications.findAll().size());
    }

    private static UUID TECH_1_ID() {
        return LocalDemoAuthenticationAdapter.TECH_1_ID;
    }
}
