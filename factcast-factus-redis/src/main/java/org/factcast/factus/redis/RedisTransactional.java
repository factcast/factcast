package org.factcast.factus.redis;

public @interface RedisTransactional {

  /**
   * try to apply up to batchSize number of events before committing (only) during catchup phase.
   */
  boolean batch() default false;

  /**
   * try to apply up to batchSize number of events before committing during catchup phase. if set to
   * 0 defaults to factcast.grpc.client.catchupBatchsize (which in turn defaults to 50)
   */
  int size() default 0;

  /** commit prematurely if timeoutInMs is exceeded (reduces batch size), defaults to 5 seconds */
  long timeoutInMs() default 5000;

  /**
   * if failure is detected, factus will restart processing from the beginning of the batch an
   * commit right before the failing handler call.
   */
  boolean approachFailure() default false;
}
