package org.factcast.factus.serializer.binary;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class TestData {
    public static final byte[] HUGE_JSON;
    static{

        try {
            HUGE_JSON=
                    new ClassPathResource("/huge.json").getContentAsString(StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8
                    );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
