package org.factcast.core.subscription;

import org.factcast.core.subscription.observer.GenericObserver;

import lombok.NonNull;

public class Subscriptions {

    public static <T> SubscriptionImpl<T> on(@NonNull GenericObserver<T> o) {
        return new SubscriptionImpl<>(o);
    }

}
