package com.phonghub.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterReadingRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryServiceRateRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UtilityApiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String ROOM = DataSeeder.ROOM_104_ID.toString();
    private static final String READINGS = "/api/rooms/" + ROOM + "/meter-readings";
    private static final String RATES = "/api/properties/" + DataSeeder.PROP_1_ID + "/service-rates";
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMeterReadingRepository readings;
    @Autowired private InMemoryServiceRateRepository rates;

    @BeforeEach
    void reset() {
        readings.clear();
        rates.clear();
    }

    private static String month(int monthsAgo) {
        return LocalDate.now().minusMonths(monthsAgo).withDayOfMonth(1).toString();
    }

    private String body(String type, String current, String date, String baseline) {
        return "{\"meterType\":\"" + type + "\",\"currentValue\":" + current + ",\"readingDate\":\"" + date + "\""
            + (baseline == null ? "" : ",\"baselineValue\":" + baseline) + "}";
    }

    private String record(String body, String user, int expected) throws Exception {
        return mockMvc.perform(post(READINGS).header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Consumption = current - previous; the next period takes the previous value from the last reading")
    void consumptionChainsAcrossPeriods() throws Exception {
        String first = record(body("ELECTRIC", "120", month(2), "100"), OWNER_1, 201);
        org.junit.jupiter.api.Assertions.assertEquals(20, ((Number) JsonPath.read(first, "$.consumption")).intValue());

        mockMvc.perform(post(READINGS).header("X-User-Id", STAFF_1).contentType(MediaType.APPLICATION_JSON)
                .content(body("ELECTRIC", "150", month(1), null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.previousValue").value(120))
            .andExpect(jsonPath("$.consumption").value(30));

        mockMvc.perform(get(READINGS).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Baseline is required for the first reading and cannot be overridden later")
    void baselineRules() throws Exception {
        record(body("WATER", "10", month(2), null), OWNER_1, 400);
        record(body("WATER", "15", month(2), "10"), OWNER_1, 201);
        record(body("WATER", "20", month(1), "99"), OWNER_1, 400);
    }

    @Test
    @DisplayName("A lower value is rejected unless the meter was replaced with a note")
    void lowerValueNeedsReplacement() throws Exception {
        record(body("ELECTRIC", "500", month(3), "400"), OWNER_1, 201);
        record(body("ELECTRIC", "50", month(2), null), OWNER_1, 400);

        String noNote = "{\"meterType\":\"ELECTRIC\",\"currentValue\":50,\"readingDate\":\"" + month(2)
            + "\",\"replacement\":{\"oldFinalValue\":520,\"newStartValue\":0,\"note\":\"\"}}";
        record(noNote, OWNER_1, 400);

        String replaced = "{\"meterType\":\"ELECTRIC\",\"currentValue\":50,\"readingDate\":\"" + month(2)
            + "\",\"replacement\":{\"oldFinalValue\":520,\"newStartValue\":0,\"note\":\"Thay đồng hồ hỏng\"}}";
        mockMvc.perform(post(READINGS).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
                .content(replaced))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.replaced").value(true))
            .andExpect(jsonPath("$.consumption").value(70));
    }

    @Test
    @DisplayName("One reading per room, meter type and period; older or equal periods conflict (409)")
    void onePerPeriod() throws Exception {
        record(body("ELECTRIC", "120", month(1), "100"), OWNER_1, 201);
        record(body("ELECTRIC", "130", month(1), null), OWNER_1, 409);
        record(body("ELECTRIC", "130", month(2), null), OWNER_1, 409);
        record(body("WATER", "12", month(1), "10"), OWNER_1, 201);
        record(body("ELECTRIC", "130", LocalDate.now().plusMonths(1).toString(), null), OWNER_1, 400);
    }

    @Test
    @DisplayName("Only users who manage the property can record; others get 403")
    void recordingAuthorization() throws Exception {
        for (String outsider : new String[]{OWNER_2, TECH_1}) {
            record(body("ELECTRIC", "120", month(1), "100"), outsider, 403);
        }
        mockMvc.perform(get(READINGS).header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Meter photo: attach by manager, rejects non-images, downloadable through a signed URL")
    void photo() throws Exception {
        String created = record(body("ELECTRIC", "120", month(1), "100"), OWNER_1, 201);
        String id = JsonPath.read(created, "$.id");
        String photoPath = "/api/meter-readings/" + id + "/photo";

        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_1)).andExpect(status().isNotFound());
        mockMvc.perform(multipart(photoPath).file(new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes()))
            .header("X-User-Id", OWNER_1)).andExpect(status().isBadRequest());
        mockMvc.perform(multipart(photoPath).file(new MockMultipartFile("file", "m.png", "image/png", PNG))
            .header("X-User-Id", OWNER_2)).andExpect(status().isForbidden());
        mockMvc.perform(multipart(photoPath).file(new MockMultipartFile("file", "m.png", "image/png", PNG))
                .header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasPhoto").value(true));
        mockMvc.perform(get(photoPath).header("X-User-Id", OWNER_1)).andExpect(status().isFound());
    }

    @Test
    @DisplayName("Service rates are configured per property by the owner; duplicate names conflict")
    void serviceRates() throws Exception {
        String rate = "{\"serviceName\":\"Điện\",\"chargeType\":\"PER_KWH\",\"pricePerUnit\":3500}";
        mockMvc.perform(post(RATES).header("X-User-Id", STAFF_1).contentType(MediaType.APPLICATION_JSON).content(rate))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(RATES).header("X-User-Id", OWNER_2).contentType(MediaType.APPLICATION_JSON).content(rate))
            .andExpect(status().isForbidden());

        String created = mockMvc.perform(post(RATES).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON).content(rate))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.pricePerUnit").value(3500))
            .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post(RATES).header("X-User-Id", OWNER_1).contentType(MediaType.APPLICATION_JSON)
            .content(rate)).andExpect(status().isConflict());

        String id = JsonPath.read(created, "$.id");
        mockMvc.perform(patch("/api/service-rates/" + id).header("X-User-Id", OWNER_1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceName\":\"Điện\",\"chargeType\":\"PER_KWH\",\"pricePerUnit\":4000}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pricePerUnit").value(4000));
        mockMvc.perform(patch("/api/service-rates/" + id).header("X-User-Id", OWNER_2)
                .contentType(MediaType.APPLICATION_JSON).content(rate))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(RATES).header("X-User-Id", STAFF_1))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
}
