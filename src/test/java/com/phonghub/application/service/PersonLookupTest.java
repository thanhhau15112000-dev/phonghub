package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonLookupTest {

    private static User user(String username, String email, String fullName, String phone) {
        return new User(UUID.randomUUID(), username, email, fullName, phone, UserRole.STAFF, User.UserStatus.ACTIVE,
            false, Instant.now());
    }

    @Test
    @DisplayName("Email and phone are masked the way the picker shows them")
    void masking() {
        assertEquals("ng***@gmail.com", PersonLookup.maskEmail("nguyenvana@gmail.com"));
        assertEquals("a***@x.vn", PersonLookup.maskEmail("ab@x.vn"));
        assertEquals("", PersonLookup.maskEmail(null));
        assertEquals("***", PersonLookup.maskEmail("khong-co-a-cong"));
        assertEquals("09******12", PersonLookup.maskPhone("0912345612"));
        assertEquals("09******12", PersonLookup.maskPhone("0912 345 612"));
        assertEquals("***", PersonLookup.maskPhone("123"));
        assertEquals("", PersonLookup.maskPhone(null));
        assertEquals("ng***@gmail.com", PersonLookup.maskedContact(user("u", "nguyenvana@gmail.com", "Tên", "0912345612")));
    }

    @Test
    @DisplayName("Matching ignores accents and case; phone matches only on the last 3-4 digits")
    void matching() {
        User staff = user("staff2", "staff2@phonghub.local", "Nhân viên Tân Bình", "0900000003");
        assertTrue(PersonLookup.matches(staff, ""));
        assertTrue(PersonLookup.matches(staff, null));
        assertTrue(PersonLookup.matches(staff, "tan binh"));
        assertTrue(PersonLookup.matches(staff, "  NHÂN VIÊN "));
        assertTrue(PersonLookup.matches(staff, "STAFF2"));
        assertTrue(PersonLookup.matches(staff, "@phonghub.local"));
        assertTrue(PersonLookup.matches(staff, "0003"));
        assertTrue(PersonLookup.matches(staff, "003"));
        assertFalse(PersonLookup.matches(staff, "0900"));
        assertFalse(PersonLookup.matches(staff, "0900000003"));
        assertFalse(PersonLookup.matches(staff, "quan 7"));
        assertTrue(PersonLookup.matches(user("dd", "dd@x.vn", "Đỗ Đức", null), "do duc"));
    }

    @Test
    @DisplayName("Limit falls back to 20 and is capped at 50")
    void limit() {
        assertEquals(20, PersonLookup.clampLimit(0));
        assertEquals(20, PersonLookup.clampLimit(-5));
        assertEquals(7, PersonLookup.clampLimit(7));
        assertEquals(50, PersonLookup.clampLimit(500));
    }
}
