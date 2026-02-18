package com.motorola.scada;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MototrboScadaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MototrboScadaApplication.class, args);
    }
}
