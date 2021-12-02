package org.factcast.server.grpc.metrics;

import io.micrometer.core.instrument.Tags;
import java.util.function.Supplier;
import org.factcast.factus.metrics.RunnableWithException;
import org.factcast.factus.metrics.SupplierWithException;
import org.factcast.server.grpc.metrics.ServerMetrics;

public class NOPServerMetrics implements ServerMetrics {
  @Override
  public void timed(OP operation, Runnable fn) {
    fn.run();
  }

  @Override
  public void timed(OP operation, Tags tags, Runnable fn) {
    fn.run();
  }

  @Override
  public <E extends Exception> void timed(
      OP operation, Class<E> exceptionClass, RunnableWithException<E> fn) throws E {
    fn.run();
  }

  @Override
  public <E extends Exception> void timed(
      OP operation, Class<E> exceptionClass, Tags tags, RunnableWithException<E> fn) throws E {
    fn.run();
  }

  @Override
  public <T> T timed(OP operation, Supplier<T> fn) {
    return fn.get();
  }

  @Override
  public <T> T timed(OP operation, Tags tags, Supplier<T> fn) {
    return fn.get();
  }

  @Override
  public <R, E extends Exception> R timed(
      OP operation, Class<E> exceptionClass, SupplierWithException<R, E> fn) throws E {
    return fn.get();
  }

  @Override
  public <R, E extends Exception> R timed(
      OP operation, Class<E> exceptionClass, Tags tags, SupplierWithException<R, E> fn) throws E {
    return fn.get();
  }

  @Override
  public void count(EVENT event) {}

  @Override
  public void count(EVENT event, Tags tags) {}
}
