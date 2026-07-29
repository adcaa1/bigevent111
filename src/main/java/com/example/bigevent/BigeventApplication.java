package com.example.bigevent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BigeventApplication {

    public static void main(String[] args) {
        SpringApplication.run(BigeventApplication.class, args);
    }

}
