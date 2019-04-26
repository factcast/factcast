package org.factcast.core.lock;

import java.util.*;

import org.factcast.core.store.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

class WithOptimisticLockTest {


    @Test
    void attemptReturnsNullShouldBeAnAbort() {

        WithOptimisticLock uut
                = new WithOptimisticLock(Mockito.mock(FactStore.class), null, Collections.singletonList(UUID.randomUUID()));


        Assertions.assertThrows(AttemptAbortedException.class,
                () -> {
                    uut.attempt(() -> null);
                }
        );

    }
}