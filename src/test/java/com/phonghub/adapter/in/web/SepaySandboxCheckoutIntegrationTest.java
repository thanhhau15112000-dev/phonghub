package com.phonghub.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.phonghub.adapter.out.identity.LocalDemoAuthenticationAdapter;
import com.phonghub.adapter.out.persistence.inmemory.DataSeeder;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPaymentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryRoomRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.config.SepayProperties;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SepaySandboxCheckoutIntegrationTest {

    private static final String OWNER = LocalDemoAuthenticationAdapter.OWNER_1_ID.toString();
    private static final String TENANT = LocalDemoAuthenticationAdapter.TENANT_1_ID.toString();
    private static final String CONTRACT_URL = "/contracts/" + DataSeeder.CONTRACT_1_ID;
    private static final String IPN_URL = "/api/v1/payments/sepay/ipn";
    private static final String CHECKOUT_URL = "https://pay-sandbox.sepay.vn/v1/checkout/init";
    private static final String SANDBOX_SECRET = "sandbox-secret";

    @Autowired private MockMvc mockMvc;
    @Autowired private SepayProperties sepayProperties;
    @Autowired private InMemoryUserRepository userRepository;
    @Autowired private InMemoryPropertyRepository propertyRepository;
    @Autowired private InMemoryStaffPropertyAssignmentRepository assignmentRepository;
    @Autowired private InMemoryRoomRepository roomRepository;
    @Autowired private InMemoryTenantRepository tenantRepository;
    @Autowired private InMemoryContractRepository contractRepository;
    @Autowired private InMemoryMaintenanceTicketRepository ticketRepository;
    @Autowired private InMemoryInvoiceRepository invoiceRepository;
    @Autowired private InMemoryPaymentRepository paymentRepository;

    @BeforeEach
    void resetData() {
        resetSepayProperties();
        userRepository.clear();
        propertyRepository.clear();
        assignmentRepository.clear();
        roomRepository.clear();
        tenantRepository.clear();
        contractRepository.clear();
        ticketRepository.clear();
        invoiceRepository.clear();
        paymentRepository.clear();
        DataSeeder.seedAll(
            userRepository, propertyRepository, assignmentRepository, roomRepository,
            tenantRepository, contractRepository, ticketRepository
        );
    }

    @AfterEach
    void restore() {
        resetSepayProperties();
    }

    private void resetSepayProperties() {
        sepayProperties.setBankCode(null);
        sepayProperties.setBankAccountNumber(null);
        sepayProperties.setBankAccountName(null);
        sepayProperties.setSandboxCheckoutEnabled(false);
        sepayProperties.setMerchantId(null);
        sepayProperties.setSecretKey(null);
        sepayProperties.setPublicBaseUrl(null);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String userId) {
        return builder.header("X-User-Id", userId);
    }

    private Invoice issueRent() throws Exception {
        mockMvc.perform(as(post(CONTRACT_URL + "/invoices"), OWNER).param("period", YearMonth.now().toString()));
        return invoiceRepository.findByContractId(DataSeeder.CONTRACT_1_ID).get(0);
    }

    private void configureSandboxCheckout() {
        sepayProperties.setBankCode("MBBank");
        sepayProperties.setBankAccountNumber("0000000001");
        sepayProperties.setBankAccountName("LE THANH HAU");
        sepayProperties.setSandboxCheckoutEnabled(true);
        sepayProperties.setMerchantId("SP-TEST-123");
        sepayProperties.setSecretKey(SANDBOX_SECRET);
        sepayProperties.setPublicBaseUrl("https://phonghub.example");
    }

    private String startSandboxCheckout(Invoice invoice) throws Exception {
        MvcResult result = mockMvc.perform(as(post("/invoices/" + invoice.getId() + "/sepay-checkout"), TENANT))
            .andExpect(status().isOk())
            .andExpect(view().name("invoices/sepay-checkout"))
            .andReturn();
        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains(CHECKOUT_URL), "checkout form points at SePay Sandbox");
        assertTrue(html.contains("document.getElementById('sepay-checkout').submit()"), "browser auto-submits the checkout form");
        assertTrue(html.contains("name=\"order_amount\" value=\""
            + invoice.remainingAmount().toBigIntegerExact() + "\""), "checkout uses the remaining invoice amount");
        assertTrue(html.contains("name=\"success_url\" value=\"https://phonghub.example/invoices/"
            + invoice.getId() + "/pay?sepay=result\""), "successful return goes back to the invoice page");
        assertTrue(html.contains("name=\"signature\" value=\""), "checkout form includes its signature");
        Matcher formMatcher = Pattern.compile("(?s)<form id=\"sepay-checkout\".*?</form>").matcher(html);
        assertTrue(formMatcher.find(), "checkout form is rendered");
        Matcher inputMatcher = Pattern.compile("<input[^>]*name=\"([^\"]+)\"[^>]*value=\"([^\"]*)\"[^>]*>")
            .matcher(formMatcher.group());
        List<String> fieldNames = new ArrayList<>();
        Map<String, String> formFields = new LinkedHashMap<>();
        while (inputMatcher.find()) {
            String fieldName = HtmlUtils.htmlUnescape(inputMatcher.group(1));
            String fieldValue = HtmlUtils.htmlUnescape(inputMatcher.group(2));
            fieldNames.add(fieldName);
            formFields.put(fieldName, fieldValue);
        }
        List<String> expectedFieldNames = List.of(
            "order_amount", "merchant", "currency", "operation", "order_description",
            "order_invoice_number", "payment_method", "success_url", "error_url", "cancel_url", "signature"
        );
        assertEquals(expectedFieldNames, fieldNames, "SePay input fields retain the signed form order");

        String signedFields = expectedFieldNames.stream()
            .filter(field -> !"signature".equals(field))
            .map(field -> field + "=" + formFields.get(field))
            .reduce((left, right) -> left + "," + right)
            .orElseThrow();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SANDBOX_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expectedSignature = Base64.getEncoder().encodeToString(mac.doFinal(signedFields.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedSignature, formFields.get("signature"), "signature matches the rendered checkout fields");

        Matcher matcher = Pattern.compile("name=\"order_invoice_number\" value=\"(PH[A-F0-9]{24})\"")
            .matcher(html);
        assertTrue(matcher.find(), "checkout form contains the pending SePay order number");
        return matcher.group(1);
    }

    private String sandboxIpn(String orderNumber, String amount, String transactionId, String transactionAmount) {
        return """
            {
              "notification_type":"ORDER_PAID",
              "order":{"order_status":"CAPTURED","order_currency":"VND","order_amount":"%s","order_invoice_number":"%s"},
              "transaction":{"transaction_type":"PAYMENT","transaction_status":"APPROVED","transaction_currency":"VND","id":"%s","transaction_amount":"%s"}
            }
            """.formatted(amount, orderNumber, transactionId, transactionAmount);
    }

    @Test
    @DisplayName("Sandbox checkout hands off to SePay and only a valid IPN marks the invoice paid")
    void checkoutAndIpnFlow() throws Exception {
        Invoice invoice = issueRent();
        configureSandboxCheckout();
        String payUrl = "/invoices/" + invoice.getId() + "/pay";

        mockMvc.perform(as(get(payUrl), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thanh toán</button>")))
            .andExpect(content().string(containsString("/invoices/" + invoice.getId() + "/sepay-checkout")))
            .andExpect(content().string(not(containsString("simulate-payment"))))
            .andExpect(content().string(not(containsString("Môi trường mô phỏng"))))
            .andExpect(content().string(not(containsString("Mã QR chuyển khoản"))))
            .andExpect(content().string(not(containsString("MBBank"))))
            .andExpect(content().string(not(containsString("LE THANH HAU"))))
            .andExpect(content().string(containsString("fetch(statusUrl")));

        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/status"), TENANT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paid").value(false));

        String orderNumber = startSandboxCheckout(invoice);
        String amount = invoice.remainingAmount().toBigIntegerExact().toString();
        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/status"), TENANT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paid").value(false));

        mockMvc.perform(post(IPN_URL)
                .header("X-Secret-Key", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sandboxIpn(orderNumber, amount, "sandbox-txn-1", amount)))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post(IPN_URL)
                .header("X-Secret-Key", SANDBOX_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sandboxIpn(orderNumber, amount, "sandbox-txn-bad-amount", "1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/status"), TENANT))
            .andExpect(jsonPath("$.paid").value(false));

        String validIpn = sandboxIpn(orderNumber, amount, "sandbox-txn-1", amount);
        mockMvc.perform(post(IPN_URL)
                .header("X-Secret-Key", SANDBOX_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validIpn))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("paid"))
            .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/status"), TENANT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paid").value(true))
            .andExpect(jsonPath("$.remaining").value(0));
        assertEquals(InvoiceStatus.PAID, invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus());
        assertEquals(1, paymentRepository.findAll().size());

        mockMvc.perform(post(IPN_URL)
                .header("X-Secret-Key", SANDBOX_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validIpn))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("paid"))
            .andExpect(jsonPath("$.duplicate").value(true));
        assertEquals(1, paymentRepository.findAll().size(), "duplicate IPN must not record another payment");

        mockMvc.perform(as(get(payUrl), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Khoản này đã được thanh toán đủ.")))
            .andExpect(content().string(not(containsString("sepay-checkout"))));
    }

    @Test
    void incompleteSandboxConfigurationKeepsTheManualTransferPage() throws Exception {
        Invoice invoice = issueRent();
        sepayProperties.setBankCode("MBBank");
        sepayProperties.setBankAccountNumber("0000000001");
        sepayProperties.setSandboxCheckoutEnabled(true);
        sepayProperties.setMerchantId("SP-TEST-123");
        sepayProperties.setPublicBaseUrl("https://phonghub.example");

        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/pay"), TENANT))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/sepay-checkout"))))
            .andExpect(content().string(containsString("Mã QR chuyển khoản")))
            .andExpect(content().string(containsString("MBBank")));
    }

    @Test
    void unrelatedOwnerReceivesForbiddenJsonFromInvoiceStatus() throws Exception {
        Invoice invoice = issueRent();
        mockMvc.perform(as(get("/invoices/" + invoice.getId() + "/status"),
                LocalDemoAuthenticationAdapter.OWNER_2_ID.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.title").value("Unauthorized Property Access"))
            .andExpect(jsonPath("$.paid").doesNotExist());
    }
}
