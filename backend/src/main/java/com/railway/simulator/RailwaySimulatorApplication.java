package com.railway.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RailwaySimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(RailwaySimulatorApplication.class, args);
    }
}
