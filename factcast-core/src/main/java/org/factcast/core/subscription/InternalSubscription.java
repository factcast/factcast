/*
 * Copyright © 2017-2022 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.core.subscription;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;

@SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
public interface InternalSubscription extends Subscription {
  void close();

  void notifyCatchup();

  void notifyFastForward(@NonNull FactStreamPosition pos);

  void notifyFactStreamInfo(@NonNull FactStreamInfo info);

  void notifyComplete();

  void notifyError(Throwable e);

  SubscriptionImpl onClose(Runnable e);

  /** supposed to be used on server side only */
  void notifyElement(@Nullable Fact f) throws TransformationException;

  // since 0.7.5
  void notifyElements(@NonNull List<Fact> e) throws TransformationException;
}
