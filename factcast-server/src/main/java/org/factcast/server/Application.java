package org.factcast.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

/**
 * Spring boot starter for running a production server.
 * 
 * This should contain a pgsql backend and grpc and rest frontends.
 * 
 * @author usr
 *
 */
@SpringBootApplication
@EnableAutoConfiguration
@Configuration
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }
}
