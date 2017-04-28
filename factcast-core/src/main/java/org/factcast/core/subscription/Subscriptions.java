package org.factcast.core.subscription;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Subscriptions {

    public static <T> SubscriptionImpl<T> on(@NonNull GenericObserver<T> o) {
        return new SubscriptionImpl<>(o);
    }

}
