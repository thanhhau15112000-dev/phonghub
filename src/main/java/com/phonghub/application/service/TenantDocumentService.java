package com.phonghub.application.service;

import com.phonghub.application.port.in.TenantDocumentUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.DocumentRevealDeniedException;
import com.phonghub.domain.exception.DuplicateDocumentNumberException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.exception.TenantDocumentNotFoundException;
import com.phonghub.domain.exception.TenantNotFoundException;
import com.phonghub.domain.exception.TooManyRevealAttemptsException;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.TenantDocument;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TenantDocumentService implements TenantDocumentUseCase {

    static final int MAX_FAILED_REVEALS = 5;
    static final Duration REVEAL_WINDOW = Duration.ofMinutes(10);
    private static final String ENCRYPTED_CONTENT_TYPE = "application/octet-stream";

    private final TenantDocumentRepositoryPort documentRepository;
    private final TenantRepositoryPort tenantRepository;
    private final TenantDataProtectionPort protector;
    private final FileStoragePort storagePort;
    private final IdentityProviderPort identityProvider;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final long maxFileSizeBytes;
    private final Clock clock;
    private final Map<UUID, Deque<Instant>> failedReveals = new ConcurrentHashMap<>();

    public TenantDocumentService(
        TenantDocumentRepositoryPort documentRepository,
        TenantRepositoryPort tenantRepository,
        TenantDataProtectionPort protector,
        FileStoragePort storagePort,
        IdentityProviderPort identityProvider,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        long maxFileSizeBytes,
        Clock clock
    ) {
        this.documentRepository = documentRepository;
        this.tenantRepository = tenantRepository;
        this.protector = protector;
        this.storagePort = storagePort;
        this.identityProvider = identityProvider;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.clock = clock;
    }

    @Override
    public List<DocumentSummary> list(UUID propertyId, UUID tenantId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenantDocuments(user, propertyId, tenantId);
        Tenant tenant = findTenant(tenantId);
        List<DocumentSummary> summaries = documentRepository.findByTenantId(tenantId).stream()
            .sorted(Comparator.comparing(TenantDocument::type))
            .map(document -> summarize(document, tenant))
            .toList();
        audit("TENANT_DOCUMENTS_VIEWED", user, tenantId, Map.of("count", summaries.size()));
        return summaries;
    }

    @Override
    public DocumentSummary view(UUID propertyId, UUID tenantId, DocumentType type) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenantDocuments(user, propertyId, tenantId);
        Tenant tenant = findTenant(tenantId);
        TenantDocument document = findDocument(tenantId, type);
        audit("TENANT_DOCUMENT_VIEWED", user, tenantId, Map.of("type", type.name()));
        return summarize(document, tenant);
    }

    @Override
    public DocumentSummary upload(UploadDocumentCommand command) {
        if (command == null || command.type() == null) {
            throw new IllegalArgumentException("Document type is required");
        }
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenantDocuments(user, command.propertyId(), command.tenantId());
        Tenant tenant = findTenant(command.tenantId());

        Prepared front = prepareImage(command.front(), true);
        Prepared back = prepareImage(command.back(), false);
        TenantDataProtectionPort.ProtectedValue number = protectNumber(command, tenant);

        TenantDocument previous = documentRepository.findByTenantIdAndType(command.tenantId(), command.type())
            .orElse(null);
        String frontKey = store(command.propertyId(), front);
        String backKey = back == null ? null : store(command.propertyId(), back);
        Instant now = clock.instant();
        TenantDocument saved;
        try {
            saved = documentRepository.save(new TenantDocument(
                previous != null ? previous.id() : UUID.randomUUID(),
                command.tenantId(),
                command.propertyId(),
                command.type(),
                number == null ? null : number.ciphertext(),
                number == null ? null : number.nonce(),
                number == null ? null : number.fingerprint(),
                number == null ? null : number.keyId(),
                lastFour(command, tenant),
                frontKey,
                front.contentType(),
                backKey,
                back == null ? null : back.contentType(),
                previous != null ? previous.createdAt() : now,
                previous != null ? previous.createdBy() : user.id(),
                now,
                user.id()
            ));
        } catch (RuntimeException ex) {
            deleteQuietly(frontKey);
            deleteQuietly(backKey);
            throw ex;
        }
        if (previous != null) {
            deleteQuietly(previous.frontImageKey());
            deleteQuietly(previous.backImageKey());
        }
        audit("TENANT_DOCUMENT_UPLOADED", user, command.tenantId(),
            Map.of("type", command.type().name(), "replaced", previous != null));
        return summarize(saved, tenant);
    }

    @Override
    public RevealedDocument reveal(UUID propertyId, UUID tenantId, DocumentType type, String password) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanAccessTenantDocuments(user, propertyId, tenantId);
        assertNotThrottled(user.id());

        if (!passwordMatches(user, password)) {
            recordFailure(user.id());
            audit("TENANT_DOCUMENT_REVEAL_FAILED", user, tenantId, Map.of("type", type.name()));
            throw new DocumentRevealDeniedException();
        }
        failedReveals.remove(user.id());

        Tenant tenant = findTenant(tenantId);
        TenantDocument document = findDocument(tenantId, type);
        RevealedDocument revealed = new RevealedDocument(
            type,
            type == DocumentType.CCCD ? tenant.identityCardNumber() : protector.reveal(
                new TenantDataProtectionPort.ProtectedValue(document.numberCiphertext(), document.numberNonce(),
                    document.numberHmac(), document.numberKeyId())),
            decryptImage(document.frontImageKey(), document.frontContentType()),
            document.hasBackImage() ? decryptImage(document.backImageKey(), document.backContentType()) : null
        );
        audit("TENANT_DOCUMENT_REVEALED", user, tenantId, Map.of("type", type.name()));
        return revealed;
    }

    @Override
    public void delete(UUID propertyId, UUID tenantId, DocumentType type) {
        CurrentUser user = currentUserPort.getCurrentUser();
        authorizationService.assertCanDeleteTenantDocuments(user, propertyId, tenantId);
        TenantDocument document = findDocument(tenantId, type);
        documentRepository.delete(tenantId, type);
        deleteQuietly(document.frontImageKey());
        deleteQuietly(document.backImageKey());
        audit("TENANT_DOCUMENT_DELETED", user, tenantId, Map.of("type", type.name()));
    }

    private boolean passwordMatches(CurrentUser user, String password) {
        if (password == null || password.isBlank()) {
            return false;
        }
        try {
            identityProvider.login(user.email(), password);
            return true;
        } catch (InvalidCredentialsException ex) {
            return false;
        }
    }

    private void assertNotThrottled(UUID userId) {
        Deque<Instant> failures = failedReveals.get(userId);
        if (failures == null) {
            return;
        }
        synchronized (failures) {
            Instant cutoff = clock.instant().minus(REVEAL_WINDOW);
            while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
                failures.pollFirst();
            }
            if (failures.size() >= MAX_FAILED_REVEALS) {
                throw new TooManyRevealAttemptsException();
            }
        }
    }

    private void recordFailure(UUID userId) {
        Deque<Instant> failures = failedReveals.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (failures) {
            failures.addLast(clock.instant());
        }
    }

    private Prepared prepareImage(DocumentFile file, boolean required) {
        if (file == null || file.content() == null || file.content().length == 0) {
            if (required) {
                throw new IllegalArgumentException("Front image of the document is required");
            }
            return null;
        }
        if (!FileSanitizer.isImage(file.contentType())) {
            throw new IllegalArgumentException("Document images must be jpeg, png or webp");
        }
        FileSanitizer.SanitizedFile sanitized = FileSanitizer.sanitize(
            file.contentType(), file.content(), maxFileSizeBytes);
        return new Prepared(sanitized.contentType(), sanitized.content());
    }

    private TenantDataProtectionPort.ProtectedValue protectNumber(UploadDocumentCommand command, Tenant tenant) {
        if (command.type() == DocumentType.CCCD) {
            return null;
        }
        if (command.number() == null || command.number().isBlank()) {
            throw new IllegalArgumentException("Driving licence number is required");
        }
        TenantDataProtectionPort.ProtectedValue value = protector.protect(command.number());
        if (documentRepository.existsByTypeAndNumberHmacForOtherTenant(
            command.type(), value.fingerprint(), tenant.id())) {
            throw new DuplicateDocumentNumberException();
        }
        return value;
    }

    private String lastFour(UploadDocumentCommand command, Tenant tenant) {
        String number = command.type() == DocumentType.CCCD
            ? tenant.identityCardNumber()
            : command.number().trim().replaceAll("\\s+", "");
        return number.length() <= 4 ? number : number.substring(number.length() - 4);
    }

    private String store(UUID propertyId, Prepared image) {
        String key = PrivateFileStorageService.objectKey(propertyId, "tenant-documents", "enc");
        storagePort.put(key, ENCRYPTED_CONTENT_TYPE, protector.encryptBytes(image.content()));
        return key;
    }

    private DocumentFile decryptImage(String key, String contentType) {
        return new DocumentFile(contentType, protector.decryptBytes(storagePort.get(key)));
    }

    private void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            storagePort.delete(key);
        } catch (RuntimeException ignored) {
            // best effort: orphaned encrypted blobs are unreadable without the key
        }
    }

    private DocumentSummary summarize(TenantDocument document, Tenant tenant) {
        String masked = document.type() == DocumentType.CCCD ? tenant.maskedIdentityCardNumber()
            : document.maskedNumber();
        return new DocumentSummary(document.type(), masked, true, document.hasBackImage(), document.updatedAt());
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
    }

    private TenantDocument findDocument(UUID tenantId, DocumentType type) {
        return documentRepository.findByTenantIdAndType(tenantId, type)
            .orElseThrow(() -> new TenantDocumentNotFoundException("Document " + type + " not found for tenant"));
    }

    private void audit(String action, CurrentUser user, UUID tenantId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>(details);
        payload.put("tenantId", tenantId.toString());
        auditPort.recordEvent(AuditPort.AuditEvent.of(action, user.id(), "TENANT_DOCUMENT", tenantId.toString(), payload));
    }

    private record Prepared(String contentType, byte[] content) {}
}
