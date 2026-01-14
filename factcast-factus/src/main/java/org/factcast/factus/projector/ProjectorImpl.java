/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.factus.projector;

import static java.util.Collections.*;

import com.google.common.annotations.VisibleForTesting;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.*;
import org.factcast.core.spec.*;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.factus.*;
import org.factcast.factus.event.*;
import org.factcast.factus.projection.*;
import org.factcast.factus.projection.parameter.*;
import org.factcast.factus.projection.tx.*;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

@Slf4j
public class ProjectorImpl<A extends Projection> implements Projector<A> {

  private final Projection projection;

  @Getter(AccessLevel.PROTECTED)
  private final Map<FactSpecCoordinates, Dispatcher> dispatchInfo;

  private final EventSerializer serializer;
  private final HandlerParameterContributors generalContributors;

  interface TargetObjectResolver extends Function<Projection, Object> {}

  public ProjectorImpl(
      @NonNull Projection p,
      @NonNull EventSerializer serializer,
      @NonNull HandlerParameterContributors parameterContributors) {
    this.serializer = serializer;
    generalContributors = parameterContributors;
    projection = p;

    dispatchInfo = ReflectionUtils.getDispatcherInfo(p, generalContributors);
  }

  /**
   * for compatibility
   *
   * @param p
   * @param es
   * @deprecated
   */
  @Deprecated
  public ProjectorImpl(@NonNull Projection p, @NonNull EventSerializer es) {
    this(p, es, new HandlerParameterContributors());
  }

  @Override
  public void apply(@NonNull List<Fact> facts) {
    doApply(facts);
  }

  public void doApply(@NonNull List<Fact> facts) {

    beginIfTransactional();

    // remember that IF this fails, we throw an exception anyway, so that we won't reuse this info
    FactStreamPosition latestSuccessful = null;

    for (Fact f : facts) {

      try {
        callHandlerFor(f);
        latestSuccessful = FactStreamPosition.from(f);
        setFactStreamPositionIfAwareButNotTransactional(latestSuccessful);
      } catch (Exception e) {
        log.trace(
            "returned with Exception {}:",
            latestSuccessful == null ? null : latestSuccessful.factId(),
            e);
        rollbackIfTransactional();
        retryApplicableIfTransactional(facts, f);

        // pass along and potentially rethrow
        projection.onError(e);
        throw ExceptionHelper.toRuntime(e);
      }
    } // end loop

    try {
      // this is something we only do, if the whole batch was successfully applied
      if (projection instanceof TransactionAware && latestSuccessful != null) {
        setFactStreamPositionIfAware(latestSuccessful);
      }
    } catch (Exception e) {

      rollbackIfTransactional();

      // pass along and potentially rethrow
      projection.onError(e);
      throw e;
    }

    try {
      commitIfTransactional();
    } catch (TransactionException e) {
      // pass along and potentially rethrow
      projection.onError(e);
      throw e;
    }
  }

  private void setFactStreamPositionIfAwareButNotTransactional(
      @NonNull FactStreamPosition latestSuccessful) {
    if (!(projection instanceof TransactionAware)) {
      setFactStreamPositionIfAware(latestSuccessful);
    }
  }

  @VisibleForTesting
  void retryApplicableIfTransactional(List<Fact> facts, Fact f) {
    if (projection instanceof TransactionAware) {
      // retry [0,n-1]
      List<Fact> applicableFacts = facts.subList(0, facts.indexOf(f));
      int applicableSize = applicableFacts.size();
      if (applicableSize > 0) {
        log.warn("Exception during batch application, reapplying {} facts.", applicableSize);
        apply(applicableFacts);
      }
    }
  }

  @VisibleForTesting
  void beginIfTransactional() {
    if (projection instanceof TransactionAware aware) {
      aware.begin();
    }
  }

  @VisibleForTesting
  void rollbackIfTransactional() {
    if (projection instanceof TransactionAware aware) {
      aware.rollback();
    }
  }

  @VisibleForTesting
  void commitIfTransactional() {
    if (projection instanceof TransactionAware aware) {
      aware.commit();
    }
  }

  private void setFactStreamPositionIfAware(@NonNull FactStreamPosition latestAttempted) {
    if (projection instanceof TransactionAware aware1) {
      aware1.transactionalFactStreamPosition(latestAttempted);
    } else if (projection instanceof FactStreamPositionAware aware) {
      aware.factStreamPosition(latestAttempted);
    }
  }

  private UUID callHandlerFor(@NonNull Fact f)
      throws InvocationTargetException, IllegalAccessException {
    UUID factId = f.id();
    log.trace("Dispatching fact {}", factId);
    FactSpecCoordinates coords = FactSpecCoordinates.from(f);
    Dispatcher dispatch = dispatchInfo.get(coords);
    if (dispatch == null) {
      // try to find one with no version as a fallback
      dispatch = dispatchInfo.get(coords.withVersion(0));
    }

    if (dispatch == null) {
      // fallback for wildcard usage

      List<Map.Entry<FactSpecCoordinates, Dispatcher>> found =
          dispatchInfo.entrySet().stream()
              .filter(e -> coords.matches(e.getKey()))
              .collect(Collectors.toList());

      if (found.size() > 1) {
        InvalidHandlerDefinition ihd =
            new InvalidHandlerDefinition(
                "Ambiguous handler definition for coordinates: '" + coords + "'");
        projection.onError(ihd);
        throw ihd;
      }

      if (found.isEmpty()) {
        InvalidHandlerDefinition ihd =
            new InvalidHandlerDefinition("Unexpected Fact coordinates: '" + coords + "'");
        projection.onError(ihd);
        throw ihd;
      }

      dispatch = found.iterator().next().getValue();

      dispatchInfo.put(coords, dispatch);
    }
    dispatch.invoke(serializer, projection, f);
    return factId;
  }

  @Override
  @SuppressWarnings("java:S2589")
  public List<FactSpec> createFactSpecs() {
    List<FactSpec> discovered =
        dispatchInfo.values().stream().map(d -> d.spec().copy()).collect(Collectors.toList());

    if (projection instanceof Aggregate aggregate) {
      UUID aggId = AggregateUtil.aggregateId(aggregate);
      if (aggId != null) {
        for (FactSpec factSpec : discovered) {
          factSpec.aggId(aggId);
        }
      }
    }

    List<FactSpec> ret = projection.postprocess(discovered);
    //noinspection ConstantConditions
    if (ret == null || ret.isEmpty()) {
      throw new InvalidHandlerDefinition(
          "No FactSpecs discovered from "
              + projection.getClass()
              + ". Either add handler methods or implement postprocess(List<FactSpec)");
    }
    return unmodifiableList(ret);
  }

  @Override
  public void onCatchup(@Nullable FactStreamPosition idOfLastFactApplied) {
    // no longer used, might still be interesting as a hook
  }

  @SneakyThrows
  @NonNull
  public static Object unwrapProxy(@NonNull Object bean) {
    while (AopUtils.isAopProxy(bean) && bean instanceof Advised) {
      Advised advised = (Advised) bean;
      Object targetBean = Objects.requireNonNull(advised.getTargetSource().getTarget());
      if (targetBean == bean) {
        throw new IllegalStateException(
            "AOP gone wrong? Advised.targetSource points back to advised?!?!");
      } else {
        bean = targetBean;
      }
    }
    return bean;
  }
}
