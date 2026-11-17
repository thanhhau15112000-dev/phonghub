package com.phonghub.migration;

import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.TenantDataProtectionPort;

/** Uses the one test key source configured for forked Maven tests in pom.xml. */
final class TestTenantDataProtection {

    private TestTenantDataProtection() {}

    static TenantDataProtectionPort createProtector() {
        return new AesGcmTenantDataProtector(
            requiredEnvironmentVariable("TENANT_DATA_KEY_ID"),
            requiredEnvironmentVariable("TENANT_DATA_ENCRYPTION_KEY"),
            requiredEnvironmentVariable("TENANT_DATA_HMAC_KEY"));
    }

    static String keyId() {
        return requiredEnvironmentVariable("TENANT_DATA_KEY_ID");
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Maven Surefire must provide " + name + " for migration tests");
        }
        return value;
    }
}
