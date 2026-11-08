package com.smallbiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmallbizBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmallbizBackendApplication.class, args);
    }
}
