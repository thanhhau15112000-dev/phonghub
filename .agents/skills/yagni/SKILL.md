---
name: yagni
description: You Aren't Gonna Need It (YAGNI) principle for backend engineering. Automatically and by default prohibits speculative generalizations, unused abstractions, unrequested extension points, future-proofing configurations, and dead code.
metadata:
  origin: phonghub
---

# YAGNI Principle (You Aren't Gonna Need It)

Quy chuẩn kỹ thuật áp dụng nguyên lý **YAGNI** trong việc xác định phạm vi, thiết kế và phát triển tính năng tại repository `phonghub`. **Quy chuẩn này mặc định áp dụng tự động trên toàn bộ tác vụ, tuyệt đối không tạo code dự phòng tương lai.**

---

## I. Mục Tiêu Cốt Lõi (Core Objective)

> **"Chỉ triển khai những gì thực sự cần cho yêu cầu hiện tại. Tuyệt đối không lập trình dựa trên giả định tương lai."**

1. **Chống Speculative Coding (Lập trình phỏng đoán):** Không bao giờ viết thêm code, thêm trường dữ liệu, hoặc cấu trúc hệ thống cho những tính năng mà "trong tương lai có thể sẽ cần tới".
2. **Bảo tồn tài nguyên và giảm diện tích bề mặt lỗi (Attack / Bug Surface):** Mọi dòng code tồn tại trong repo đều phải chịu chi phí bảo trì, đọc hiểu, viết test và nguy cơ sinh lỗi. Code ít đi nghĩa là ít bug hơn.
3. **Mỗi dòng code phải có nguồn gốc (Traceability):** Mọi class, method, column trong Database, hoặc config key đều phải ánh xạ trực tiếp tới ít nhất một **Acceptance Criterion (AC)** của bài toán hiện tại.

---

## II. Bộ Quy Tắc Thực Thi Mặc Định (Default Enforcement Rules)

### 1. No Speculative Interfaces & Extension Points (Cấm Interface dự phòng)
- **Quy tắc:** Chỉ tạo Interface khi:
  - Bắt buộc theo ranh giới kiến trúc Hexagonal (Inbound Port / Outbound Port để đảo ngược phụ thuộc).
  - Hoặc hiện tại **đã có ít nhất 2 implementation thực tế** đang hoạt động đồng thời.
- **Cấm:** Không tạo các pattern mở rộng như Plugin SPI, Dynamic Hook, Strategy Pattern, Event Bus phức tạp nếu hệ thống chỉ có một cách xử lý duy nhất.

### 2. No Phantom Fields in DB & DTOs (Cấm trường dữ liệu "bóng ma")
- **Quy tắc:** Schema Database (Flyway/Liquibase/DDL), JPA Entity và Request/Response DTOs chỉ chứa các trường đang được đọc/ghi thực tế trong luồng nghiệp vụ hiện tại.
- **Cấm:** Không thêm các cột như `fax_number`, `secondary_backup_phone`, `metadata_json`, `custom_attributes`... nếu không có user story hay tính năng cụ thể nào yêu cầu.

### 3. No Premature Optimization & Infrastructure Bloat (Cấm tối ưu hóa sớm & phình to hạ tầng)
- **Quy tắc:** Sử dụng giải pháp đơn giản nhất của stack hiện tại trước khi kéo thêm công nghệ mới.
  - Sử dụng quan hệ Database chuẩn (PostgreSQL) và đánh Index chính xác trước khi nghĩ đến việc tích hợp Redis Cache.
  - Sử dụng Spring `@Async` hoặc transaction boundary chuẩn trước khi dựng cụm Kafka / RabbitMQ.
- **Cấm:** Không cài đặt hệ thống caching phân tán, sharding, distributed lock khi volume dữ liệu và traffic của dự án quản lý phòng trọ chưa đạt ngưỡng nghẽn thực tế.

