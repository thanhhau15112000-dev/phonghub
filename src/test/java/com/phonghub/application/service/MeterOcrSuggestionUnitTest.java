package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.application.port.out.MeterOcrPort;
import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeterOcrSuggestionUnitTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final MeterOcrPort ocrPort = mock(MeterOcrPort.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final UUID roomId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), "owner@local", "Owner", UserRole.OWNER);
    private UtilityService service;

    @BeforeEach
    void setUp() {
        RoomRepositoryPort rooms = mock(RoomRepositoryPort.class);
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getPropertyId()).thenReturn(propertyId);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        CurrentUserPort currentUser = mock(CurrentUserPort.class);
        when(currentUser.getCurrentUser()).thenReturn(owner);
        service = new UtilityService(mock(MeterReadingRepositoryPort.class), mock(ServiceRateRepositoryPort.class),
            rooms, mock(ContractRepositoryPort.class), mock(HandoverRepositoryPort.class),
            mock(FileStoragePort.class), currentUser, authorization, mock(AuditPort.class), ocrPort,
            new BigDecimal("0.85"), 1024 * 1024);
    }

    private UtilityUseCase.OcrSuggestion read(String value, String confidence) {
        when(ocrPort.read(any(), any(), any())).thenReturn(new MeterOcrPort.OcrResult(
            value == null ? null : new BigDecimal(value), new BigDecimal(confidence), "stub"));
        return service.readMeterPhoto(roomId, MeterType.ELECTRIC, "image/png", PNG);
    }

    @Test
    @DisplayName("Confidence at or above the threshold returns the value for the user to confirm")
    void confidentReadingIsSuggested() {
        UtilityUseCase.OcrSuggestion suggestion = read("1234.5", "0.85");
        assertFalse(suggestion.requiresManualEntry());
        assertEquals(new BigDecimal("1234.5"), suggestion.value());
    }

    @Test
    @DisplayName("Low confidence or no value hides the number and forces manual entry")
    void lowConfidenceForcesManualEntry() {
        UtilityUseCase.OcrSuggestion low = read("1234.5", "0.84");
        assertTrue(low.requiresManualEntry());
        assertNull(low.value());
        assertTrue(read(null, "0.99").requiresManualEntry());
    }

    @Test
    @DisplayName("Users who cannot manage the property are rejected before OCR runs")
    void unauthorizedUserIsRejected() {
        org.mockito.Mockito.doThrow(new UnauthorizedPropertyAccessException("no access"))
            .when(authorization).assertCanManageProperty(owner, propertyId);
        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> service.readMeterPhoto(roomId, MeterType.ELECTRIC, "image/png", PNG));
        org.mockito.Mockito.verifyNoInteractions(ocrPort);
    }
}
