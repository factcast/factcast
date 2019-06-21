package org.factcast.core.lock;

import java.util.UUID;

import lombok.Getter;

public final class ExceptionAfterPublish extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    @Getter
    private UUID lastFactId;

    public ExceptionAfterPublish(UUID lastFactId, Throwable e) {
        super("An exception has happened in the 'andThen' part of your publishing attempt. This is a programming error, as the runnable in andThen is not supposed to throw an Exception. Note that publish actually worked, and the id of your last published fact is "
                + lastFactId,
                e);
        this.lastFactId = lastFactId;
    }

}
