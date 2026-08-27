package com.mediparse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class MediParseApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediParseApplication.class, args);
    }
}
