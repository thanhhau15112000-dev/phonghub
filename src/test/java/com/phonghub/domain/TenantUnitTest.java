package com.phonghub.domain;

import com.phonghub.domain.model.Tenant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit tests cho đối tượng Tenant thuộc tầng Domain")
class TenantUnitTest {

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("TC1: Khởi tạo Tenant thành công khi các trường hợp lệ")
    void createTenantWithValidData() {
        Instant now = Instant.now();
        Tenant tenant = new Tenant(
            tenantId,
            userId,
            "Nguyễn Văn A",
            "079123456789",
            "0901234567",
            "tenant@phonghub.local",
            "123 Đường Số 1, Quận 7, TP.HCM",
            now
        );

        assertEquals(tenantId, tenant.id());
        assertEquals(userId, tenant.userId());
        assertEquals("Nguyễn Văn A", tenant.fullName());
        assertEquals("079123456789", tenant.identityCardNumber());
        assertEquals("0901234567", tenant.phone());
        assertEquals("tenant@phonghub.local", tenant.email());
        assertEquals("123 Đường Số 1, Quận 7, TP.HCM", tenant.permanentAddress());
        assertEquals(now, tenant.createdAt());
        assertFalse(tenant.identityPurged());
    }

    @Test
    @DisplayName("TC2: Khởi tạo với id null ném IllegalArgumentException")
    void createTenantWithNullId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new Tenant(null, userId, "Nguyễn Văn A", "079123456789", "0901234567", "a@test.com", "Địa chỉ", Instant.now())
        );
        assertEquals("Tenant id cannot be null", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("TC3: Khởi tạo với fullName rỗng/khoảng trắng ném IllegalArgumentException")
    void createTenantWithBlankFullName(String blankName) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new Tenant(tenantId, userId, blankName, "079123456789", "0901234567", "a@test.com", "Địa chỉ", Instant.now())
        );
        assertTrue(ex.getMessage().contains("Họ tên người thuê không được để trống"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("TC4: Khởi tạo với số CCCD rỗng/khoảng trắng ném IllegalArgumentException")
    void createTenantWithBlankIdentityCard(String blankCccd) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new Tenant(tenantId, userId, "Nguyễn Văn A", blankCccd, "0901234567", "a@test.com", "Địa chỉ", Instant.now())
        );
        assertTrue(ex.getMessage().contains("Số CCCD không được để trống"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("TC5: Khởi tạo với số điện thoại rỗng/khoảng trắng ném IllegalArgumentException")
    void createTenantWithBlankPhone(String blankPhone) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new Tenant(tenantId, userId, "Nguyễn Văn A", "079123456789", blankPhone, "a@test.com", "Địa chỉ", Instant.now())
        );
        assertTrue(ex.getMessage().contains("Số điện thoại không được để trống"));
    }

    @Test
    @DisplayName("TC6: Khởi tạo khi createdAt null thì tự gán Instant.now()")
    void createTenantWithNullCreatedAt() {
        Tenant tenant = new Tenant(
            tenantId,
            userId,
            "Nguyễn Văn A",
            "079123456789",
            "0901234567",
            "a@test.com",
            "Địa chỉ",
            null
        );

        assertNotNull(tenant.createdAt());
    }

    @Test
    @DisplayName("TC7: Phương thức factory Tenant.create() sinh id ngẫu nhiên và createdAt hợp lệ")
    void factoryMethodCreateGeneratesId() {
        Tenant tenant = Tenant.create(
            userId,
            "Trần Thị B",
            "079987654321",
            "0987654321",
            "b@test.local",
            "Quận 1, TP.HCM"
        );

        assertNotNull(tenant.id());
        assertEquals(userId, tenant.userId());
        assertEquals("Trần Thị B", tenant.fullName());
        assertNotNull(tenant.createdAt());
    }

    @Test
    @DisplayName("TC8: updateInfo thành công và tự động trim() các trường khoảng trắng thừa")
    void updateInfoSuccessfullyWithTrimmedData() {
        Tenant original = Tenant.create(userId, "Lê Văn C", "079111222333", "0912345678", "c@test.com", "Địa chỉ cũ");

        Tenant updated = original.updateInfo(
            "  Lê Văn C Mới  ",
            "  079111222999  ",
            "  0908765432  ",
            "  new_email@phonghub.local  ",
            "  456 Đường Mới, Quận 10  "
        );

        assertEquals(original.id(), updated.id());
        assertEquals(original.userId(), updated.userId());
        assertEquals("Lê Văn C Mới", updated.fullName());
        assertEquals("079111222999", updated.identityCardNumber());
        assertEquals("0908765432", updated.phone());
        assertEquals("new_email@phonghub.local", updated.email());
        assertEquals("456 Đường Mới, Quận 10", updated.permanentAddress());
        assertEquals(original.createdAt(), updated.createdAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "901234567",       // Thiếu số 0 đầu (9 chữ số)
        "1901234567",      // Bắt đầu bằng số 1 thay vì 0
        "090123456",       // Chỉ có 9 chữ số
        "09012345678",     // Có 11 chữ số
        "090123456a",      // Có ký tự chữ cái
        "090-123-4567",    // Có dấu gạch nối
        "090 123 4567"     // Có khoảng trắng ở giữa
    })
    @DisplayName("TC9: updateInfo ném lỗi khi số điện thoại không đúng định dạng 10 chữ số bắt đầu bằng 0")
    void updateInfoWithInvalidPhoneNumber(String invalidPhone) {
        Tenant tenant = Tenant.create(userId, "Lê Văn C", "079111222333", "0912345678", "c@test.com", "Địa chỉ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tenant.updateInfo("Lê Văn C", "079111222333", invalidPhone, "c@test.com", "Địa chỉ")
        );
        assertTrue(ex.getMessage().contains("Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng số 0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-an-email",
        "user@",
        "@domain.com",
        "user@domain",
        "user@domain.",
        "user space@domain.com"
    })
    @DisplayName("TC10: updateInfo ném lỗi khi định dạng email không hợp lệ")
    void updateInfoWithInvalidEmailFormat(String invalidEmail) {
        Tenant tenant = Tenant.create(userId, "Lê Văn C", "079111222333", "0912345678", "c@test.com", "Địa chỉ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tenant.updateInfo("Lê Văn C", "079111222333", "0912345678", invalidEmail, "Địa chỉ")
        );
        assertTrue(ex.getMessage().contains("Định dạng email không hợp lệ"));
    }

    @Test
    @DisplayName("TC10b: updateInfo cho phép email và địa chỉ để null hoặc rỗng")
    void updateInfoAllowsNullEmailAndAddress() {
        Tenant tenant = Tenant.create(userId, "Lê Văn C", "079111222333", "0912345678", "c@test.com", "Địa chỉ");

        Tenant updated = tenant.updateInfo("Lê Văn C", "079111222333", "0912345678", "   ", "");
        assertNull(updated.email());
        assertNull(updated.permanentAddress());

        Tenant updatedNull = tenant.updateInfo("Lê Văn C", "079111222333", "0912345678", null, null);
        assertNull(updatedNull.email());
        assertNull(updatedNull.permanentAddress());
    }

    @Test
    @DisplayName("TC11: Vòng đời định danh bị xóa theo chính sách lưu trữ (purgedIdentityMarker)")
    void purgedIdentityLifecycle() {
        String marker = Tenant.purgedIdentityMarker(tenantId);
        assertTrue(marker.startsWith(Tenant.PURGED_IDENTITY_PREFIX));
        assertEquals("PURGED-11111111111111111111111111111111", marker);

        Tenant normalTenant = Tenant.create(userId, "Khách Thuê", "079123456789", "0901234567", null, null);
        assertFalse(normalTenant.identityPurged());

        Tenant purgedTenant = new Tenant(
            tenantId,
            userId,
            "Khách Thuê Cũ",
            marker,
            "0901234567",
            null,
            null,
            Instant.now()
        );
        assertTrue(purgedTenant.identityPurged());
    }

    @Test
    @DisplayName("TC12: Che mờ số CCCD: chỉ lộ 4 số cuối hoặc hiển thị 'Đã xóa'")
    void maskedIdentityCardNumberScenarios() {
        Tenant normal = Tenant.create(userId, "Khách Thuê", "079123456789", "0901234567", null, null);
        assertEquals("****6789", normal.maskedIdentityCardNumber());

        Tenant purged = new Tenant(
            tenantId,
            userId,
            "Khách Cũ",
            Tenant.purgedIdentityMarker(tenantId),
            "0901234567",
            null,
            null,
            Instant.now()
        );
        assertEquals("Đã xóa", purged.maskedIdentityCardNumber());

        Tenant shortCccd = new Tenant(tenantId, userId, "Khách Ngắn", "1234", "0901234567", null, null, Instant.now());
        assertEquals("****", shortCccd.maskedIdentityCardNumber());

        Tenant trimmedCccd = new Tenant(tenantId, userId, "Khách Khoảng Trắng", "  079000111222  ", "0901234567", null, null, Instant.now());
        assertEquals("****1222", trimmedCccd.maskedIdentityCardNumber());
    }
}

