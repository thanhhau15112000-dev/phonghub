package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoicePaymentRecordRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryNotificationRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.application.port.in.DemoActorPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.service.MaintenanceApprovalService;
import com.phonghub.application.service.MaintenanceApprovalService.ProcessResult;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Luồng đầy đủ từ khi admin tạo chủ trọ tới khi tenant trả phòng, chạy trên chính các API và trang của ứng dụng:
 * thiết lập, nhận phòng, báo hỏng hai nhánh (hao mòn / lỗi người thuê), thanh toán, trả phòng kết thúc sớm.
 */
class FullLifecycleE2EIntegrationTest extends SeededWebTest {

    private static final String ADMIN = com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter.ADMIN_ID.toString();
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryRoomRepository rooms;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryInvoicePaymentRecordRepository paymentRecords;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryMaintenanceTicketRepository tickets;
    @Autowired private InMemoryNotificationRepository notifications;
    @Autowired private MaintenanceApprovalService approvalService;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("demoActorPort") private DemoActorPort demoActors;

    // actors
    private String owner;
    private String staff;
    private String technician;
    private String tenant;
    private String tenantUsername;
    // data
    private String propertyId;
    private String roomId;
    private String tenantProfileId;
    private String contractId;
    private final LocalDate today = LocalDate.now();

    // ------------------------------------------------------------------ helpers

    /** Chế độ demo chỉ nhận ra các tài khoản mẫu qua X-User-Id; đăng ký tài khoản vừa tạo để đóng vai họ. */
    private String register(String userId, String username, UserRole role) {
        demoActors.setCurrentUser(new CurrentUser(UUID.fromString(userId), username, username, role));
        return userId;
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.id");
    }

