package com.phonghub.adapter.in.web;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** #80: chỉ owner/admin của nhà trọ mới cấu hình được phạt trả trễ. */
class LateFeePolicyUiIntegrationTest extends SeededWebTest {

    private static final String OWNER_1 = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String OWNER_2 = LocalDemoAuthenticationAdapter.OWNER_2_ID.toString();
    private static final String STAFF_1 = LocalDemoAuthenticationAdapter.STAFF_1_ID.toString();
    private static final String TENANT_1 = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String PAGE = "/properties/" + DataSeeder.PROP_1_ID + "/late-fee";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("owner lưu cấu hình và thấy lại giá trị đã lưu")
    void ownerSavesPolicy() throws Exception {
        mockMvc.perform(post(PAGE).header("X-User-Id", OWNER_1).param("enabled", "true").param("feeType", "PERCENT")
                .param("feeValue", "3").param("graceDays", "4").param("capAmount", "200000"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage", "Đã lưu cấu hình phạt trả trễ."));

        String html = mockMvc.perform(get(PAGE).header("X-User-Id", OWNER_1))
            .andExpect(status().isOk()).andExpect(view().name("properties/late-fee"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"4\""), "grace days shown");
        assertTrue(html.contains("200000"), "cap shown");
    }

    @Test
    @DisplayName("cấu hình sai hiển thị lỗi và không lưu")
    void invalidPolicyRejected() throws Exception {
        mockMvc.perform(post(PAGE).header("X-User-Id", OWNER_1).param("enabled", "true").param("feeType", "PERCENT")
                .param("feeValue", "150").param("graceDays", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("errorMessage", "Phạt theo phần trăm không được vượt quá 100%."));
    }

    @Test
    @DisplayName("owner khác, nhân viên và người thuê không xem hay sửa được")
    void accessControl() throws Exception {
        for (String user : new String[] {OWNER_2, STAFF_1, TENANT_1}) {
            mockMvc.perform(get(PAGE).header("X-User-Id", user)).andExpect(status().is3xxRedirection());
            mockMvc.perform(post(PAGE).header("X-User-Id", user).param("enabled", "true").param("feeType", "FIXED")
                    .param("feeValue", "50000").param("graceDays", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
        }
    }
}
