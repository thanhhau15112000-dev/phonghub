---
name: spring-boot-hexagonal
description: Hexagonal Architecture (Ports and Adapters) specialized for Java Spring Boot 3 applications. Covers package layout (BuckPal pattern), pure domain modeling, application use cases, inbound/outbound ports, adapters (Web, JPA persistence), DTO mappers, ArchUnit rules, and curated reference repositories. Use when designing, refactoring, or reviewing Spring Boot services with Hexagonal Architecture.
metadata:
  origin: phonghub
---

# Spring Boot Hexagonal Architecture (Ports & Adapters)

Hướng dẫn chuẩn hóa thiết kế và triển khai kiến trúc Lục giác (Hexagonal Architecture / Ports & Adapters) trên nền tảng **Java 21** và **Spring Boot 3**.

---

## I. Curated Reference Repositories (Repo Tham Khảo Chuẩn)

1. **thombergs/buckpal**
   - URL: `https://github.com/thombergs/buckpal`
   - Đặc điểm: Mã nguồn đồng hành của cuốn sách kinh điển *"Get Your Hands Dirty on Clean Architecture"* (Tom Hombergs). Là chuẩn mực triển khai Ports & Adapters trên Spring Boot với cấu trúc package inbound/outbound rõ ràng, kiểm tra kiến trúc bằng ArchUnit, tách biệt hoàn toàn JPA Entity và Domain Model.
2. **kamilmazurek/hexagonal-architecture-template**
   - URL: `https://github.com/kamilmazurek/hexagonal-architecture-template`
   - Đặc điểm: Template hiện đại cho Spring Boot 3, hỗ trợ đầy đủ Testcontainers, ArchUnit, Docker, REST API chuẩn enterprise và chia module rõ ràng giữa Domain, Application, Infrastructure.
3. **jaguililla/hexagonal_spring**
   - URL: `https://github.com/jaguililla/hexagonal_spring`
   - Đặc điểm: Tiếp cận thực dụng và tối giản (pragmatic production-ready), sử dụng Java hiện đại, Postgres, Kafka và Testcontainers. Phù hợp cho kiến trúc microservices.
4. **SvenWoltmann/hexagonal-architecture-java**
   - URL: `https://github.com/SvenWoltmann/hexagonal-architecture-java`
   - Đặc điểm: Dự án mẫu có tính giáo khoa cao, giải thích chi tiết ranh giới giữa core domain logic và adapters bên ngoài.
5. **MartinCastroAlvarez/hexagonal-spring-boot**
   - URL: `https://github.com/MartinCastroAlvarez/hexagonal-spring-boot`
   - Đặc điểm: Repo mẫu gọn nhẹ, minh họa trực quan việc sử dụng interface ports cho repositories và use cases.

---

## II. Nguyên Tắc Cốt Lõi (Core Principles & Invariants)

1. **Quy tắc phụ thuộc một chiều (Inward Dependency Rule):**
   - Mọi phụ thuộc mã nguồn chỉ được hướng từ ngoài vào trong:
     $$\text{Adapters (Web / DB)} \longrightarrow \text{Application (Use Cases / Ports)} \longrightarrow \text{Domain (Core Models)}$$
2. **Domain tinh khiết (Pure Domain):**
   - Package `domain` tuyệt đối **KHÔNG import** `org.springframework.*`, `jakarta.persistence.*`, hay bất kỳ thư viện framework nào.
   - Domain chứa: Entities, Value Objects, Domain Services, Invariants và Business Exceptions.
3. **Phân biệt Ports:**
   - **Inbound Port (Driving / Primary):** Interface định nghĩa use case mà bên ngoài gọi vào (ví dụ: `RegisterRoomUseCase`, `GetRoomDetailQuery`).
   - **Outbound Port (Driven / Secondary):** Interface định nghĩa khả năng ứng dụng cần từ bên ngoài (ví dụ: `RoomRepositoryPort`, `PaymentGatewayPort`, `NotificationPort`).
4. **Adapters:**
   - **Inbound Adapter:** REST Controller, CLI, Message Consumer, Cron Job. Nhận request, validate định dạng transport, chuyển đổi sang Command/Query object và gọi Inbound Port.
   - **Outbound Adapter:** Triển khai Outbound Port (Spring Data JPA, Redis Cache, HTTP Client, Email Sender). Chuyển đổi Domain Model sang Entity/DTO của bên thứ ba và ngược lại.
5. **Tách biệt Data Model và Domain Model:**
   - Database Entity (`@Entity`, `@Table`) thuộc adapter persistence.
   - Domain Model (`Room`, `Tenant`) thuộc domain core.
   - Luôn sử dụng Mapper tĩnh hoặc mapper chuyên biệt để ánh xạ giữa hai mô hình, ngăn chặn việc rò rỉ cơ sở dữ liệu vào nghiệp vụ.

---

## III. Cấu Trúc Package Khuyến Nghị (BuckPal Pattern)

Áp dụng tổ chức theo Feature (Package by Feature) hoặc Component:

