package org.factcast.example.integration.inproc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import lombok.NonNull;

@SpringBootTest
@ActiveProfiles("test")
public class SomeIntegrationTest {

    @Autowired
    UnitUnderTest uut;

    @Autowired
    FactCast fc;

    class Counter implements FactObserver {

        int count = 0;

        AtomicBoolean completed = new AtomicBoolean(false);

        @Override
        public void onNext(@NonNull Fact element) {
            count++;
        }

        @Override
        public void onComplete() {
            synchronized (completed) {
                completed.set(true);
                completed.notifyAll();
            }
        }

        public Counter await() throws InterruptedException {
            synchronized (completed) {
                while (!completed.get())
                    completed.wait();
                return this;
            }
        }
    }

    @Test
    public void test() throws InterruptedException {

        Counter c = new Counter();
        fc.subscribe(SubscriptionRequest.catchup(FactSpec.ns("namespace")).fromScratch(), c);
        assertEquals(0, c.await().count);

        uut.publishAnEvent();

        c = new Counter();
        fc.subscribe(SubscriptionRequest.catchup(FactSpec.ns("namespace")).fromScratch(), c);
        assertEquals(1, c.await().count);

    }
}
