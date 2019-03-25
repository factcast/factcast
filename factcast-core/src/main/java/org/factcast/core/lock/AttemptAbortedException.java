package org.factcast.core.lock;

/**
 * Extend this class if you want to pass extra info out of the Attempt lambda.
 * This is necessary as throwables cannot be generic.
 *
 */
public class AttemptAbortedException extends Exception {
    private static final long serialVersionUID = 1L;

    public AttemptAbortedException(String msg) {
        super(msg);
    }

    public AttemptAbortedException(Exception e) {
        super(e);
    }

}
