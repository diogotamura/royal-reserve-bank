package com.royal.reserve.bank.risk.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Main class for the Risk Score Api.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class RiskScoreApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskScoreApiApplication.class, args);
    }
}
