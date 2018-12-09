package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Callable;

import org.junit.jupiter.api.function.Executable;

public class TestHelper {

    public static void expectNPE(Executable e) {
        expect(NullPointerException.class, e);
    }

    public static void expect(Class<? extends Throwable> ex, Executable e) {
        assertThrows(ex, e);
    }
}
