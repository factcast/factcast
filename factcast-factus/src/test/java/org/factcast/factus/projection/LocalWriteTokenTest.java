package org.factcast.factus.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class LocalWriteTokenTest {

    private LocalWriteToken underTest = new LocalWriteToken();

    @Test
    void acquireWriteToken() throws Exception {
        // acquire lock
        AutoCloseable lock = underTest.acquireWriteToken(Duration.ofSeconds(1));

        // we have a lock when this is not null
        assertThat(lock)
                .isNotNull();

        // same thread should be able to get the lock again
        AutoCloseable otherLock = underTest.acquireWriteToken(Duration.ofSeconds(1));

        assertThat(otherLock)
                .isNotNull();

        // test that we can unlock without exception
        lock.close();
        otherLock.close();
    }

    @Test
    void cannotLock() throws Exception {
        AtomicReference<AutoCloseable> lock = new AtomicReference<>();

        // signal for other thread to unlock
        AtomicBoolean unlock = new AtomicBoolean(false);

        // another thread that acquires (and keeps) a lock
        // we test that we cannot acquire one while this thread keeps the lock,
        // but that we can get one after the thread has released its lock
        Thread lockingThread = new Thread(() -> {
            lock.set(underTest.acquireWriteToken(Duration.ofSeconds(1)));
            while (!unlock.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
            // and unlock
            try {
                lock.get().close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            lockingThread.start();

            // wait until locked
            while (lock.get() == null && lockingThread.isAlive()) {
                Thread.sleep(10);
            }

            // make sure the thread really got a lock
            assertThat(lock.get())
                    .isNotNull();

            // now make sure our thread cannot acquire a lock while the other
            // thread holds a lock
            assertThat(underTest.acquireWriteToken(Duration.ofMillis(1)))
                    .isNull();

        } finally {
            // make sure other thread does not run forever in case of exception
            unlock.set(true);
        }

        // make sure other thread has finished (and hence released the lock)
        lockingThread.join();

        // now we should be able to acquire the lock as well
        AutoCloseable newLock = underTest.acquireWriteToken(Duration.ofMillis(1));
        assertThat(newLock)
                .isNotNull();

        newLock.close();
    }
}