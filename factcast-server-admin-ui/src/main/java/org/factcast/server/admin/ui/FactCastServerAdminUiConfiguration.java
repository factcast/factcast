package org.factcast.server.admin.ui;

import org.factcast.server.admin.ui.controller.CountingController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FactCastServerAdminUiConfiguration {

    @Bean
    public CountingController countingController(){
        return new CountingController();
    }
}