### 4. No Unused Config Properties & Feature Flags (Không tạo biến config dư thừa)
- **Quy tắc:** Không đưa vào `application.yml` các biến cấu hình mà thực tế không bao giờ thay đổi runtime giữa các môi trường.
- **Quy tắc:** Nếu một giá trị là quy ước bất biến của nghiệp vụ (ví dụ: tiền cọc tối thiểu là 1 tháng, thời hạn thuê tối thiểu 1 tháng), hãy giữ nó trong Domain Invariant hoặc hằng số rõ nghĩa, không biến nó thành dynamic config khi chưa có yêu cầu.

### 5. Zero Dead Code & Zero Commented Code (Xóa sạch code chết)
- **Quy tắc:** Xóa bỏ triệt để mọi method, class, biến không còn sử dụng.
- **Cấm:** Tuyệt đối không comment lại các đoạn code cũ với lý do "để lại sau này có khi cần xem lại". Lịch sử Git (`git log`, `git blame`) đã lưu trữ toàn bộ thay đổi; repo chỉ được chứa mã nguồn có hiệu lực.

---

## III. Minh Họa Đối Chiếu (Before vs After)

### Tình huống: Thiết kế Entity và Use Case quản lý phòng trọ

#### ❌ Before (Vi phạm YAGNI - Dự đoán tính năng IoT, Dynamic Pricing, Đa tiền tệ...)
```java
// Thực tế bài toán hiện tại chỉ cần quản lý phòng trọ cơ bản!
public class Room {
    private RoomId id;
    private String roomNumber;
    private BigDecimal basePrice;
    
    // CÁC TRƯỜNG DỰ PHÒNG TƯƠNG LAI (VI PHẠM YAGNI)
    private String iotSmartLockDeviceId;      // "Sau này có thể lắp khóa vân tay"
    private String blockchainContractAddress; // "Sau này có thể tích hợp hợp đồng thông minh"
    private String currencyCode;              // "Sau này có thể cho thuê khách Tây"
    private Map<String, Object> dynamicMeta;  // "Thêm cột JSON cho linh hoạt"
    private DynamicPricingStrategy strategy;  // "Sau này có thể đổi giá theo mùa"
    
    // CÁC METHOD CHƯA AI GỌI
    public void syncWithIotGateway() { ... }
    public void calculateSurgePricing() { ... }
}
```

#### ✅ After (Tuân thủ YAGNI - Tinh gọn, đúng bài toán thực tế)
```java
// Đúng và đủ cho nhu cầu quản lý phòng trọ thực tế hiện tại
public class Room {
    private final RoomId id;
    private String roomNumber;
    private BigDecimal monthlyRent;
    private Integer maxOccupants;
    private RoomStatus status;

    public Room(RoomId id, String roomNumber, BigDecimal monthlyRent, Integer maxOccupants) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.monthlyRent = monthlyRent;
        this.maxOccupants = maxOccupants;
        this.status = RoomStatus.AVAILABLE;
    }

    public void rentOut() {
        if (this.status != RoomStatus.AVAILABLE) {
            throw new IllegalStateException("Room is not available for rent");
        }
        this.status = RoomStatus.RENTED;
    }

    public RoomId getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public BigDecimal getMonthlyRent() { return monthlyRent; }
    public RoomStatus getStatus() { return status; }
}
```

---

## IV. Nhận Diện Các "Red Flags" (Dấu hiệu vi phạm YAGNI khi review)

Khi đọc PR hoặc thảo luận thiết kế, nếu xuất hiện các lập luận sau thì **99% đang vi phạm YAGNI**:
1. *"Cứ để sẵn interface/method này ở đây, sau này thêm tính năng đỡ phải tạo lại."*
2. *"Thêm cột này vào bảng DB trước, mai mốt cần đỡ phải chạy migration."*
3. *"Viết class này thật generic để sau này entity khác cũng dùng được."*
4. *"Cài thêm Redis/Kafka vào đây cho chuyên nghiệp, sau này scale dễ hơn."*
