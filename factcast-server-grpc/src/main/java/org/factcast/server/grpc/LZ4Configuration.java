package org.factcast.server.grpc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Configuration
@ConditionalOnClass(name = "net.jpountz.lz4.LZ4Constants")
@Slf4j
public class LZ4Configuration {

    {
        log.info("Initializing Server-side LZ4 Compression");
        // TODO Registration is not complete without
        // https://github.com/yidongnan/grpc-spring-boot-starter/issues/96
    }

}
