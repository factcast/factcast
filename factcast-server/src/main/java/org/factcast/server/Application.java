package org.factcast.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Spring boot starter for running a production server.
 * 
 * This should contain a pgsql backend and grpc and rest frontends.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@SpringBootApplication
@EnableAutoConfiguration(exclude = ErrorMvcAutoConfiguration.class)
@Configuration
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }
}
