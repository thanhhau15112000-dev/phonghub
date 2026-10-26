package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
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
import com.phonghub.application.port.out.AuditPort;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class HandoverReportApiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String CONTRACT = "/api/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String BODY = "{\"initialElectricMeter\":1500,\"initialWaterMeter\":40,"
        + "\"roomCondition\":\"Phòng sạch\",\"keysProvided\":\"2 chìa\"}";
    private static final String BODY_OUT = "{\"initialElectricMeter\":1620,\"initialWaterMeter\":52,"
        + "\"roomCondition\":\"Phòng trả\",\"keysProvided\":\"2 chìa\"}";
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
    @Autowired private InMemoryInvoiceRepository invoices;
    @Autowired private InMemoryInvoicePaymentRecordRepository paymentRecords;
    @Autowired private InMemoryMeterReadingRepository meterReadings;
    @Autowired private InMemoryServiceRateRepository serviceRates;

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

    private void addPhotos(String path) throws Exception {
        for (String kind : new String[]{"ROOM", "METER_ELECTRIC", "METER_WATER"}) {
            mockMvc.perform(multipart(path + "/photos").file(new MockMultipartFile("file", "p.png", "image/png", PNG))
                .param("kind", kind).header("X-User-Id", OWNER_1)).andExpect(status().isCreated());
        }
    }

    private void createAsset(String name) throws Exception {
        mockMvc.perform(post("/api/rooms/" + DataSeeder.ROOM_101_ID + "/assets").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"quantity\":1}"))
            .andExpect(status().isCreated());
    }

    private void approvedCheckIn() throws Exception {
        createAsset("Giường");
        createAsset("Tủ lạnh");
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

    /** Check-out: giường hỏng, tủ lạnh tốt; đã gửi và tenant đã xác nhận, chưa duyệt. */
    private void checkOutPendingApproval() throws Exception {
        String created = mockMvc.perform(post(CONTRACT + "/check-out").header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(BODY_OUT))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(created, "$.items[0].id");
        mockMvc.perform(patch(CONTRACT + "/check-out/items/" + itemId).header("X-User-Id", STAFF_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DAMAGED\",\"conditionNote\":\"Vỡ cánh\",\"deductionAmount\":300000}"))
            .andExpect(status().isOk());
        addPhotos(CONTRACT + "/check-out");
        mockMvc.perform(post(CONTRACT + "/check-out/send").header("X-User-Id", OWNER_1)).andExpect(status().isOk());
        mockMvc.perform(post(CONTRACT + "/check-out/confirm").header("X-User-Id", TENANT_1)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Comparison lists each asset before/after, flags changes and shows meter consumption")
    void comparison() throws Exception {
        approvedCheckIn();
        mockMvc.perform(get(CONTRACT + "/handover-comparison").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.checkOut").doesNotExist())
            .andExpect(jsonPath("$.rows.length()").value(2));

        checkOutPendingApproval();
        String json = mockMvc.perform(get(CONTRACT + "/handover-comparison").header("X-User-Id", STAFF_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.electricConsumption").value(120))
            .andExpect(jsonPath("$.waterConsumption").value(12))
            .andExpect(jsonPath("$.checkInPhotoIds.length()").value(1))
            .andExpect(jsonPath("$.checkOutPhotoIds.length()").value(1))
            .andReturn().getResponse().getContentAsString();
        assertEquals(1, ((java.util.List<?>) JsonPath.read(json, "$.rows[?(@.changed==true)]")).size());
        assertEquals("DAMAGED", ((java.util.List<?>) JsonPath.read(json,
            "$.rows[?(@.checkOutStatus=='DAMAGED')].checkOutStatus")).get(0));
        assertFalse(json.contains("imageKey"), "storage keys must not be exposed");
        assertFalse(json.contains("Hash"), "integrity hashes are only in the PDF");
    }

    @Test
    @DisplayName("Comparison is limited to the contract's managers and its tenants")
    void comparisonAuthorization() throws Exception {
        approvedCheckIn();
        for (String denied : new String[]{OWNER_2, STAFF_2, TECH_1}) {
            mockMvc.perform(get(CONTRACT + "/handover-comparison").header("X-User-Id", denied))
                .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(CONTRACT + "/handover-comparison").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/contracts/" + java.util.UUID.randomUUID() + "/handover-comparison")
            .header("X-User-Id", OWNER_1)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PDF export needs an approved handover (409 otherwise) and yields a real, audited PDF")
    void pdfExport() throws Exception {
        approvedCheckIn();
        checkOutPendingApproval();

        mockMvc.perform(get(CONTRACT + "/handover-report/check-out/pdf").header("X-User-Id", OWNER_1))
            .andExpect(status().isConflict());
        mockMvc.perform(get(CONTRACT + "/handover-report/check-out/pdf").header("X-User-Id", OWNER_2))
            .andExpect(status().isForbidden());

        byte[] checkInPdf = mockMvc.perform(get(CONTRACT + "/handover-report/check-in/pdf")
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Content-Type", "application/pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(checkInPdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
        assertTrue(checkInPdf.length > 5_000, "font and content should be embedded");

        mockMvc.perform(post(CONTRACT + "/check-out/approve").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON).content("{\"finalDeduction\":250000}")).andExpect(status().isOk());
        byte[] checkOutPdf = mockMvc.perform(get(CONTRACT + "/handover-report/check-out/pdf")
                .header("X-User-Id", TENANT_1))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(checkOutPdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));

        long exports = ((InMemoryAuditAdapter) auditPort).getRecordedEvents().stream()
            .filter(event -> event.action().equals("HANDOVER_PDF_EXPORTED")).count();
        assertEquals(2, exports);
    }
}