    private String json(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private ResultActions postJson(String url, String user, String body) throws Exception {
        return mockMvc.perform(post(url).header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions postNothing(String url, String user) throws Exception {
        return mockMvc.perform(post(url).header("X-User-Id", user));
    }

    private void addPhotos(String url, String user) throws Exception {
        for (String kind : new String[] {"ROOM", "METER_ELECTRIC", "METER_WATER"}) {
            mockMvc.perform(multipart(url + "/photos")
                    .file(new MockMultipartFile("file", "p.png", "image/png", PNG)).param("kind", kind)
                    .header("X-User-Id", user))
                .andExpect(status().isCreated());
        }
    }

    private void uploadTenantCccd() throws Exception {
        mockMvc.perform((MockMultipartHttpServletRequestBuilder) multipart("/api/properties/" + propertyId + "/tenants/"
                + tenantProfileId + "/documents/CCCD").with(request -> {
                request.setMethod("PUT");
                return request;
            }).file(new MockMultipartFile("front", "f.png", "image/png", PNG)).header("X-User-Id", tenant))
            .andExpect(status().isOk());
    }

    private Invoice invoice(InvoiceType type) {
        return invoices.findByContractId(UUID.fromString(contractId)).stream()
            .filter(i -> i.getType() == type && i.getStatus() != InvoiceStatus.VOIDED).findFirst().orElseThrow();
    }

    private Contract contract() {
        return contracts.findById(UUID.fromString(contractId)).orElseThrow();
    }

    private MaintenanceTicket ticket(String ticketId) {
        return tickets.findById(UUID.fromString(ticketId)).orElseThrow();
    }

    private String reportTicket(String title, String cause) throws Exception {
        return id(json(postJson("/api/maintenance", tenant, "{\"roomId\":\"" + roomId + "\",\"title\":\"" + title
            + "\",\"description\":\"" + title + " ở phòng\",\"priority\":\"MEDIUM\",\"setRoomMaintenance\":false,"
            + "\"cause\":\"" + cause + "\"}").andExpect(status().isCreated())));
    }

    // ------------------------------------------------------------------ the flow

    @Test
    @DisplayName("Flow: setup -> check-in -> payment -> wear repair -> tenant-fault repair -> early check-out")
    void fullRentalLifecycle() throws Exception {
        setupAccountsPropertyAndRooms();
        contractAndCheckIn();
        firstCyclePayment();
        wearRepair();
        tenantFaultRepair();
        earlyCheckOutAfterPaidCycle();
    }

    // 1. Thiết lập: admin tạo chủ trọ, chủ trọ tạo nhà trọ, admin duyệt, chủ trọ tạo phòng, tài khoản tenant, phân quyền staff
    private void setupAccountsPropertyAndRooms() throws Exception {
        owner = register(id(json(postJson("/api/admin/users", ADMIN, user("e2e.owner", "OWNER", "0911000001", null))
            .andExpect(status().isCreated()))), "e2e.owner", UserRole.OWNER);
        staff = register(id(json(postJson("/api/admin/users", ADMIN, user("e2e.staff", "STAFF", "0911000002", null))
            .andExpect(status().isCreated()))), "e2e.staff", UserRole.STAFF);
        technician = register(id(json(postJson("/api/admin/users", ADMIN,
            user("e2e.tech", "TECHNICIAN", "0911000003", null)).andExpect(status().isCreated()))), "e2e.tech",
            UserRole.TECHNICIAN);
        postJson("/api/admin/users", owner, user("e2e.bad", "OWNER", "0911000009", null)).andExpect(status().isForbidden());

        propertyId = id(json(postJson("/api/properties", owner,
            "{\"name\":\"Nhà trọ E2E\",\"address\":\"1 Đường Thử, Q7\",\"description\":\"\",\"totalRooms\":10}")
            .andExpect(status().isCreated())));
        postJson("/api/admin/properties/" + propertyId + "/verify", owner, "{}").andExpect(status().isForbidden());
        postNothing("/api/admin/properties/" + propertyId + "/verify", ADMIN).andExpect(status().isOk());

        // Chủ trọ phân quyền nhân viên (thu tiền, hợp đồng/kiểm tra, giấy tờ, phòng) và kỹ thuật viên
        postJson("/api/properties/" + propertyId + "/staff", owner,
            "{\"usernameOrEmail\":\"e2e.staff\",\"canCollectPayment\":true,\"canManageContracts\":true,"
                + "\"canViewDocuments\":true,\"canManageRoomsAndAssets\":true}").andExpect(status().isCreated());
        postJson("/api/properties/" + propertyId + "/staff", owner, "{\"usernameOrEmail\":\"e2e.tech\"}")
            .andExpect(status().isCreated());

        // Phòng đầu tiên có hiện trạng ban đầu: chỉ số điện nước, tài sản
        roomId = id(json(postJson("/api/properties/" + propertyId + "/rooms", owner,
            "{\"roomNumber\":\"P101\",\"floor\":1,\"areaSqm\":25,\"basePrice\":3500000,\"maxOccupants\":2,"
                + "\"electricMeter\":100,\"waterMeter\":10,\"conditionNote\":\"Phòng mới sơn\",\"noAssets\":false,"
                + "\"assetTemplate\":\"MANUAL\",\"assets\":[{\"name\":\"Giường\",\"quantity\":1,\"status\":\"GOOD\"},"
                + "{\"name\":\"Tủ quần áo\",\"quantity\":1,\"status\":\"GOOD\"},{\"name\":\"Điều hòa\",\"quantity\":1,"
                + "\"status\":\"GOOD\",\"estimatedValue\":6000000}]}").andExpect(status().isCreated())));
        // Tạo hàng loạt hai phòng cùng lúc; trùng số phòng thì không lưu phòng nào
        postJson("/api/properties/" + propertyId + "/rooms/batch", owner,
            "{\"floor\":2,\"areaSqm\":20,\"basePrice\":3000000,\"maxOccupants\":2,\"conditionNote\":\"Mới bàn giao\","
                + "\"assetTemplate\":\"BASIC\",\"rooms\":[{\"roomNumber\":\"P201\",\"electricMeter\":0,\"waterMeter\":0},"
                + "{\"roomNumber\":\"P101\",\"electricMeter\":0,\"waterMeter\":0}]}").andExpect(status().isConflict());
        postJson("/api/properties/" + propertyId + "/rooms/batch", owner,
            "{\"floor\":2,\"areaSqm\":20,\"basePrice\":3000000,\"maxOccupants\":2,\"conditionNote\":\"Mới bàn giao\","
                + "\"assetTemplate\":\"BASIC\",\"rooms\":[{\"roomNumber\":\"P201\",\"electricMeter\":0,\"waterMeter\":0},"
                + "{\"roomNumber\":\"P202\",\"electricMeter\":0,\"waterMeter\":0}]}").andExpect(status().isCreated());
        mockMvc.perform(get("/api/properties/" + propertyId + "/rooms").header("X-User-Id", owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));

        // Tài khoản người thuê tạo cùng hồ sơ (CCCD bắt buộc, một CCCD một tài khoản)
        String created = json(postJson("/api/properties/" + propertyId + "/tenants", owner,
            "{\"fullName\":\"Lê Thị Khách\",\"identityCardNumber\":\"079201234567\",\"phone\":\"0922000001\","
                + "\"email\":\"e2e.tenant@test.local\",\"permanentAddress\":\"Cần Thơ\"}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.accountCreated").value(true))
            .andExpect(jsonPath("$.temporaryPassword").isNotEmpty()));
        tenantProfileId = id(created);
        tenantUsername = JsonPath.read(created, "$.username");
        tenant = register(JsonPath.read(created, "$.userId"), tenantUsername, UserRole.TENANT);
        assertNotNull(tenantUsername);
        postJson("/api/properties/" + propertyId + "/tenants", staff,
            "{\"fullName\":\"Trùng CCCD\",\"identityCardNumber\":\"079201234567\",\"phone\":\"0922000002\","
                + "\"email\":\"dup@test.local\"}").andExpect(status().isConflict());
    }

    private static String user(String username, String role, String phone, String idCard) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.local\",\"fullName\":\"" + username
            + "\",\"phone\":\"" + phone + "\",\"role\":\"" + role + "\""
            + (idCard == null ? "" : ",\"identityCardNumber\":\"" + idCard + "\"") + "}";
    }

    // 2. Nhận phòng: tạo hợp đồng nháp, lập biên bản check-in có tài sản, người thuê xác nhận, duyệt thì hợp đồng có hiệu lực
    private void contractAndCheckIn() throws Exception {
        contractId = id(json(postJson("/api/contracts", staff,
            "{\"propertyId\":\"" + propertyId + "\",\"roomId\":\"" + roomId + "\",\"primaryTenantId\":\""
                + tenantProfileId + "\",\"depositAmount\":7000000,\"rentAmount\":3500000,\"startDate\":\"" + today
                + "\",\"endDate\":\"" + today.plusMonths(12).minusDays(1) + "\",\"paymentDay\":5}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))));
        assertEquals(RoomStatus.AVAILABLE, rooms.findById(UUID.fromString(roomId)).orElseThrow().getStatus());

        // Không có đường nào kích hoạt khi chưa có biên bản check-in được duyệt
        postNothing("/api/contracts/" + contractId + "/activate", owner).andExpect(status().isConflict());

        String checkIn = "/api/contracts/" + contractId + "/handover";
        postJson("/api/contracts/" + contractId + "/check-in", staff,
            "{\"initialElectricMeter\":100,\"initialWaterMeter\":10,\"roomCondition\":\"Phòng sạch, đủ tài sản\","
                + "\"keysProvided\":\"2 chìa khóa\"}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("CHECK_IN"))
            .andExpect(jsonPath("$.items.length()").value(3));
        addPhotos(checkIn, staff);
        postNothing(checkIn + "/send", staff).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_TENANT"));
        // Người thuê cần có ảnh CCCD trước khi xác nhận; chủ trọ không xác nhận thay
        postNothing(checkIn + "/confirm", tenant).andExpect(status().isBadRequest());
        postNothing(checkIn + "/confirm", owner).andExpect(status().isForbidden());
        uploadTenantCccd();
        postNothing(checkIn + "/confirm", tenant).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertEquals(ContractStatus.DRAFT, contract().getStatus(), "chưa duyệt thì hợp đồng chưa có hiệu lực");

        postNothing(checkIn + "/approve", staff).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertEquals(ContractStatus.ACTIVE, contract().getStatus());
        assertEquals(RoomStatus.OCCUPIED, rooms.findById(UUID.fromString(roomId)).orElseThrow().getStatus());
        assertNotNull(contract().getActivatedAt(), "lưu ngày kích hoạt để tính chu kỳ");

        Invoice first = invoice(InvoiceType.RENT);
        assertEquals(1, first.getCycleNumber(), "kỳ đầu tạo ngay khi kích hoạt");
        assertEquals(today, first.getCycleStartDate());
        assertEquals(0, new BigDecimal("3500000").compareTo(first.getTotalAmount()));
        assertEquals(today.plusDays(7), first.getDueDate(), "hạn thanh toán = ngày bắt đầu kỳ + 7 ngày");
    }

    // 3. Thanh toán kỳ đầu ngoài cổng: người thuê báo, nhân viên thu tiền xác nhận
    private void firstCyclePayment() throws Exception {
        Invoice first = invoice(InvoiceType.RENT);
        String claim = json(postJson("/api/invoices/" + first.getId() + "/payment-claims", tenant,
            "{\"amount\":3500000,\"method\":\"CASH\",\"note\":\"Nộp tiền mặt cho nhân viên\"}")
            .andExpect(status().isCreated()));
        postNothing("/api/payment-claims/" + id(claim) + "/confirm", technician).andExpect(status().isForbidden());
        postNothing("/api/payment-claims/" + id(claim) + "/confirm", staff).andExpect(status().isOk());
        assertEquals(InvoiceStatus.PAID, invoices.findById(first.getId()).orElseThrow().getStatus());
    }

    // 4. Báo hỏng nhánh A (hao mòn): chủ trọ duyệt, chủ trọ chịu 100%, tenant không bị tính phí
    private void wearRepair() throws Exception {
        String id = id(json(mockMvc.perform(multipart("/api/maintenance")
                .file(new MockMultipartFile("files", "hong.png", "image/png", PNG))
                .param("roomId", roomId).param("title", "Bóng đèn cháy").param("description", "Bóng đèn phòng khách cháy")
                .param("priority", "LOW").param("cause", "NATURAL_WEAR").header("X-User-Id", tenant))
            .andExpect(status().isCreated())));
        mockMvc.perform(get("/api/maintenance/" + id + "/attachments").header("X-User-Id", tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        postJson("/api/maintenance/" + id + "/assign", staff, "{\"technicianId\":\"" + technician + "\"}")
            .andExpect(status().isConflict());
        postJson("/api/maintenance/" + id + "/branch", staff, "{\"branch\":\"OWNER\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_OWNER_APPROVAL"));
        postNothing("/api/maintenance/" + id + "/owner-approve", staff).andExpect(status().isForbidden());
        postNothing("/api/maintenance/" + id + "/owner-approve", owner).andExpect(status().isOk());
        postJson("/api/maintenance/" + id + "/assign", staff, "{\"technicianId\":\"" + technician + "\"}")
            .andExpect(status().isOk());
        postNothing("/api/maintenance/" + id + "/accept", technician).andExpect(status().isOk());
        postJson("/api/maintenance/" + id + "/resolve", technician,
            "{\"resolutionNotes\":\"Đã thay bóng\",\"repairCost\":50000,\"releaseRoomToAvailable\":false}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        assertTrue(invoices.findActiveByTicketId(UUID.fromString(id)).isEmpty(), "hao mòn: tenant không phải trả");
        postNothing("/api/maintenance/" + id + "/verify", tenant).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("VERIFIED"));
        assertEquals(RoomStatus.OCCUPIED, rooms.findById(UUID.fromString(roomId)).orElseThrow().getStatus());

        // Chủ trọ im lặng quá 3 ngày: job tự chuyển cho nhân viên/kỹ thuật viên xử lý
        String late = reportTicket("Vòi sen yếu", "NATURAL_WEAR");
        postJson("/api/maintenance/" + late + "/branch", staff, "{\"branch\":\"OWNER\"}").andExpect(status().isOk());
        assertEquals(ProcessResult.ESCALATED, approvalService.processTicket(UUID.fromString(late),
            ticket(late).getApprovalDueAt().plus(Duration.ofMinutes(1))));
        assertEquals(MaintenanceStatus.REPORTED, ticket(late).getStatus());
        postJson("/api/maintenance/" + late + "/assign", staff, "{\"technicianId\":\"" + technician + "\"}")
            .andExpect(status().isOk());
    }

    // 5. Báo hỏng nhánh B (lỗi người thuê): báo giá có linh kiện, thương lượng, phê duyệt, sửa, hóa đơn, thanh toán
    private void tenantFaultRepair() throws Exception {
        String id = reportTicket("Vỡ kính cửa sổ", "TENANT_USAGE");
        postJson("/api/maintenance/" + id + "/branch", staff, "{\"branch\":\"TENANT\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REPORTED"));
        postJson("/api/maintenance/" + id + "/assign", owner, "{\"technicianId\":\"" + technician + "\"}")
            .andExpect(status().isOk());
        postNothing("/api/maintenance/" + id + "/accept", technician).andExpect(status().isConflict());

        postJson("/api/maintenance/" + id + "/quote-versions", technician,
            "{\"lines\":[{\"name\":\"Kính 5mm\",\"quantity\":2,\"unitPrice\":150000},{\"name\":\"Ron cao su\",\"quantity\":1,"
                + "\"unitPrice\":20000}],\"laborAmount\":100000,\"note\":\"Thay kính\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_TENANT_APPROVAL"));
        postJson("/api/maintenance/" + id + "/quote/negotiate", tenant, "{\"note\":\"Bỏ khoản ron cao su\"}")
            .andExpect(status().isOk());
        postJson("/api/maintenance/" + id + "/quote-versions", technician,
            "{\"lines\":[{\"name\":\"Kính 5mm\",\"quantity\":2,\"unitPrice\":150000}],\"laborAmount\":100000}")
            .andExpect(status().isOk());
        postNothing("/api/maintenance/" + id + "/quote/approve", tenant).andExpect(status().isOk());
        assertEquals(0, new BigDecimal("400000").compareTo(ticket(id).getApprovedQuoteTotal()));

        postNothing("/api/maintenance/" + id + "/accept", technician).andExpect(status().isOk());
        postJson("/api/maintenance/" + id + "/resolve", technician,
            "{\"resolutionNotes\":\"Đã thay kính\",\"repairCost\":450000,\"releaseRoomToAvailable\":false}")
            .andExpect(status().isConflict());
        postJson("/api/maintenance/" + id + "/resolve", technician,
            "{\"resolutionNotes\":\"Đã thay kính\",\"repairCost\":400000,\"releaseRoomToAvailable\":false}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
        Invoice fee = invoices.findActiveByTicketId(UUID.fromString(id)).orElseThrow();
        assertEquals(InvoiceType.MAINTENANCE, fee.getType());
        assertEquals(0, new BigDecimal("400000").compareTo(fee.getTotalAmount()), "phí khớp báo giá đã duyệt");

        // Tenant thanh toán, chủ trọ xác nhận: phiếu hoàn tất rồi tenant xác nhận kết quả
        String claim = json(postJson("/api/invoices/" + fee.getId() + "/payment-claims", tenant,
            "{\"amount\":400000,\"method\":\"BANK_TRANSFER\"}").andExpect(status().isCreated()));
        postNothing("/api/payment-claims/" + id(claim) + "/confirm", owner).andExpect(status().isOk());
        assertEquals(MaintenanceStatus.RESOLVED, ticket(id).getStatus());
        postNothing("/api/maintenance/" + id + "/verify", tenant).andExpect(status().isOk());
        assertEquals(MaintenanceStatus.VERIFIED, ticket(id).getStatus());
    }

    // 6. Trả phòng kết thúc sớm: mở check-out, kiểm tài sản, gửi người thuê xác nhận (lập phí hủy giữa kỳ)
    private String startEarlyCheckOut(boolean damaged) throws Exception {
        // Không còn đường kết thúc trực tiếp
        postJson("/api/contracts/" + contractId + "/terminate?reason=Thử", owner, "{}").andExpect(status().isConflict());

        mockMvc.perform(post("/contracts/" + contractId + "/terminate").header("X-User-Id", staff)
                .param("reason", "Người thuê chuyển công tác"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        String checkOut = "/api/contracts/" + contractId + "/check-out";
        assertEquals(ContractStatus.ACTIVE, contract().getStatus(), "chưa duyệt check-out thì chưa kết thúc");

        // Kiểm tra tài sản: nếu điều hòa hỏng thì khấu trừ 100.000 đ
        String details = json(mockMvc.perform(get(checkOut).header("X-User-Id", staff)).andExpect(status().isOk()));
        List<String> itemIds = JsonPath.read(details, "$.items[*].id");
        List<String> names = JsonPath.read(details, "$.items[*].assetName");
        assertEquals(3, itemIds.size(), "biên bản check-out có đủ 3 tài sản của phòng");
        if (damaged) {
            String airConditioner = itemIds.get(names.indexOf("Điều hòa"));
            mockMvc.perform(patch(checkOut + "/items/" + airConditioner).header("X-User-Id", staff)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"DAMAGED\",\"conditionNote\":\"Hỏng remote\",\"deductionAmount\":100000}"))
                .andExpect(status().isOk());
        }
        addPhotos(checkOut, staff);
        postNothing(checkOut + "/send", staff).andExpect(status().isOk());
        postNothing(checkOut + "/confirm", tenant).andExpect(status().isOk());
        return checkOut;
    }

    private void assertContractEndedAndHistoryKept(RoomStatus expectedRoomStatus) throws Exception {
        assertEquals(ContractStatus.TERMINATED, contract().getStatus());
        assertEquals(expectedRoomStatus, rooms.findById(UUID.fromString(roomId)).orElseThrow().getStatus());
        assertTrue(handovers.find(UUID.fromString(contractId), com.phonghub.domain.model.HandoverType.CHECK_IN).isPresent());
        assertTrue(handovers.find(UUID.fromString(contractId), com.phonghub.domain.model.HandoverType.CHECK_OUT).isPresent());
        mockMvc.perform(get("/api/contracts/" + contractId).header("X-User-Id", owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TERMINATED"));
        // Người thuê đã trả phòng không còn báo hỏng cho phòng này
        postJson("/api/maintenance", tenant, "{\"roomId\":\"" + roomId + "\",\"title\":\"Sau trả phòng\","
            + "\"description\":\"x\",\"priority\":\"LOW\",\"setRoomMaintenance\":false,\"cause\":\"UNKNOWN\"}")
            .andExpect(status().isForbidden());
        assertFalse(notifications.findAll().isEmpty());
    }

    // 6a. Chu kỳ đang ở đã trả đủ, phòng trả sạch: phí hủy 50% bù bằng tiền đã trả, phần chênh 50% hoàn lại, phòng trống
    private void earlyCheckOutAfterPaidCycle() throws Exception {
        String checkOut = startEarlyCheckOut(false);

        Invoice cancellation = invoice(InvoiceType.CANCELLATION);
        assertEquals(0, new BigDecimal("1750000").compareTo(cancellation.getTotalAmount()),
            "phí hủy = 50% tiền thuê chu kỳ đang ở");
        assertEquals(InvoiceStatus.PAID, cancellation.getStatus(), "kỳ đã trả đủ nên phí hủy đã được thanh toán");
        assertEquals(0, new BigDecimal("1750000").compareTo(cancellation.getSettlementCreditAmount()));
        assertEquals(0, new BigDecimal("1750000").compareTo(contract().getSettlementCreditAmount()),
            "phần đã trả vượt phí hủy được ghi nhận để hoàn lại");

        postNothing(checkOut + "/approve", technician).andExpect(status().isForbidden());
        postNothing(checkOut + "/approve", staff).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
        assertContractEndedAndHistoryKept(RoomStatus.AVAILABLE);
        // Hoàn lại = cọc 7.000.000 + phần chênh hoàn 1.750.000 = 8.750.000
        mockMvc.perform(get("/contracts/" + contractId + "/check-out").header("X-User-Id", owner))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("8.750.000")));
    }

    // 6b. Chưa trả kỳ đang ở, tài sản hỏng: phí hủy còn nợ, chặn duyệt tới khi trừ vào cọc; phòng chờ sửa rồi mới trống
    private void earlyCheckOutWithDebtOffsetFromDeposit() throws Exception {
        String checkOut = startEarlyCheckOut(true);

        Invoice cancellation = invoice(InvoiceType.CANCELLATION);
        assertEquals(0, new BigDecimal("1750000").compareTo(cancellation.getTotalAmount()));
        assertEquals(InvoiceStatus.ISSUED, cancellation.getStatus());

        postNothing(checkOut + "/approve", staff).andExpect(status().isConflict());
        assertEquals(ContractStatus.ACTIVE, contract().getStatus());
        postJson(checkOut + "/approve", staff, "{\"finalDeduction\":100000,\"depositOffsetInvoiceIds\":[\""
            + cancellation.getId() + "\"]}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        assertContractEndedAndHistoryKept(RoomStatus.MAINTENANCE);
        assertEquals(InvoiceStatus.PAID, invoices.findById(cancellation.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("1750000").compareTo(contract().getDepositAppliedAmount()), "cọc trừ phí hủy");
        assertFalse(paymentRecords.findByInvoiceId(cancellation.getId()).isEmpty(), "cấn trừ cọc được ghi vào sổ thanh toán");
        // Hoàn lại = cọc 7.000.000 - phí hủy trừ vào cọc 1.750.000 - khấu trừ tài sản 100.000 = 5.150.000
        mockMvc.perform(get("/contracts/" + contractId + "/check-out").header("X-User-Id", owner))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("5.150.000")));
        // Sửa xong phần tài sản hỏng thì chủ trọ đưa phòng về trống để cho thuê tiếp
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/rooms/" + roomId + "/status").header("X-User-Id", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"AVAILABLE\"}"))
            .andExpect(status().isOk());
        assertEquals(RoomStatus.AVAILABLE, rooms.findById(UUID.fromString(roomId)).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Flow: unpaid cycle at early check-out -> the cancellation fee is deducted from the deposit")
    void earlyCheckOutOffsetsDepositWhenCycleUnpaid() throws Exception {
        setupAccountsPropertyAndRooms();
        contractAndCheckIn();
        earlyCheckOutWithDebtOffsetFromDeposit();
    }
}
