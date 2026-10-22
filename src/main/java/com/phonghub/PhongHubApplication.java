package com.phonghub;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class PhongHubApplication {

    public static void main(String[] args) {
        // Ngày/giờ nghiệp vụ theo giờ Việt Nam; máy chủ deploy (Render) chạy UTC nên đặt múi giờ mặc định của JVM.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(PhongHubApplication.class, args);
    }
}
