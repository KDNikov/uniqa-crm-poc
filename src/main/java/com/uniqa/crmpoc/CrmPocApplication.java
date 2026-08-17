package com.uniqa.crmpoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CrmPocApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrmPocApplication.class, args);
    }
}
