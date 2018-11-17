package org.factcast.core;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.Callable;

public class TestHelper {

    public static void expectNPE(Callable<?> e) {
        expect(NullPointerException.class, e);
    }

    public static void expect(Class<? extends Throwable> ex, Callable<?> e) {
        try {
            e.call();
            fail("expected " + ex);
        } catch (Throwable actual) {
            if (!ex.isInstance(actual)) {
                fail("Wrong exception, expected " + ex + " but got " + actual);
            }
        }
    }
}
