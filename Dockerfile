# ==========================================
# Stage 1: Build application with Maven
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and POM first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copy source code and build production jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# Stage 2: Minimal Production Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tesseract cho OCR chỉ số đồng hồ (#67), chỉ cần dữ liệu tiếng Anh vì chỉ đọc chữ số (~25 MB)
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng

# Run as non-root user
RUN addgroup -S phonghub && adduser -S phonghub -G phonghub
USER phonghub:phonghub

# Copy executable jar from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

# Configure JVM flags suitable for container memory constraints
# Heap 60% để metaspace, thread stack và code cache của Spring Boot vẫn nằm trong giới hạn 512MB của Render free
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0"
ENV SPRING_PROFILES_ACTIVE=prod
# Image đã có tesseract nên bật OCR; đặt METER_OCR_ENGINE=disabled để tắt mà không cần build lại
ENV METER_OCR_ENGINE=tesseract

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
