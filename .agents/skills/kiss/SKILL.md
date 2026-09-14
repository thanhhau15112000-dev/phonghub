---
name: kiss
description: Keep It Simple, Stupid (KISS) principle for software design and code implementation. Automatically and by default enforces minimal cognitive complexity, direct solutions, shallow abstractions, flat control flow, and zero unnecessary layers.
metadata:
  origin: phonghub
---

# KISS Principle (Keep It Simple, Stupid)

Quy chuẩn kỹ thuật áp dụng nguyên lý **KISS** trong toàn bộ quá trình thiết kế, triển khai mã nguồn và review tại repository `phonghub`. **Quy chuẩn này mặc định áp dụng tự động trên mọi dòng code, không cần nhắc lại.**

---

## I. Mục Tiêu Cốt Lõi (Core Objective)

> **"Đơn giản không phải là tầm thường; đơn giản là sự chuẩn xác và trực diện nhất để giải quyết vấn đề."**

1. **Giảm thiểu Cognitive Load (Tải nhận thức):** Một lập trình viên mới hoặc AI đọc vào bất kỳ class, method hay flow nào đều có thể hiểu đúng bản chất nghiệp vụ trong vòng dưới 30 giây mà không cần phải mở cùng lúc 4-5 file trung gian.
2. **Loại bỏ Accidental Complexity (Độ phức tạp phát sinh ngoài ý muốn):** Chỉ giải quyết bài toán nghiệp vụ thực tế, không đưa thêm các tầng trừu tượng, wrapper hoặc pattern phức tạp nếu giải pháp trực tiếp đã đáp ứng đầy đủ yêu cầu và invariant.
3. **Dễ đọc, Dễ kiểm thử, Dễ gỡ lỗi:** Mã nguồn đơn giản sẽ có ít điểm lỗi (failure points), đường đi kiểm thử (cyclomatic complexity) thấp và log/stacktrace ngắn gọn, dễ truy vết.

---

## II. Bộ Quy Tắc Thực Thi Mặc Định (Default Enforcement Rules)

### 1. Shallow Abstraction (Trừu tượng hóa nông)
- **Quy tắc:** Ưu tiên cấu trúc phẳng. Tránh tạo chuỗi kế thừa sâu (không kế thừa quá 1 cấp; nếu cần chia sẻ logic, ưu tiên composition hoặc static helper thuần túy).
- **Cấm:** Không xây dựng hệ thống Generic lồng nhau kiểu `BaseGenericService<T, ID, REPO, MAPPER, DTO>` chỉ để "tái sử dụng vài hàm CRUD". Tái sử dụng giả tạo sẽ khóa chặt codebase vào các ràng buộc cứng nhắc khó mở rộng.

### 2. Flat Control Flow & Guard Clauses (Luồng phẳng & Trả về sớm)
- **Quy tắc:** Kiểm tra điều kiện tiên quyết (pre-conditions), validation lỗi và trả về ngay từ đầu hàm (Early Return).
- **Tiêu chuẩn:** Mức lồng ghép logic (`if / else / switch / for`) **tối đa không quá 2 cấp**. Nếu vượt quá 2 cấp, bắt buộc phải refactor bằng cách trích xuất hàm con hoặc chuẩn hóa thành Guard Clause.

### 3. Direct Implementation First (Ưu tiên giải pháp trực diện)
- Viết code trực diện nhất để vượt qua bài kiểm tra và đáp ứng tiêu chí nghiệm thu (Acceptance Criteria).
- Không vội vàng tạo thêm class trung gian (Helper, Transformer, Manager, Processor...) nếu logic chỉ gói gọn trong 10-20 dòng và chỉ phục vụ duy nhất một chỗ.

### 4. Self-Documenting Naming (Đặt tên rõ ràng thay vì viết comment giải thích)
- Tên class, record, method và biến phải mô tả chính xác mục đích và hành vi kỹ thuật.
- Tránh viết tắt vô nghĩa (`r`, `tmp`, `hdl`, `proc`).
- Nếu code cần một đoạn comment dài dòng để giải thích "nó đang làm cái gì", đó là dấu hiệu code quá phức tạp — hãy đơn giản hóa code thay vì viết thêm comment.

### 5. No Framework/Library Magic (Hạn chế lạm dụng ma thuật ngầm)
- Ưu tiên Java chuẩn (Standard Java), Constructor Injection tường minh thay vì Reflection, Custom Annotation phức tạp hoặc Dynamic Bytecode Manipulation khi không thực sự cần thiết.

---

## III. Minh Họa Đối Chiếu (Before vs After)

### Tình huống: Kiểm tra điều kiện và kích hoạt phòng trọ

#### ❌ Before (Vi phạm KISS - Over-engineered, kế thừa sâu, lồng if)
```java
// Kế thừa Generic phức tạp, che giấu logic
public class RoomActivationManager extends AbstractBaseLifecycleManager<Room, RoomId, RoomRepository> {

    public BaseResult<RoomResponse> executeActivationFlow(RoomActivationContext context) {
        if (context != null) {
            if (context.getRoomId() != null) {
                Optional<Room> optionalRoom = repository.findById(context.getRoomId());
                if (optionalRoom.isPresent()) {
                    Room room = optionalRoom.get();
                    if (room.getStatus() == RoomStatus.PENDING) {
                        room.setStatus(RoomStatus.ACTIVE);
                        repository.save(room);
                        return BaseResult.success(mapper.toResponse(room));
                    } else {
                        return BaseResult.failed("INVALID_STATUS");
                    }
                } else {
                    return BaseResult.failed("ROOM_NOT_FOUND");
                }
            } else {
                return BaseResult.failed("ID_NULL");
            }
        }
        return BaseResult.failed("NULL_CONTEXT");
    }
}
```

#### ✅ After (Tuân thủ KISS - Trực diện, phẳng, Guard Clauses, dễ test)
```java
@Service
@Transactional
public class ActivateRoomService implements ActivateRoomUseCase {

    private final LoadRoomPort loadRoomPort;
    private final SaveRoomPort saveRoomPort;

    public ActivateRoomService(LoadRoomPort loadRoomPort, SaveRoomPort saveRoomPort) {
        this.loadRoomPort = loadRoomPort;
        this.saveRoomPort = saveRoomPort;
    }

    @Override
    public void activate(RoomId roomId) {
        Room room = loadRoomPort.loadRoom(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

        room.activate(); // Invariant và logic kiểm tra trạng thái nằm gọn trong domain entity

        saveRoomPort.saveRoom(room);
    }
}
```

---

## IV. Danh Sách Anti-Patterns Cần Tránh

1. **One-line wrapper methods:** Method chỉ làm đúng 1 việc là gọi method khác cùng kiểu tham số mà không thêm bất kỳ giá trị hay chuyển đổi logic nào.
2. **Architecture Astronaut:** Thiết kế hệ sinh thái class đồ sộ (Factory, Facade, Strategy, Observer) cho một ứng dụng quản lý phòng trọ quy mô vừa và nhỏ.
3. **Boolean Flag Soup:** Truyền 3-4 cờ boolean vào một hàm để điều khiển các nhánh logic rẽ nhánh khác nhau thay vì tách thành các hàm rõ nghĩa riêng biệt.
