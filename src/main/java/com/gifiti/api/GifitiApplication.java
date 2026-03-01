package com.gifiti.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class GifitiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GifitiApplication.class, args);
    }

}
