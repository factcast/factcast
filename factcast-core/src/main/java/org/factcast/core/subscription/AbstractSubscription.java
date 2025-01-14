package org.factcast.core.subscription;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeoutException;

@Slf4j
public abstract class AbstractSubscription implements Subscription {
    @NonNull private Runnable onClose = () -> {};

    @Override
    public Subscription onClose(@NonNull Runnable e) {
        Runnable formerOnClose = onClose;
        onClose =
                () -> {
                    tryRun(formerOnClose);
                    tryRun(e);
                };
        return this;
    }

    private void tryRun(Runnable e) {
        try {
            e.run();
        } catch (Exception ex) {
            log.error("While executing onClose:", ex);
        }
    }

    public abstract void internalClose();

    @Override
    public void close() {
        try {
            internalClose();
        }finally {
            onClose.run();
        }
    }
}
