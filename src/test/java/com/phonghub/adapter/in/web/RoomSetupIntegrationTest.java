package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomConditionRepository;
import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import com.phonghub.domain.model.StaffPropertyAssignment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class RoomSetupIntegrationTest extends SeededWebTest {
    private static final String OWNER = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final UUID PROPERTY = DataSeeder.PROP_1_ID;

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepositoryPort rooms;
    @Autowired private RoomConditionRepositoryPort conditions;
    @Autowired private StaffPropertyAssignmentPort assignments;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryRoomConditionRepository conditionStore;

    @BeforeEach
    void clearHandoverAndConditionFixtures() {
        handovers.clear();
        conditionStore.clear();
    }

    @Test
    @DisplayName("Single create saves required meter readings, condition note and asset copy")
    void createOneRoomWithInitialCondition() throws Exception {
        mockMvc.perform(post("/properties/" + PROPERTY + "/rooms").header("X-User-Id", OWNER)
                .param("roomNumber", "P109").param("floor", "2").param("areaSqm", "20")
                .param("basePrice", "3500000").param("maxOccupants", "2")
                .param("electricMeter", "12.500").param("waterMeter", "3.250")
                .param("conditionNote", "Tường mới sơn").param("assetName", "Giường")
                .param("assetQuantity", "1").param("assetStatus", "DAMAGED")
                .param("assetNote", "Trầy cạnh").param("assetValue", "1000000"))
            .andExpect(flash().attributeExists("successMessage"));

        Room room = rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P109").orElseThrow();
        var condition = conditions.findLatestByRoomId(room.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("12.500").compareTo(condition.electricMeter()));
        assertEquals("Tường mới sơn", condition.conditionNote());
        var item = conditions.findItemsBySnapshotId(condition.id()).getFirst();
        assertEquals("Giường", item.name());
        assertEquals("Trầy cạnh", item.note());
        assertEquals("Tường mới sơn", condition.conditionNote());

        String detail = mockMvc.perform(get("/rooms/" + room.getId()).header("X-User-Id", OWNER))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("Biên bản hiện trạng"));
        assertTrue(detail.contains("Tường mới sơn"));
        assertTrue(detail.contains("Giường"));
    }

    @Test
    @DisplayName("Single create can seed the initial condition from a room asset template")
    void createOneRoomFromTemplate() throws Exception {
        mockMvc.perform(post("/properties/" + PROPERTY + "/rooms").header("X-User-Id", OWNER)
                .param("roomNumber", "P109").param("floor", "2").param("areaSqm", "20")
                .param("basePrice", "3500000").param("maxOccupants", "2")
                .param("electricMeter", "12").param("waterMeter", "3").param("conditionNote", "Phòng mới")
                .param("assetTemplate", "BASIC"))
            .andExpect(flash().attributeExists("successMessage"));

        Room room = rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P109").orElseThrow();
        var baseline = conditions.findLatestByRoomId(room.getId()).orElseThrow();
        assertEquals(5, conditions.findItemsBySnapshotId(baseline.id()).size());
    }

    @Test
    @DisplayName("Bulk create expands a range and stores per-room readings plus the shared asset template")
    void createRoomRange() throws Exception {
        mockMvc.perform(post("/properties/" + PROPERTY + "/rooms/batch").header("X-User-Id", OWNER)
                .param("roomNumbers", "P105-P106").param("floor", "2").param("areaSqm", "20")
                .param("basePrice", "3500000").param("maxOccupants", "2")
                .param("conditionNote", "Phòng mới").param("assetTemplate", "BASIC")
                .param("electricMeters", "100", "110").param("waterMeters", "20", "25"))
            .andExpect(flash().attribute("successMessage", "Đã tạo 2 phòng cùng biên bản hiện trạng."));

        Room first = rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P105").orElseThrow();
        Room second = rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P106").orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(conditions.findLatestByRoomId(first.getId()).orElseThrow().electricMeter()));
        assertEquals(0, new BigDecimal("25").compareTo(conditions.findLatestByRoomId(second.getId()).orElseThrow().waterMeter()));
        assertEquals(5, conditions.findItemsBySnapshotId(conditions.findLatestByRoomId(first.getId()).orElseThrow().id()).size());
        assertEquals(5, conditions.findItemsBySnapshotId(conditions.findLatestByRoomId(second.getId()).orElseThrow().id()).size());
    }

    @Test
    @DisplayName("API creates a batch with separate readings for each room")
    void apiCreatesRoomBatch() throws Exception {
        String request = "{\"floor\":2,\"areaSqm\":20,\"basePrice\":3500000,\"maxOccupants\":2,"
            + "\"conditionNote\":\"Phòng mới\",\"assetTemplate\":\"NONE\",\"rooms\":["
            + "{\"roomNumber\":\"P105\",\"electricMeter\":100,\"waterMeter\":20},"
            + "{\"roomNumber\":\"P106\",\"electricMeter\":110,\"waterMeter\":25}]}";

        mockMvc.perform(post("/api/properties/" + PROPERTY + "/rooms/batch").header("X-User-Id", OWNER)
                .contentType("application/json").content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].roomNumber").value("P105"))
            .andExpect(jsonPath("$[1].roomNumber").value("P106"));

        Room first = rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P105").orElseThrow();
        Room second = rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P106").orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(conditions.findLatestByRoomId(first.getId()).orElseThrow().electricMeter()));
        assertEquals(0, new BigDecimal("25").compareTo(conditions.findLatestByRoomId(second.getId()).orElseThrow().waterMeter()));
    }

    @Test
    @DisplayName("Single room API accepts a selected asset template")
    void apiCreatesRoomFromTemplate() throws Exception {
        String request = "{\"roomNumber\":\"P109\",\"floor\":2,\"areaSqm\":20,\"basePrice\":3500000,"
            + "\"maxOccupants\":2,\"electricMeter\":12,\"waterMeter\":3,\"conditionNote\":\"Phòng mới\","
            + "\"noAssets\":false,\"assetTemplate\":\"BASIC\"}";

        mockMvc.perform(post("/api/properties/" + PROPERTY + "/rooms").header("X-User-Id", OWNER)
                .contentType("application/json").content(request))
            .andExpect(status().isCreated());

        Room room = rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P109").orElseThrow();
        var baseline = conditions.findLatestByRoomId(room.getId()).orElseThrow();
        assertEquals(5, conditions.findItemsBySnapshotId(baseline.id()).size());
    }

    @Test
    @DisplayName("API duplicate in a batch returns conflict without creating any room")
    void apiRejectsDuplicateBatchAtomically() throws Exception {
        String request = "{\"floor\":2,\"areaSqm\":20,\"basePrice\":3500000,\"maxOccupants\":2,"
            + "\"conditionNote\":\"Phòng mới\",\"assetTemplate\":\"NONE\",\"rooms\":["
            + "{\"roomNumber\":\"P105\",\"electricMeter\":100,\"waterMeter\":20},"
            + "{\"roomNumber\":\"p101\",\"electricMeter\":110,\"waterMeter\":25}]}";

        mockMvc.perform(post("/api/properties/" + PROPERTY + "/rooms/batch").header("X-User-Id", OWNER)
                .contentType("application/json").content(request))
            .andExpect(status().isConflict());

        assertTrue(rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P105").isEmpty());
    }

    @Test
    @DisplayName("Duplicate room in batch leaves every room untouched")
    void duplicateBulkCreateIsAtomicBeforeWrites() throws Exception {
        mockMvc.perform(post("/properties/" + PROPERTY + "/rooms/batch").header("X-User-Id", OWNER)
                .param("roomNumbers", "P105, p105").param("floor", "1").param("basePrice", "3500000")
                .param("maxOccupants", "2").param("conditionNote", "Phòng mới").param("assetTemplate", "NONE")
                .param("electricMeters", "10", "11").param("waterMeters", "2", "3"))
            .andExpect(flash().attribute("errorMessage",
                "Số phòng 'p105' đã tồn tại hoặc bị lặp; không có phòng nào được tạo."));

        assertTrue(rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P105").isEmpty());
    }

    @Test
    @DisplayName("Bulk create refuses to exceed the declared room count")
    void bulkCreateRespectsDeclaredRoomCapacity() throws Exception {
        mockMvc.perform(post("/properties/" + PROPERTY + "/rooms/batch").header("X-User-Id", OWNER)
                .param("roomNumbers", "P105-P111").param("floor", "1").param("basePrice", "3500000")
                .param("maxOccupants", "2").param("conditionNote", "Phòng mới").param("assetTemplate", "NONE")
                .param("electricMeters", "1", "1", "1", "1", "1", "1", "1")
                .param("waterMeters", "1", "1", "1", "1", "1", "1", "1"))
            .andExpect(flash().attribute("errorMessage", "Số phòng tạo mới vượt tổng số phòng đã khai báo (10)."));
        assertTrue(rooms.findByPropertyIdAndRoomNumber(PROPERTY, "P105").isEmpty());
    }

    @Test
    @DisplayName("Staff without permission is forbidden; owner-granted staff can create rooms, technicians cannot")
    void staffRoomPermissionIsEnforcedByTheApi() throws Exception {
        String request = "{\"roomNumber\":\"P109\",\"floor\":1,\"areaSqm\":20,\"basePrice\":3500000,"
            + "\"maxOccupants\":2,\"electricMeter\":1,\"waterMeter\":2,\"conditionNote\":\"Mới\","
            + "\"noAssets\":true,\"assets\":[]}";
        String url = "/api/properties/" + PROPERTY + "/rooms";

        mockMvc.perform(post(url).header("X-User-Id", OWNER).contentType("application/json")
                .content("{\"roomNumber\":\"P109\",\"floor\":1,\"areaSqm\":20,"
                    + "\"basePrice\":3500000,\"maxOccupants\":2}"))
            .andExpect(status().isBadRequest());

        assertFalse(assignments.find(UUID.fromString(STAFF), PROPERTY).orElseThrow().canManageRoomsAndAssets());
        String propertyPage = mockMvc.perform(get("/properties/" + PROPERTY).header("X-User-Id", STAFF))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(propertyPage.contains("Thêm phòng kèm biên bản hiện trạng"));
        String roomPage = mockMvc.perform(get("/rooms/" + DataSeeder.ROOM_104_ID).header("X-User-Id", STAFF))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(roomPage.contains("id=\"batchAssetModal\""));

        mockMvc.perform(post(url).header("X-User-Id", STAFF).contentType("application/json").content(request))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(url).header("X-User-Id", TECH).contentType("application/json").content(request))
            .andExpect(status().isForbidden());

        StaffPropertyAssignment old = assignments.find(UUID.fromString(STAFF), PROPERTY).orElseThrow();
        assignments.save(new StaffPropertyAssignment(old.id(), old.staffUserId(), old.propertyId(),
            old.canCollectPayment(), old.canManageContracts(), old.canViewDocuments(), true, old.assignedAt()));
        mockMvc.perform(post(url).header("X-User-Id", STAFF).contentType("application/json").content(request))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.roomNumber").value("P109"));
    }

    @Test
    @DisplayName("First check-in form prefills the latest room condition baseline")
    void firstCheckInUsesRoomConditionBaseline() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        conditions.save(new RoomConditionSnapshot(snapshotId, DataSeeder.ROOM_101_ID,
            new BigDecimal("55.12"), new BigDecimal("9.50"), "Tường và sàn còn tốt", null, null,
            Instant.now(), LocalDemoAuthenticationAdapter.OWNER_1_ID));
        conditions.saveItems(snapshotId, List.of(new RoomConditionItem(UUID.randomUUID(), snapshotId,
            UUID.randomUUID(), 0, "Giường đơn", 2, AssetStatus.GOOD, "Còn mới", new BigDecimal("1500000"))));

        String page = mockMvc.perform(get("/contracts/" + DataSeeder.CONTRACT_1_ID + "/handover").header("X-User-Id", OWNER))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("name=\"initialElectricMeter\""));
        assertTrue(page.contains("value=\"55.12\""));
        assertTrue(page.contains("value=\"9.50\""));
        assertTrue(page.contains("Tường và sàn còn tốt"));
        assertTrue(page.contains("Tài sản hiện trạng được điền sẵn"));
        assertTrue(page.contains("Giường đơn"));
        assertFalse(page.contains("value=\"0\""));
    }
}