```text
com.phonghub.core.<feature>/
├── domain/                                  <-- 100% Pure Java (No Spring/JPA)
│   ├── model/
│   │   ├── Room.java                        (Entity nghiệp vụ)
│   │   ├── RoomId.java                      (Value Object / Record)
│   │   └── RoomStatus.java                  (Enum trạng thái)
│   └── exception/
│       └── RoomAlreadyRentedException.java
│
├── application/                             <-- Nghiệp vụ ứng dụng & Điều phối
│   ├── port/
│   │   ├── in/                              (Inbound / Driving Ports)
│   │   │   ├── CreateRoomUseCase.java       (Interface)
│   │   │   ├── CreateRoomCommand.java       (Record - Input validation)
│   │   │   └── GetRoomQuery.java
│   │   └── out/                             (Outbound / Driven Ports)
│   │       ├── LoadRoomPort.java            (Interface)
│   │       └── SaveRoomPort.java            (Interface)
│   └── service/
│       └── CreateRoomService.java           (@Service implements CreateRoomUseCase)
│
└── adapter/                                 <-- Tầng hạ tầng & giao tiếp bên ngoài
    ├── in/
    │   └── web/
    │       ├── RoomController.java          (@RestController)
    │       ├── request/CreateRoomRequest.java
    │       ├── response/RoomResponse.java
    │       └── RoomWebMapper.java
    └── out/
        └── persistence/
            ├── RoomJpaEntity.java           (@Entity, @Table)
            ├── SpringDataRoomRepository.java (JpaRepository)
            ├── RoomPersistenceAdapter.java  (@Component implements LoadRoomPort, SaveRoomPort)
            └── RoomPersistenceMapper.java
```

---

## IV. Minh Họa Code Mẫu

### 1. Inbound Port & Command (Application Layer)

```java
package com.phonghub.core.room.application.port.in;

import java.math.BigDecimal;

public interface CreateRoomUseCase {
    RoomId createRoom(CreateRoomCommand command);

    record CreateRoomCommand(
        String roomNumber,
        BigDecimal basePrice,
        Integer maxOccupants
    ) {
        public CreateRoomCommand {
            if (roomNumber == null || roomNumber.isBlank()) {
                throw new IllegalArgumentException("Room number cannot be empty");
            }
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Price must be greater than zero");
            }
        }
    }
}
```

### 2. Outbound Port (Application Layer)

```java
package com.phonghub.core.room.application.port.out;

import com.phonghub.core.room.domain.model.Room;
import com.phonghub.core.room.domain.model.RoomId;
import java.util.Optional;

public interface SaveRoomPort {
    void saveRoom(Room room);
}

public interface LoadRoomPort {
    Optional<Room> loadRoom(RoomId roomId);
    boolean existsByRoomNumber(String roomNumber);
}
```

### 3. Application Service (Use Case Implementation)

```java
package com.phonghub.core.room.application.service;

import com.phonghub.core.room.application.port.in.CreateRoomUseCase;
import com.phonghub.core.room.application.port.out.LoadRoomPort;
import com.phonghub.core.room.application.port.out.SaveRoomPort;
import com.phonghub.core.room.domain.model.Room;
import com.phonghub.core.room.domain.model.RoomId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateRoomService implements CreateRoomUseCase {

    private final LoadRoomPort loadRoomPort;
    private final SaveRoomPort saveRoomPort;

    public CreateRoomService(LoadRoomPort loadRoomPort, SaveRoomPort saveRoomPort) {
        this.loadRoomPort = loadRoomPort;
        this.saveRoomPort = saveRoomPort;
    }

    @Override
    public RoomId createRoom(CreateRoomCommand command) {
        if (loadRoomPort.existsByRoomNumber(command.roomNumber())) {
            throw new IllegalStateException("Room number already exists: " + command.roomNumber());
        }

        Room newRoom = Room.createNew(
            command.roomNumber(),
            command.basePrice(),
            command.maxOccupants()
        );

        saveRoomPort.saveRoom(newRoom);
        return newRoom.getId();
    }
}
```

### 4. Outbound Persistence Adapter (Adapter Layer)

```java
package com.phonghub.core.room.adapter.out.persistence;

import com.phonghub.core.room.application.port.out.LoadRoomPort;
import com.phonghub.core.room.application.port.out.SaveRoomPort;
import com.phonghub.core.room.domain.model.Room;
import com.phonghub.core.room.domain.model.RoomId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RoomPersistenceAdapter implements LoadRoomPort, SaveRoomPort {

    private final SpringDataRoomRepository repository;
    private final RoomPersistenceMapper mapper;

    public RoomPersistenceAdapter(SpringDataRoomRepository repository, RoomPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void saveRoom(Room room) {
        RoomJpaEntity entity = mapper.toEntity(room);
        repository.save(entity);
    }

    @Override
    public Optional<Room> loadRoom(RoomId roomId) {
        return repository.findById(roomId.value())
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByRoomNumber(String roomNumber) {
        return repository.existsByRoomNumber(roomNumber);
    }
}
```

---

## V. Kiến Trúc Kiểm Định Bằng ArchUnit

Để đảm bảo quy tắc phân tầng không bị vi phạm trong quá trình phát triển, bổ sung kiểm thử ArchUnit:

```java
@AnalyzeClasses(packages = "com.phonghub.core", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..", "org.springframework..");

    @ArchTest
    static final ArchRule application_must_not_depend_on_adapters =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");
}
```

---

## VI. Tiêu Chí KISS & YAGNI trong Hexagonal Architecture

- **Không tạo port vô nghĩa:** Nếu một hàm tra cứu đơn giản phục vụ riêng cho UI hiển thị danh sách (read-only query không có nghiệp vụ phức tạp), cân nhắc tách riêng luồng Query đơn giản (CQRS light), không nhất thiết phải bọc qua 5 lớp mapper nếu không thay đổi logic.
- **Tránh Over-mapping khi chưa cần thiết:** Chỉ tạo model trung gian khi dữ liệu thực sự cần biến đổi hoặc kiểm soát invariant.
- **Ưu tiên Record:** Dùng Java `record` cho Commands, Queries, DTOs và Value Objects để code ngắn gọn, bất biến và tự sinh `equals/hashCode`.
