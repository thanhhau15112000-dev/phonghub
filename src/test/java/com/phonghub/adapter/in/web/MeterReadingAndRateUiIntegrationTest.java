package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryHandoverRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterReadingRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterSubmissionRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryServiceRateRepository;
import com.phonghub.domain.model.MeterType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** #366: trang chỉ số điện nước hiện mốc kỳ trước, đọc số từ ảnh, cho chủ trọ ghi chỉ số; đơn giá sửa trên trang nhà trọ. */
class MeterReadingAndRateUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String STAFF_2 = LocalDemoAuthenticationAdapter.STAFF_2_ID.toString();
    private static final String TECH_1 = LocalDemoAuthenticationAdapter.TECH_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String PAGE = "/rooms/" + DataSeeder.ROOM_101_ID + "/meter-submissions";
    private static final String PROPERTY_PAGE = "/properties/" + DataSeeder.PROP_1_ID;
    private static final String RATES = PROPERTY_PAGE + "/service-rates";
    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMeterReadingRepository readings;
    @Autowired private InMemoryMeterSubmissionRepository submissions;
    @Autowired private InMemoryServiceRateRepository rates;
    @Autowired private InMemoryHandoverRepository handovers;

    @BeforeEach
    void reset() {
        // Biên bản nhận phòng do test khác để lại sẽ thành mốc kỳ trước; dọn để phòng 101 bắt đầu chưa có mốc
        handovers.clear();
        readings.clear();
        submissions.clear();
        rates.clear();
    }

    private String page(String user) throws Exception {
        return mockMvc.perform(get(PAGE).header("X-User-Id", user)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Chủ trọ ghi chỉ số trên trang; người thuê thấy mốc kỳ trước và chỉ số đã chốt nhưng không ghi được")
    void ownerRecordsAndEveryoneSeesPreviousReading() throws Exception {
        String before = page(OWNER_1);
        assertTrue(before.contains("id=\"recordReading\""), "chủ trọ có form ghi chỉ số");
        assertTrue(before.contains("Chưa có mốc"), "phòng chưa có chỉ số kỳ trước");

        LocalDate lastMonth = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusMonths(1).withDayOfMonth(1);
        mockMvc.perform(multipart(PAGE + "/readings").header("X-User-Id", OWNER_1)
                .param("meterType", "ELECTRIC").param("currentValue", "1200").param("baselineValue", "1000")
                .param("readingDate", lastMonth.toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage", Matchers.containsString("tiêu thụ 200 kWh")));

        String tenantView = page(TENANT_1);
        assertTrue(tenantView.contains("data-previous-value=\"1200\""), "mốc kỳ trước là số vừa chốt");
        assertTrue(tenantView.contains("Chốt kỳ " + lastMonth.toString().substring(0, 7)));
        assertTrue(tenantView.contains("200 kWh"), "bảng chỉ số đã chốt có mức tiêu thụ");
        assertFalse(tenantView.contains("id=\"recordReading\""), "người thuê không có form ghi chỉ số");

        mockMvc.perform(multipart(PAGE + "/readings").header("X-User-Id", TENANT_1)
                .param("meterType", "WATER").param("currentValue", "10").param("baselineValue", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("errorMessage"));
        assertTrue(readings.findLatest(DataSeeder.ROOM_101_ID, MeterType.WATER).isEmpty(),
            "người thuê không ghi được chỉ số nước");
    }

    @Test
    @DisplayName("Nhân viên được giao nhà trọ ghi được chỉ số, ghi lại cùng tháng thì báo lỗi rõ")
    void staffRecordsReading() throws Exception {
        mockMvc.perform(multipart(PAGE + "/readings").header("X-User-Id", STAFF_1)
                .param("meterType", "WATER").param("currentValue", "35").param("baselineValue", "20"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage", Matchers.containsString("tiêu thụ 15 m³")));
        mockMvc.perform(multipart(PAGE + "/readings").header("X-User-Id", STAFF_1)
                .param("meterType", "WATER").param("currentValue", "40"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", Matchers.containsString("đã có chỉ số kỳ")));
        mockMvc.perform(multipart(PAGE + "/readings").header("X-User-Id", STAFF_1)
                .param("meterType", "ELECTRIC").param("currentValue", "12a"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", "Chỉ số mới phải là số."));
        mockMvc.perform(multipart(PAGE + "/readings").header("X-User-Id", STAFF_1)
                .param("meterType", "ELECTRIC").param("currentValue", " "))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", "Vui lòng nhập chỉ số mới trên đồng hồ."));
        assertTrue(readings.findLatest(DataSeeder.ROOM_101_ID, MeterType.ELECTRIC).isEmpty(), "nhập sai thì không ghi");
    }

    @Test
    @DisplayName("Đọc số từ ảnh: người thuê của phòng và chủ trọ được gọi; kỹ thuật viên và nhân viên nhà khác bị chặn")
    void ocrSuggestionAccess() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("file", "m.png", "image/png", PNG);
        mockMvc.perform(multipart(PAGE + "/ocr").file(photo).param("meterType", "ELECTRIC").header("X-User-Id", TENANT_1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manual").value(true))
            .andExpect(jsonPath("$.message").value(Matchers.containsString("tự nhập")));
        mockMvc.perform(multipart(PAGE + "/ocr").file(photo).param("meterType", "WATER").header("X-User-Id", OWNER_1))
            .andExpect(status().isOk());
        mockMvc.perform(multipart(PAGE + "/ocr").file(photo).param("meterType", "WATER").header("X-User-Id", TECH_1))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart(PAGE + "/ocr").file(photo).param("meterType", "WATER").header("X-User-Id", STAFF_2))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart(PAGE + "/ocr")
                .file(new MockMultipartFile("file", "m.txt", "text/plain", "abc".getBytes()))
                .param("meterType", "WATER").header("X-User-Id", OWNER_1))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Đơn giá: chủ trọ thêm và sửa trên trang nhà trọ, nhân viên chỉ xem, kỹ thuật viên không thấy")
    void serviceRatesOnPropertyPage() throws Exception {
        mockMvc.perform(post(RATES).header("X-User-Id", OWNER_1)
                .param("serviceName", "Điện").param("chargeType", "PER_KWH").param("pricePerUnit", "3500"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("successMessage"));
        String rateId = rates.findByPropertyId(DataSeeder.PROP_1_ID).get(0).id().toString();

        String ownerView = mockMvc.perform(get(PROPERTY_PAGE).header("X-User-Id", OWNER_1)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(ownerView.contains("id=\"serviceRates\""));
        assertTrue(ownerView.contains("Theo số điện (kWh)"));
        assertTrue(ownerView.contains("3.500"));
        assertTrue(ownerView.contains("Thêm đơn giá"));

        mockMvc.perform(post(RATES + "/" + rateId).header("X-User-Id", OWNER_1)
                .param("serviceName", "Điện").param("chargeType", "PER_KWH").param("pricePerUnit", "4000"))
            .andExpect(status().is3xxRedirection());
        assertTrue(mockMvc.perform(get(PROPERTY_PAGE).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString().contains("4.000"));

        mockMvc.perform(post(RATES).header("X-User-Id", OWNER_1)
                .param("serviceName", "Nước").param("chargeType", "PER_M3").param("pricePerUnit", "-1"))
            .andExpect(flash().attribute("errorMessage", "Đơn giá phải là số không âm."));
        mockMvc.perform(post(RATES).header("X-User-Id", OWNER_1)
                .param("serviceName", "Nước").param("chargeType", "PER_M3").param("pricePerUnit", "abc"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", "Đơn giá phải là số không âm."));
        mockMvc.perform(post(RATES + "/" + rateId).header("X-User-Id", OWNER_1)
                .param("serviceName", "Điện").param("chargeType", "PER_KWH").param("pricePerUnit", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", "Đơn giá phải là số không âm."));

        // Điện, nước đứng trước các dịch vụ khác dù tên xếp sau
        mockMvc.perform(post(RATES).header("X-User-Id", OWNER_1)
                .param("serviceName", "An ninh").param("chargeType", "PER_ROOM_FLAT").param("pricePerUnit", "50000"))
            .andExpect(status().is3xxRedirection());
        String sorted = mockMvc.perform(get(PROPERTY_PAGE).header("X-User-Id", OWNER_1)).andReturn().getResponse()
            .getContentAsString();
        assertTrue(sorted.indexOf("Theo số điện (kWh)</td>") < sorted.indexOf("Cố định theo phòng</td>"),
            "đơn giá điện đứng trước dịch vụ cố định");

        String staffView = mockMvc.perform(get(PROPERTY_PAGE).header("X-User-Id", STAFF_1)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(staffView.contains("id=\"serviceRates\""), "nhân viên xem được đơn giá");
        assertFalse(staffView.contains("Thêm đơn giá"), "nhân viên không sửa đơn giá");
        mockMvc.perform(post(RATES).header("X-User-Id", STAFF_1)
                .param("serviceName", "Wifi").param("chargeType", "PER_ROOM_FLAT").param("pricePerUnit", "100000"))
            .andExpect(flash().attributeExists("errorMessage"));

        String techView = mockMvc.perform(get(PROPERTY_PAGE).header("X-User-Id", TECH_1)).andReturn().getResponse()
            .getContentAsString();
        assertFalse(techView.contains("id=\"serviceRates\""), "#346: kỹ thuật viên không thấy đơn giá");
    }
}
