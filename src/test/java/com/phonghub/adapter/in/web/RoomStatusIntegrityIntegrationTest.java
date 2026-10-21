package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Trạng thái phòng luôn khớp với hợp đồng: phiếu bảo trì không đưa phòng có người ở sang Bảo trì/Trống, người thuê chỉ
 * báo hỏng phòng của mình, và dữ liệu cũ (phòng có người ở đang Bảo trì) vẫn kết thúc được hợp đồng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomStatusIntegrityIntegrationTest {

    private static final String ADMIN = LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String TENANT = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;

    @BeforeEach
    void resetData() {
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();
        DataSeeder.seedAll(
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String userId) {
        return builder.header("X-User-Id", userId);
    }

    private MockHttpServletRequestBuilder newTicket(String userId, UUID roomId, String title, boolean setMaintenance) {
        return asUser(post("/maintenance"), userId)
            .param("roomId", roomId.toString())
            .param("title", title)
            .param("description", "Mô tả " + title)
            .param("cause", "NATURAL_WEAR")
            .param("setRoomMaintenance", String.valueOf(setMaintenance));
    }

    private RoomStatus roomStatus(UUID roomId) {
        return roomRepository.findById(roomId).orElseThrow().getStatus();
    }

    private long ticketsTitled(String title) {
        return ticketRepository.findAll().stream().filter(t -> t.getTitle().equals(title)).count();
    }

    @Test
    @DisplayName("Phòng có người ở: chọn chuyển sang bảo trì bị từ chối, không tạo phiếu; bỏ chọn thì tạo phiếu, phòng vẫn Đang thuê")
    void occupiedRoomStaysOccupiedWhenTicketCreated() throws Exception {
        mockMvc.perform(newTicket(OWNER_1, DataSeeder.ROOM_101_ID, "Máy lạnh không mát", true))
            .andExpect(flash().attribute("errorMessage", containsString("không chuyển sang bảo trì được")));
        assertEquals(0, ticketsTitled("Máy lạnh không mát"));
        assertEquals(RoomStatus.OCCUPIED, roomStatus(DataSeeder.ROOM_101_ID));

        mockMvc.perform(newTicket(OWNER_1, DataSeeder.ROOM_101_ID, "Máy lạnh không mát", false));
        assertEquals(1, ticketsTitled("Máy lạnh không mát"));
        assertEquals(RoomStatus.OCCUPIED, roomStatus(DataSeeder.ROOM_101_ID));
    }

    @Test
    @DisplayName("Phòng trống: chọn chuyển sang bảo trì thì phòng chuyển sang Bảo trì")
    void availableRoomCanBePutUnderMaintenance() throws Exception {
        mockMvc.perform(newTicket(OWNER_1, DataSeeder.ROOM_104_ID, "Sơn lại tường", true));
        assertEquals(1, ticketsTitled("Sơn lại tường"));
        assertEquals(RoomStatus.MAINTENANCE, roomStatus(DataSeeder.ROOM_104_ID));
    }

    @Test
    @DisplayName("Người thuê không báo hỏng được phòng khác và không đổi trạng thái phòng của mình")
    void tenantReportsOnlyOwnRoomWithoutChangingStatus() throws Exception {
        mockMvc.perform(newTicket(TENANT, DataSeeder.ROOM_104_ID, "Phòng bên cạnh", true))
            .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(0, ticketsTitled("Phòng bên cạnh"));
        assertEquals(RoomStatus.AVAILABLE, roomStatus(DataSeeder.ROOM_104_ID));

        mockMvc.perform(newTicket(TENANT, DataSeeder.ROOM_101_ID, "Vòi nước rỉ", true));
        assertEquals(1, ticketsTitled("Vòi nước rỉ"));
        assertEquals(RoomStatus.OCCUPIED, roomStatus(DataSeeder.ROOM_101_ID));
    }

    @Test
    @DisplayName("Đổi trạng thái tay: phòng có hợp đồng không đổi được; không ai tự đặt Đang thuê; phòng trống vào/ra bảo trì được")
    void manualStatusChangesRespectContracts() throws Exception {
        mockMvc.perform(asUser(post("/rooms/" + DataSeeder.ROOM_101_ID + "/status"), OWNER_1)
                .param("status", "MAINTENANCE"))
            .andExpect(flash().attribute("errorMessage", containsString("đang có người ở theo hợp đồng")));
        assertEquals(RoomStatus.OCCUPIED, roomStatus(DataSeeder.ROOM_101_ID));

        mockMvc.perform(asUser(post("/rooms/" + DataSeeder.ROOM_104_ID + "/status"), OWNER_1)
                .param("status", "OCCUPIED"))
            .andExpect(flash().attribute("errorMessage", containsString("chỉ chuyển sang Đang thuê khi kích hoạt")));
        assertEquals(RoomStatus.AVAILABLE, roomStatus(DataSeeder.ROOM_104_ID));

        mockMvc.perform(asUser(post("/rooms/" + DataSeeder.ROOM_104_ID + "/status"), OWNER_1)
            .param("status", "MAINTENANCE"));
        assertEquals(RoomStatus.MAINTENANCE, roomStatus(DataSeeder.ROOM_104_ID));
        mockMvc.perform(asUser(post("/rooms/" + DataSeeder.ROOM_104_ID + "/status"), OWNER_1)
            .param("status", "AVAILABLE"));
        assertEquals(RoomStatus.AVAILABLE, roomStatus(DataSeeder.ROOM_104_ID));
    }

    @Test
    @DisplayName("Dữ liệu cũ: phòng có người ở đang Bảo trì vẫn kết thúc được hợp đồng, phòng giữ Bảo trì")
    void legacyMaintenanceRoomCanStillTerminate() throws Exception {
        Room room = roomRepository.findById(DataSeeder.ROOM_101_ID).orElseThrow();
        room.transitionTo(RoomStatus.MAINTENANCE);
        roomRepository.save(room);

        mockMvc.perform(asUser(post("/contracts/" + DataSeeder.CONTRACT_1_ID + "/terminate"), OWNER_1))
            .andExpect(flash().attribute("successMessage", containsString("Bảo trì")));

        assertEquals(ContractStatus.TERMINATED,
            contractRepository.findById(DataSeeder.CONTRACT_1_ID).orElseThrow().getStatus());
        assertEquals(RoomStatus.MAINTENANCE, roomStatus(DataSeeder.ROOM_101_ID));
    }

    @Test
    @DisplayName("Dữ liệu cũ: xử lý xong phiếu không đưa phòng còn người ở về Trống")
    void resolvingDoesNotReleaseResidingRoom() throws Exception {
        Contract contract = Contract.create(DataSeeder.PROP_1_ID, DataSeeder.ROOM_102_ID, UUID.randomUUID(),
            new BigDecimal("3500000"), new BigDecimal("3500000"), LocalDate.now().minusMonths(1),
            LocalDate.now().plusMonths(11), 5);
        contract.activate();
        contractRepository.save(contract);

        mockMvc.perform(asUser(post("/maintenance/" + DataSeeder.TICKET_1_ID + "/resolve"), ADMIN)
                .param("resolutionNotes", "Đã thay vòi nước")
                .param("releaseRoomToAvailable", "true"))
            .andExpect(flash().attribute("successMessage", containsString("giữ nguyên trạng thái")));

        assertTrue(ticketRepository.findById(DataSeeder.TICKET_1_ID).orElseThrow().isWorkFinished());
        assertEquals(MaintenanceStatus.RESOLVED,
            ticketRepository.findById(DataSeeder.TICKET_1_ID).orElseThrow().getStatus());
        assertEquals(RoomStatus.MAINTENANCE, roomStatus(DataSeeder.ROOM_102_ID));
    }
}
