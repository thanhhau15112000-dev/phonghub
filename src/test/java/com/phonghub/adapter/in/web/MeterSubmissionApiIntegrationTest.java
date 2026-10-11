package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterReadingRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterSubmissionRepository;
import com.phonghub.application.port.out.MeterOcrPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class MeterSubmissionApiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String ROOM = DataSeeder.ROOM_101_ID.toString();
    private static final String SUBMISSIONS = "/api/rooms/" + ROOM + "/meter-submissions";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMeterSubmissionRepository submissions;
    @Autowired private InMemoryMeterReadingRepository readings;
    @MockitoBean private MeterOcrPort ocr;

    @BeforeEach
    void reset() {
        submissions.clear();
        readings.clear();
        ocrReads("1234.5", "0.95");
    }

    private void ocrReads(String value, String confidence) {
        when(ocr.read(any(), any(), any())).thenReturn(new MeterOcrPort.OcrResult(
            value == null ? null : new BigDecimal(value), new BigDecimal(confidence), "stub"));
    }

    private ResultActions submit(String type, String declared, String user) throws Exception {
        return mockMvc.perform(multipart(SUBMISSIONS).file(new MockMultipartFile("file", "m.png", "image/png", PNG))
            .param("meterType", type).param("declaredValue", declared).header("X-User-Id", user));
    }

    private String submitOk(String type, String declared) throws Exception {
        return JsonPath.read(submit(type, declared, TENANT_1).andExpect(status().isCreated()).andReturn().getResponse()
            .getContentAsString(), "$.id");
    }

    private ResultActions accept(String id, String body, String user) throws Exception {
        return mockMvc.perform(post("/api/meter-submissions/" + id + "/accept").header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("The server compares the tenant's number with the photo: MATCH, MISMATCH or UNVERIFIED")
    void crossCheck() throws Exception {
        submit("ELECTRIC", "1234.5", TENANT_1).andExpect(status().isCreated())
            .andExpect(jsonPath("$.matchStatus").value("MATCH")).andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.ocrValue").value(1234.5));
        submit("WATER", "1250", TENANT_1).andExpect(status().isCreated())
            .andExpect(jsonPath("$.matchStatus").value("MISMATCH"));

        submissions.clear();
        ocrReads("1234.5", "0.40");
        submit("ELECTRIC", "1234.5", TENANT_1).andExpect(jsonPath("$.matchStatus").value("UNVERIFIED"));
        submissions.clear();
        ocrReads(null, "0");
        submit("ELECTRIC", "1234.5", TENANT_1).andExpect(jsonPath("$.matchStatus").value("UNVERIFIED"));
    }

    @Test
    @DisplayName("Only the room's tenant can submit; managers see the queue, outsiders get 403")
    void authorization() throws Exception {
        for (String denied : new String[]{OWNER_1, STAFF_1, OWNER_2, TECH_1}) {
            submit("ELECTRIC", "10", denied).andExpect(status().isForbidden());
        }
        submitOk("ELECTRIC", "1234.5");
        for (String ok : new String[]{TENANT_1, OWNER_1, STAFF_1}) {
            mockMvc.perform(get(SUBMISSIONS).header("X-User-Id", ok)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        }
        for (String denied : new String[]{OWNER_2, STAFF_2, TECH_1}) {
            mockMvc.perform(get(SUBMISSIONS).header("X-User-Id", denied)).andExpect(status().isForbidden());
        }
        String queue = "/api/properties/" + DataSeeder.PROP_1_ID + "/meter-submissions?status=PENDING";
        mockMvc.perform(get(queue).header("X-User-Id", STAFF_1)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(queue).header("X-User-Id", TENANT_1)).andExpect(status().isForbidden());
        mockMvc.perform(get(queue).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("One pending submission per meter; values lower than the last reading and non-images are refused")
    void validation() throws Exception {
        submitOk("ELECTRIC", "1234.5");
        submit("ELECTRIC", "1300", TENANT_1).andExpect(status().isConflict());
        submit("WATER", "20", TENANT_1).andExpect(status().isCreated());
        submit("WATER", "-1", TENANT_1).andExpect(status().isBadRequest());
        mockMvc.perform(multipart(SUBMISSIONS).file(new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes()))
            .param("meterType", "ELECTRIC").param("declaredValue", "1").header("X-User-Id", TENANT_1))
            .andExpect(status().isBadRequest());

        // Đã có chỉ số chính thức 1200 thì số khai nhỏ hơn bị từ chối.
        submissions.clear();
        mockMvc.perform(post("/api/rooms/" + ROOM + "/meter-readings").header("X-User-Id", OWNER_1)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"meterType\":\"ELECTRIC\",\"currentValue\":1200,\"readingDate\":\""
                + LocalDate.now().minusMonths(1) + "\",\"baselineValue\":1000}")).andExpect(status().isCreated());
        submit("ELECTRIC", "1100", TENANT_1).andExpect(status().isBadRequest());
        submit("ELECTRIC", "1250", TENANT_1).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Accepting writes the official reading with the server-read OCR value; a mismatch needs an explicit decision")
    void acceptFlow() throws Exception {
        String id = submitOk("ELECTRIC", "1250");     // OCR đọc 1234.5 nên lệch
        accept(id, "{\"baselineValue\":1000}", STAFF_2).andExpect(status().isForbidden());
        accept(id, "{\"baselineValue\":1000}", TENANT_1).andExpect(status().isForbidden());
        accept(id, "{\"baselineValue\":1000}", OWNER_1).andExpect(status().isConflict());   // lệch, chưa xác nhận
        accept(id, "{\"finalValue\":1234.5}", OWNER_1).andExpect(status().isBadRequest());  // chưa có chỉ số gốc

        accept(id, "{\"finalValue\":1234.5,\"baselineValue\":1000}", OWNER_1).andExpect(status().isOk())
            .andExpect(jsonPath("$.currentValue").value(1234.5))
            .andExpect(jsonPath("$.consumption").value(234.5))
            .andExpect(jsonPath("$.ocrValue").value(1234.5))
            .andExpect(jsonPath("$.ocrCorrected").value(false))
            .andExpect(jsonPath("$.hasPhoto").value(true));
        mockMvc.perform(get(SUBMISSIONS).header("X-User-Id", TENANT_1))
            .andExpect(jsonPath("$[0].status").value("ACCEPTED")).andExpect(jsonPath("$[0].finalValue").value(1234.5));
        accept(id, "{}", OWNER_1).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A mismatch can also be accepted as declared after an explicit acknowledgement (audited as corrected)")
    void acceptMismatchAsDeclared() throws Exception {
        String id = submitOk("WATER", "50");
        accept(id, "{\"baselineValue\":10,\"acknowledgeMismatch\":true}", STAFF_1).andExpect(status().isOk())
            .andExpect(jsonPath("$.currentValue").value(50))
            .andExpect(jsonPath("$.ocrValue").value(1234.5))
            .andExpect(jsonPath("$.ocrCorrected").value(true));
    }

    @Test
    @DisplayName("Without a readable photo the tenant's number is accepted as unverified and no OCR value is stored")
    void unverifiedAccept() throws Exception {
        ocrReads(null, "0");
        String id = submitOk("ELECTRIC", "1500");
        accept(id, "{\"baselineValue\":1400}", OWNER_1).andExpect(status().isOk())
            .andExpect(jsonPath("$.currentValue").value(1500)).andExpect(jsonPath("$.ocrValue").doesNotExist());
    }

    @Test
    @DisplayName("Rejecting needs a reason, is manager-only and can be done once; the pending slot frees up")
    void reject() throws Exception {
        String id = submitOk("ELECTRIC", "1234.5");
        String path = "/api/meter-submissions/" + id + "/reject";
        mockMvc.perform(post(path).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("X-User-Id", TENANT_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Ảnh mờ\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(path).header("X-User-Id", OWNER_2).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Ảnh mờ\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(path).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Ảnh mờ\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.reviewNote").value("Ảnh mờ"));
        mockMvc.perform(post(path).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Lần hai\"}")).andExpect(status().isConflict());
        submit("ELECTRIC", "1234.5", TENANT_1).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Photo: the submitter and managers of the property can open it while pending; others get 403")
    void photoAccess() throws Exception {
        String id = submitOk("ELECTRIC", "1234.5");
        String photo = "/api/meter-submissions/" + id + "/photo";
        for (String ok : new String[]{TENANT_1, OWNER_1, STAFF_1}) {
            mockMvc.perform(get(photo).header("X-User-Id", ok)).andExpect(status().isFound());
        }
        for (String denied : new String[]{OWNER_2, STAFF_2, TECH_1}) {
            mockMvc.perform(get(photo).header("X-User-Id", denied)).andExpect(status().isForbidden());
        }
        String body = mockMvc.perform(get(SUBMISSIONS).header("X-User-Id", TENANT_1)).andReturn().getResponse()
            .getContentAsString();
        assertFalse(body.contains("photoKey"));
        assertTrue(body.contains("matchStatus"));
    }
}
