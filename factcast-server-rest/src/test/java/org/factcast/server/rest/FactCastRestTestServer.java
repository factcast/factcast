package org.factcast.server.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;

@SpringBootApplication
public class FactCastRestTestServer extends SpringBootServletInitializer {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(FactCastRestTestServer.class, args);
    }
}
