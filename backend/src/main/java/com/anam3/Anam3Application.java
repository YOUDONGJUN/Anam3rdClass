package com.anam3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Anam3Application {

    public static void main(String[] args) {
        SpringApplication.run(Anam3Application.class, args);
    }
}
