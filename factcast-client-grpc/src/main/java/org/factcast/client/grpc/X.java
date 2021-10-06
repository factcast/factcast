package org.factcast.client.grpc;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class X {
  public static void main(String[] args) throws ExecutionException, InterruptedException {
    ExecutorService es = Executors.newSingleThreadExecutor();
    es.submit(()->es.shutdown()).get();

    System.out.println(es.isShutdown());

    es.shutdown();
    System.out.println(es.isShutdown());

  }
}
