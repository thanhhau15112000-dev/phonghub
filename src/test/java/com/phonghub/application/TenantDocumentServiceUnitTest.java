package com.phonghub.application;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantDocumentRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.adapter.out.storage.InMemoryFileStorageAdapter;
import com.phonghub.application.port.in.TenantDocumentUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.TenantDocumentService;
import com.phonghub.application.service.TenantLockPolicy;
import com.phonghub.domain.exception.DuplicateDocumentNumberException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.exception.TenantProfileLockedException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantDocumentServiceUnitTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final UUID propertyId = UUID.randomUUID();
    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), "o@x.vn", "Owner", UserRole.OWNER);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-09T10:00:00Z"));

    private InMemoryTenantDocumentRepository documents;
    private InMemoryFileStorageAdapter storage;
    private AesGcmTenantDataProtector protector;
    private TenantRepositoryPort tenants;
    private IdentityProviderPort identity;
    private AuthorizationService authorization;
    private CurrentUserPort currentUser;
    private TenantDocumentService service;
    private Tenant tenant;
    private Tenant otherTenant;

    @BeforeEach
    void setUp() {
        documents = new InMemoryTenantDocumentRepository();
        storage = new InMemoryFileStorageAdapter();
        protector = new AesGcmTenantDataProtector("v1",
            Base64.getEncoder().encodeToString(new byte[32]),
            Base64.getEncoder().encodeToString(new byte[]{
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32}));
        tenants = mock(TenantRepositoryPort.class);
        identity = mock(IdentityProviderPort.class);
        authorization = mock(AuthorizationService.class);
        currentUser = mock(CurrentUserPort.class);
        tenant = new Tenant(UUID.randomUUID(), null, "Nguyen A", "079201001111", "0900000001", null, null, Instant.now());
        otherTenant = new Tenant(UUID.randomUUID(), null, "Tran B", "079201002222", "0900000002", null, null, Instant.now());
        when(tenants.findById(tenant.id())).thenReturn(Optional.of(tenant));
        when(tenants.findById(otherTenant.id())).thenReturn(Optional.of(otherTenant));
        when(currentUser.getCurrentUser()).thenReturn(owner);
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        service = new TenantDocumentService(documents, tenants, protector, storage, identity, currentUser,
            authorization, mock(AuditPort.class), 1024 * 1024, clock);
    }

    private TenantDocumentUseCase.UploadDocumentCommand gplx(Tenant forTenant, String number) {
        return new TenantDocumentUseCase.UploadDocumentCommand(propertyId, forTenant.id(), DocumentType.GPLX, number,
            new TenantDocumentUseCase.DocumentFile("image/png", PNG), null);
    }

    @Test
    void storedImageAndNumberAreEncryptedAtRest() throws Exception {
        service.upload(gplx(tenant, "012345678901"));

        var stored = documents.findByTenantIdAndType(tenant.id(), DocumentType.GPLX).orElseThrow();
        byte[] blob = storage.get(stored.frontImageKey());
        assertFalse(Arrays.equals(PNG, blob));
        assertNotEquals((byte) 0x89, blob[0], "stored blob must not start with the PNG signature");
        assertFalse(new String(blob, StandardCharsets.ISO_8859_1).contains("PNG"));
        assertFalse(stored.numberCiphertext().contains("012345678901"));
        assertEquals("****8901", stored.maskedNumber());
        assertTrue(stored.frontImageKey().endsWith(".enc"));
    }

    @Test
    void revealDecryptsNumberAndImagesAfterCorrectPassword() {
        service.upload(gplx(tenant, "012345678901"));

        var revealed = service.reveal(propertyId, tenant.id(), DocumentType.GPLX, "correct-horse");

        assertEquals("012345678901", revealed.number());
        assertEquals("image/png", revealed.front().contentType());
        assertEquals((byte) 0x89, revealed.front().content()[0]);
    }

    @Test
    void duplicateDrivingLicenceNumberForAnotherTenantIsRejected() {
        service.upload(gplx(tenant, "012 345 678 901"));

        assertThrows(DuplicateDocumentNumberException.class, () -> service.upload(gplx(otherTenant, "012345678901")));
        service.upload(gplx(tenant, "012345678901"));
    }

    @Test
    void replacingADocumentDeletesTheOldEncryptedBlob() {
        service.upload(gplx(tenant, "012345678901"));
        String oldKey = documents.findByTenantIdAndType(tenant.id(), DocumentType.GPLX).orElseThrow().frontImageKey();

        service.upload(gplx(tenant, "012345678901"));

        String newKey = documents.findByTenantIdAndType(tenant.id(), DocumentType.GPLX).orElseThrow().frontImageKey();
        assertNotEquals(oldKey, newKey);
        assertThrows(IllegalArgumentException.class, () -> storage.get(oldKey));
        assertEquals(1, documents.findByTenantId(tenant.id()).size());
    }

    @Test
    void unauthorizedCallerNeverReachesStorageOrDatabase() {
        org.mockito.Mockito.doThrow(new UnauthorizedPropertyAccessException("no")).when(authorization)
            .assertCanAccessTenantDocuments(any(), any(), any());

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.upload(gplx(tenant, "012345678901")));
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.reveal(propertyId, tenant.id(), DocumentType.GPLX, "correct-horse"));
        assertTrue(documents.findByTenantId(tenant.id()).isEmpty());
    }

    @Test
    void failedAttemptsExpireAfterTheWindow() {
        service.upload(gplx(tenant, "012345678901"));
        when(identity.login(any(), any())).thenThrow(new InvalidCredentialsException("bad"));
        for (int i = 0; i < 5; i++) {
            assertThrows(com.phonghub.domain.exception.DocumentRevealDeniedException.class,
                () -> service.reveal(propertyId, tenant.id(), DocumentType.GPLX, "bad"));
        }
        assertThrows(com.phonghub.domain.exception.TooManyRevealAttemptsException.class,
            () -> service.reveal(propertyId, tenant.id(), DocumentType.GPLX, "bad"));

        now.set(now.get().plus(Duration.ofMinutes(11)));
        org.mockito.Mockito.reset(identity);
        assertArrayEquals(PNG.length > 0 ? new byte[]{(byte) 0x89} : new byte[0],
            new byte[]{service.reveal(propertyId, tenant.id(), DocumentType.GPLX, "good-password")
                .front().content()[0]});
    }

    @Test
    void missingNumberForDrivingLicenceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.upload(gplx(tenant, " ")));
        assertTrue(documents.findByTenantId(tenant.id()).isEmpty());
    }

    private static final TenantDocumentUseCase.DocumentFile PNG_FILE =
        new TenantDocumentUseCase.DocumentFile("image/png", PNG);
    private final CurrentUser tenantUser = new CurrentUser(UUID.randomUUID(), "t@x.vn", "Tenant", UserRole.TENANT);

    private void signInAsTenant(Tenant profile) {
        when(currentUser.getCurrentUser()).thenReturn(tenantUser);
        when(tenants.findByUserId(tenantUser.id())).thenReturn(Optional.ofNullable(profile));
    }

    @Test
    void tenantUploadsOwnIdentityCardBeforeAnyContract() {
        signInAsTenant(tenant);

        var summary = service.uploadOwnIdentityCard(PNG_FILE, PNG_FILE);

        var stored = documents.findByTenantIdAndType(tenant.id(), DocumentType.CCCD).orElseThrow();
        assertNull(stored.propertyId(), "uploaded from the profile, not tied to any property");
        assertTrue(stored.frontImageKey().startsWith("tenants/" + tenant.id() + "/tenant-documents/"));
        assertTrue(stored.hasBackImage());
        assertFalse(Arrays.equals(PNG, storage.get(stored.frontImageKey())), "image is encrypted at rest");
        assertEquals("****1111", summary.maskedNumber());
        var own = service.ownDocuments();
        assertTrue(own.profileLinked());
        assertEquals(List.of(DocumentType.CCCD), own.documents().stream()
            .map(TenantDocumentUseCase.DocumentSummary::type).toList());
        verifyNoInteractions(authorization);
    }

    @Test
    void identityCardFromProfileIsListedForThePropertyOfALaterContract() {
        signInAsTenant(tenant);
        service.uploadOwnIdentityCard(PNG_FILE, null);

        when(currentUser.getCurrentUser()).thenReturn(owner);
        var listed = service.list(propertyId, tenant.id());

        assertEquals(1, listed.size());
        assertEquals(DocumentType.CCCD, listed.getFirst().type());
    }

    @Test
    void replacingFromProfileKeepsThePropertyWhereItWasFirstUploaded() {
        service.upload(new TenantDocumentUseCase.UploadDocumentCommand(propertyId, tenant.id(), DocumentType.CCCD,
            null, PNG_FILE, null));
        String oldKey = documents.findByTenantIdAndType(tenant.id(), DocumentType.CCCD).orElseThrow().frontImageKey();
        signInAsTenant(tenant);

        service.uploadOwnIdentityCard(PNG_FILE, null);

        var stored = documents.findByTenantIdAndType(tenant.id(), DocumentType.CCCD).orElseThrow();
        assertEquals(propertyId, stored.propertyId());
        assertTrue(stored.frontImageKey().startsWith("properties/" + propertyId + "/"));
        assertThrows(IllegalArgumentException.class, () -> storage.get(oldKey), "old blob is deleted");
        assertEquals(1, documents.findByTenantId(tenant.id()).size());
    }

    @Test
    void tenantAccountWithoutLinkedProfileCannotUpload() {
        signInAsTenant(null);

        assertFalse(service.ownDocuments().profileLinked());
        assertThrows(IllegalArgumentException.class, () -> service.uploadOwnIdentityCard(PNG_FILE, null));
    }

    @Test
    void onlyTenantsUseTheProfileUpload() {
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.uploadOwnIdentityCard(PNG_FILE, null));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.ownDocuments());
        assertTrue(documents.findByTenantId(tenant.id()).isEmpty());
    }

    @Test
    void missingFrontImageIsRejected() {
        signInAsTenant(tenant);

        assertThrows(IllegalArgumentException.class, () -> service.uploadOwnIdentityCard(null, PNG_FILE));
        assertTrue(documents.findByTenantId(tenant.id()).isEmpty());
    }

    @Test
    void lockedProfileCannotUploadFromTheProfilePage() {
        TenantLockPolicy lockPolicy = mock(TenantLockPolicy.class);
        doThrow(new TenantProfileLockedException("locked")).when(lockPolicy).assertNotLocked(tenant.id());
        TenantDocumentService lockedService = new TenantDocumentService(documents, tenants, protector, storage,
            identity, currentUser, authorization, mock(AuditPort.class), 1024 * 1024, Clock.systemUTC(), lockPolicy);
        signInAsTenant(tenant);

        assertThrows(TenantProfileLockedException.class, () -> lockedService.uploadOwnIdentityCard(PNG_FILE, null));
        assertTrue(documents.findByTenantId(tenant.id()).isEmpty());
    }

    @Test
    void tenantUploadsOwnDriverLicenseFromProfile() {
        signInAsTenant(tenant);

        var summary = service.uploadOwnDriverLicense("123456789", PNG_FILE, PNG_FILE);

        var stored = documents.findByTenantIdAndType(tenant.id(), DocumentType.GPLX).orElseThrow();
        assertNull(stored.propertyId(), "uploaded from the profile, not tied to any property");
        assertTrue(stored.frontImageKey().startsWith("tenants/" + tenant.id() + "/tenant-documents/"));
        assertTrue(stored.hasBackImage());
        assertFalse(Arrays.equals(PNG, storage.get(stored.frontImageKey())), "image is encrypted at rest");
        assertEquals("****6789", summary.maskedNumber());
        var own = service.ownDocuments();
        assertTrue(own.profileLinked());
        assertEquals(List.of(DocumentType.GPLX), own.documents().stream()
            .map(TenantDocumentUseCase.DocumentSummary::type).toList());
        verifyNoInteractions(authorization);
    }

    @Test
    void driverLicenseFromProfileIsListedForThePropertyOfALaterContract() {
        signInAsTenant(tenant);
        service.uploadOwnDriverLicense("123456789", PNG_FILE, null);

        when(currentUser.getCurrentUser()).thenReturn(owner);
        var listed = service.list(propertyId, tenant.id());

        assertEquals(1, listed.size());
        assertEquals(DocumentType.GPLX, listed.getFirst().type());
        assertEquals("****6789", listed.getFirst().maskedNumber());
    }

    @Test
    void replacingDriverLicenseCanKeepOrUpdateNumber() {
        signInAsTenant(tenant);
        service.uploadOwnDriverLicense("123456789", PNG_FILE, null);

        // Replace keeping same number by passing null/blank
        var replacedKeep = service.uploadOwnDriverLicense(null, PNG_FILE, null);
        assertEquals("****6789", replacedKeep.maskedNumber());

        // Replace with new number
        var replacedNew = service.uploadOwnDriverLicense("987654321", PNG_FILE, null);
        assertEquals("****4321", replacedNew.maskedNumber());
    }

    @Test
    void newDriverLicenseRequiresNumber() {
        signInAsTenant(tenant);
        assertThrows(IllegalArgumentException.class, () -> service.uploadOwnDriverLicense(null, PNG_FILE, null));
        assertThrows(IllegalArgumentException.class, () -> service.uploadOwnDriverLicense("   ", PNG_FILE, null));
    }

    @Test
    void driverLicenseRejectsDuplicateNumberForOtherTenant() {
        signInAsTenant(tenant);
        service.uploadOwnDriverLicense("123456789", PNG_FILE, null);

        signInAsTenant(otherTenant);
        assertThrows(DuplicateDocumentNumberException.class,
            () -> service.uploadOwnDriverLicense("123 456 789", PNG_FILE, null));
    }
}
