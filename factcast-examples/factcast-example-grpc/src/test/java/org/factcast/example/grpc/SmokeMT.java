package org.factcast.example.grpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@EnableAutoConfiguration
@Configuration
@SuppressWarnings("unused")
public class SmokeMT {

    public static void main(String[] args) {

        SpringApplication.run(SmokeMT.class);
    }

}
