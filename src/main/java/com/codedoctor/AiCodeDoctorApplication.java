package com.codedoctor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiCodeDoctorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiCodeDoctorApplication.class, args);
    }
}
