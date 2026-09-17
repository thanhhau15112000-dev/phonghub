package com.phonghub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class PhongHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhongHubApplication.class, args);
    }
}
