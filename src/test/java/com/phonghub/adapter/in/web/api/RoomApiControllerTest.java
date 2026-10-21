package com.phonghub.adapter.in.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.adapter.in.web.api.dto.RoomResponse;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

/** #84: controller chỉ chuyển tham số lọc sang use case, không tự quyết phân quyền. */
class RoomApiControllerTest {

    private final UUID propertyId = UUID.randomUUID();
    private RoomUseCase roomUseCase;
    private RoomApiController controller;

    @BeforeEach
    void setUp() {
        roomUseCase = mock(RoomUseCase.class);
        controller = new RoomApiController(roomUseCase);
    }

    private Room room(String number) {
        return new Room(UUID.randomUUID(), propertyId, number, 1, new BigDecimal("20"), new BigDecimal("3000000"), 2,
            RoomStatus.AVAILABLE, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("không tham số lọc thì dùng danh sách cũ, không có header phân trang")
    void unfilteredUsesLegacyList() {
        when(roomUseCase.listRoomsForProperty(propertyId)).thenReturn(List.of(room("P101"), room("P102")));

        ResponseEntity<List<RoomResponse>> response =
            controller.listRoomsForProperty(propertyId, null, null, null, null, 0, null, false);

        assertEquals(2, response.getBody().size());
        assertNull(response.getHeaders().getFirst("X-Total-Count"));
        verify(roomUseCase, never()).searchRooms(any());
    }

    @Test
    @DisplayName("có tham số lọc thì chuyển đủ điều kiện sang use case và trả header tổng số")
    void filteredDelegatesCriteria() {
        when(roomUseCase.searchRooms(any())).thenReturn(new RoomPage(List.of(room("P104")), 7, 1, 1));

        ResponseEntity<List<RoomResponse>> response = controller.listRoomsForProperty(propertyId,
            RoomStatus.AVAILABLE, new BigDecimal("1000"), new BigDecimal("5000000"), 1, 1, 1, false);

        ArgumentCaptor<RoomSearchCriteria> captor = ArgumentCaptor.forClass(RoomSearchCriteria.class);
        verify(roomUseCase).searchRooms(captor.capture());
        RoomSearchCriteria sent = captor.getValue();
        assertEquals(propertyId, sent.propertyId());
        assertEquals(RoomStatus.AVAILABLE, sent.status());
        assertEquals(new BigDecimal("1000"), sent.minPrice());
        assertEquals(new BigDecimal("5000000"), sent.maxPrice());
        assertEquals(1, sent.floor());
        assertEquals(1, sent.page());
        assertEquals(1, sent.size());
        assertNull(sent.restrictToRoomIds(), "controller không được tự nới/giới hạn phạm vi phòng");
        assertEquals("7", response.getHeaders().getFirst("X-Total-Count"));
        assertEquals("1", response.getHeaders().getFirst("X-Page"));
        assertEquals("P104", response.getBody().get(0).roomNumber());
        verify(roomUseCase, never()).listRoomsForProperty(any());
    }

    @Test
    @DisplayName("tham số sai bị chặn trước khi gọi use case")
    void invalidParamsRejectedBeforeUseCase() {
        assertThrows(IllegalArgumentException.class, () -> controller.listRoomsForProperty(propertyId, null,
            new BigDecimal("9"), new BigDecimal("1"), null, 0, null, false));
        assertThrows(IllegalArgumentException.class, () -> controller.listRoomsForProperty(propertyId, null,
            null, null, null, 2, null, false));
        verify(roomUseCase, never()).searchRooms(any());
        verify(roomUseCase, never()).listRoomsForProperty(any());
    }

    @Test
    @DisplayName("lỗi phân quyền từ use case được ném nguyên vẹn để trả 403")
    void authorizationErrorPropagates() {
        when(roomUseCase.searchRooms(any())).thenThrow(new UnauthorizedPropertyAccessException("no"));

        assertThrows(UnauthorizedPropertyAccessException.class, () -> controller.listRoomsForProperty(propertyId,
            RoomStatus.AVAILABLE, null, null, null, 0, null, false));
    }
}
