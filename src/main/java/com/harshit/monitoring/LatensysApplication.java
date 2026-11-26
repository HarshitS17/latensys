package com.harshit.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LatensysApplication {

    public static void main(String[] args) {
        SpringApplication.run(LatensysApplication.class, args);
    }

}
