package org.factcast.server.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;

@SpringBootApplication
public class FactCastRestServer extends SpringBootServletInitializer {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(FactCastRestServer.class, args);
    }
}
