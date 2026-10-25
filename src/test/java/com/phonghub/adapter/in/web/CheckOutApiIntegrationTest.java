package com.phonghub.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverItemRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverPhotoRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoicePaymentRecordRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterReadingRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyHouseRulesRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomAssetRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryServiceRateRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.application.port.in.ContractLifecycleUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.InvoicePaymentRecord;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.ServiceRate;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckOutApiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String CONTRACT = "/api/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String BODY = "{\"initialElectricMeter\":1500,\"initialWaterMeter\":40,"
        + "\"roomCondition\":\"Phòng trả lại\",\"keysProvided\":\"2 chìa khóa\"}";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditPort auditPort;
    @Autowired private InMemoryHandoverRepository handovers;
    @Autowired private InMemoryHandoverPhotoRepository photos;
    @Autowired private InMemoryHandoverItemRepository items;
    @Autowired private InMemoryPropertyHouseRulesRepository rules;
    @Autowired private InMemoryTenantDocumentRepository documents;
    @Autowired private InMemoryRoomAssetRepository roomAssets;
    @Autowired private InMemoryContractRepository contracts;
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryInvoicePaymentRecordRepository paymentRecords;
    @Autowired private InMemoryMeterReadingRepository meterReadings;
    @Autowired private InMemoryServiceRateRepository serviceRates;
    @Autowired private ContractLifecycleUseCase contractLifecycle;

    @BeforeEach
    void reset() {
        endSeededContractToday();
        handovers.clear();
        photos.clear();
        items.clear();
        rules.clear();
        documents.clear();
        roomAssets.clear();
        invoices.clear();
        paymentRecords.clear();
        meterReadings.clear();
        serviceRates.clear();
        ((InMemoryAuditAdapter) auditPort).clear();
    }

    private long count(String action) {
        return ((InMemoryAuditAdapter) auditPort).getRecordedEvents().stream()
            .filter(event -> event.action().equals(action)).count();
    }

    private void addPhotos(String path) throws Exception {
        for (String kind : new String[]{"ROOM", "METER_ELECTRIC", "METER_WATER"}) {
            mockMvc.perform(multipart(path + "/photos").file(new MockMultipartFile("file", "p.png", "image/png", PNG))
                    .param("kind", kind).header("X-User-Id", OWNER_1))
                .andExpect(status().isCreated());
        }
    }

    private void createAsset(String name) throws Exception {
        mockMvc.perform(post("/api/rooms/" + DataSeeder.ROOM_101_ID + "/assets").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"quantity\":1}"))
            .andExpect(status().isCreated());
    }

    /** Check-in đã được duyệt đầy đủ với hai tài sản. */
    private void approvedCheckIn() throws Exception {
        createAsset("Giường trả");
        createAsset("Tủ lạnh trả");
        mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        addPhotos(CONTRACT + "/handover");
        mockMvc.perform(post(CONTRACT + "/handover/send").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        mockMvc.perform((MockMultipartHttpServletRequestBuilder) multipart("/api/properties/" + DataSeeder.PROP_1_ID
                + "/tenants/" + DataSeeder.TENANT_RECORD_ID + "/documents/CCCD").with(request -> {
                request.setMethod("PUT");
                return request;
            }).file(new MockMultipartFile("front", "f.png", "image/png", PNG)).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
        mockMvc.perform(post(CONTRACT + "/handover/confirm").header("X-User-Id", TENANT_1)).andExpect(status().isOk());
        mockMvc.perform(post(CONTRACT + "/handover/approve").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
    }

    private String createCheckOut() throws Exception {
        return mockMvc.perform(post(CONTRACT + "/check-out").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("CHECK_OUT"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn().getResponse().getContentAsString();
    }

    private void sendAndConfirm() throws Exception {
        addPhotos(CONTRACT + "/check-out");
        mockMvc.perform(post(CONTRACT + "/check-out/send").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_TENANT"));
        mockMvc.perform(post(CONTRACT + "/check-out/confirm").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Check-out needs an approved check-in; only one check-out per contract")
    void checkOutPrerequisites() throws Exception {
        mockMvc.perform(post(CONTRACT + "/check-out").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());

        mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mockMvc.perform(post(CONTRACT + "/check-out").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());

        handovers.clear();
        photos.clear();
        items.clear();
        approvedCheckIn();
        createCheckOut();
        mockMvc.perform(post(CONTRACT + "/check-out").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Damaged item: owner sets the final deduction; approval ends the contract and sends the room to maintenance")
    void damagedItemSendsRoomToMaintenance() throws Exception {
        approvedCheckIn();
        String created = createCheckOut();
        String itemId = JsonPath.read(created, "$.items[0].id");
        String otherItemId = JsonPath.read(created, "$.items[1].id");

        mockMvc.perform(patch(CONTRACT + "/check-out/items/" + itemId).header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DAMAGED\",\"conditionNote\":\"Vỡ cánh\",\"deductionAmount\":300000}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DAMAGED"));
        mockMvc.perform(patch(CONTRACT + "/check-out/items/" + otherItemId).header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOOD\",\"deductionAmount\":50000}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch(CONTRACT + "/check-out/items/" + UUID.randomUUID()).header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOOD\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(patch(CONTRACT + "/handover/items/" + itemId).header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOOD\"}"))
            .andExpect(status().isConflict());

        sendAndConfirm();
        mockMvc.perform(patch(CONTRACT + "/check-out/items/" + itemId).header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOOD\"}"))
            .andExpect(status().isConflict());

        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", STAFF_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"finalDeduction\":250000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.proposedDeduction").value(300000))
            .andExpect(jsonPath("$.finalDeduction").value(250000));

        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("TERMINATED"));
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("MAINTENANCE"));
        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        assertEquals(1, count("CHECKOUT_APPROVED"));
        assertEquals(1, count("CHECKOUT_CREATED"));
    }

    @Test
    @DisplayName("Staff with contract-management permission can approve a confirmed check-out")
    void assignedStaffCanApproveCheckOut() throws Exception {
        approvedCheckIn();
        createCheckOut();
        sendAndConfirm();

        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("TERMINATED"));
    }

    @Test
    @DisplayName("Tenant confirmation issues final utility invoice; unpaid invoice blocks approval unless offset from deposit")
    void finalUtilitiesMustBeSettledBeforeApproval() throws Exception {
        approvedCheckIn();
        Instant now = Instant.now();
        serviceRates.save(new ServiceRate(UUID.randomUUID(), DataSeeder.PROP_1_ID, "Điện", ChargeType.PER_KWH,
            new java.math.BigDecimal("3500"), now, LocalDemoAuthenticationAdapter.OWNER_1_ID, now,
            LocalDemoAuthenticationAdapter.OWNER_1_ID));
        serviceRates.save(new ServiceRate(UUID.randomUUID(), DataSeeder.PROP_1_ID, "Nước", ChargeType.PER_M3,
            new java.math.BigDecimal("1000"), now, LocalDemoAuthenticationAdapter.OWNER_1_ID, now,
            LocalDemoAuthenticationAdapter.OWNER_1_ID));
        meterReadings.save(MeterReading.record(DataSeeder.ROOM_101_ID, UUID.randomUUID(), MeterType.ELECTRIC,
            LocalDate.now().minusDays(2), new java.math.BigDecimal("1000"), new java.math.BigDecimal("2000"), null,
            LocalDemoAuthenticationAdapter.OWNER_1_ID));

        mockMvc.perform(post(CONTRACT + "/check-out").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initialElectricMeter\":1510,\"initialWaterMeter\":45,"
                    + "\"roomCondition\":\"Phòng trả lại\",\"keysProvided\":\"2 chìa khóa\"}"))
            .andExpect(status().isCreated());
        addPhotos(CONTRACT + "/check-out");
        mockMvc.perform(post(CONTRACT + "/check-out/send").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk());
        mockMvc.perform(post(CONTRACT + "/check-out/confirm").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        var utilityInvoice = invoices.findByContractId(DataSeeder.CONTRACT_1_ID).stream()
            .filter(invoice -> invoice.getType() == InvoiceType.CHECK_OUT).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("40000")
            .compareTo(utilityInvoice.getTotalAmount()));
        org.junit.jupiter.api.Assertions.assertEquals(InvoiceStatus.ISSUED, utilityInvoice.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(utilityInvoice.getDueDate().isBefore(
            LocalDate.now().plusDays(com.phonghub.domain.model.Invoice.MIN_DAYS_TO_PAY)));

        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"depositOffsetInvoiceIds\":[\"" + utilityInvoice.getId() + "\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        org.junit.jupiter.api.Assertions.assertEquals(InvoiceStatus.PAID, utilityInvoice.getStatus());
        List<InvoicePaymentRecord> offsets = paymentRecords.findByInvoiceId(utilityInvoice.getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, offsets.size());
        org.junit.jupiter.api.Assertions.assertEquals(InvoicePaymentRecord.Type.DEPOSIT_OFFSET, offsets.getFirst().type());
    }

    @Test
    @DisplayName("No damage: the final deduction defaults to zero and the room becomes available")
    void cleanReturnMakesRoomAvailable() throws Exception {
        approvedCheckIn();
        createCheckOut();
        sendAndConfirm();

        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finalDeduction").value(0));
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("TERMINATED"));
    }

    @Test
    @DisplayName("A lost item also needs repair; a negative final deduction is rejected")
    void lostItemAndInvalidDeduction() throws Exception {
        approvedCheckIn();
        String created = createCheckOut();
        String itemId = JsonPath.read(created, "$.items[0].id");
        mockMvc.perform(patch(CONTRACT + "/check-out/items/" + itemId).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOST\",\"deductionAmount\":100000}"))
            .andExpect(status().isOk());
        sendAndConfirm();

        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"finalDeduction\":-1}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    @DisplayName("Reject and reopen work for check-out, and the contract stays active meanwhile")
    void rejectAndReopen() throws Exception {
        approvedCheckIn();
        createCheckOut();
        sendAndConfirm();

        mockMvc.perform(post(CONTRACT + "/check-out/reject").header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thiếu ảnh\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post(CONTRACT + "/check-out/reopen").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Wrong roles get 403 on check-out; tenants of the contract can read it")
    void roles() throws Exception {
        approvedCheckIn();
        createCheckOut();

        for (String outsider : new String[]{OWNER_2, TECH_1, TENANT_1}) {
            mockMvc.perform(post(CONTRACT + "/check-out/send").header("X-User-Id", outsider))
                .andExpect(status().isForbidden());
            mockMvc.perform(patch(CONTRACT + "/check-out").header("X-User-Id", outsider)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(CONTRACT + "/check-out").header("X-User-Id", TENANT_1)).andExpect(status().isOk());
        mockMvc.perform(get(CONTRACT + "/check-out").header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(post(CONTRACT + "/check-out/confirm").header("X-User-Id", OWNER_1))
            .andExpect(status().isForbidden());
    }

    private void expireContract() {
        LocalDate end = contracts.findById(DataSeeder.CONTRACT_1_ID).orElseThrow().getEndDate();
        assertEquals(1, contractLifecycle.runDaily(end.plusDays(1)).expired());
    }

    @Test
    @DisplayName("Expired contract (#87): room stays occupied and blocks new contracts; only check-out ends it")
    void expiredContractStillNeedsCheckOut() throws Exception {
        approvedCheckIn();
        expireContract();

        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("EXPIRED"));
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("OCCUPIED"));
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/contracts").header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"propertyId": "%s", "roomId": "%s", "primaryTenantId": "%s",
                     "depositAmount": 1000000, "rentAmount": 3000000, "startDate": "%s", "endDate": "%s",
                     "paymentDay": 5}
                    """.formatted(DataSeeder.PROP_1_ID, DataSeeder.ROOM_101_ID, DataSeeder.TENANT_RECORD_ID, LocalDate.now(),
                    LocalDate.now().plusMonths(6))))
            .andExpect(status().isConflict());

        createCheckOut();
        sendAndConfirm();
        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/contracts/" + DataSeeder.CONTRACT_1_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("TERMINATED"));
        mockMvc.perform(get("/api/rooms/" + DataSeeder.ROOM_101_ID).header("X-User-Id", OWNER_1))
            .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("Expired contract (#87): a new check-in is no longer allowed")
    void expiredContractRejectsCheckIn() throws Exception {
        expireContract();
        mockMvc.perform(post(CONTRACT + "/check-in").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
    }
}
