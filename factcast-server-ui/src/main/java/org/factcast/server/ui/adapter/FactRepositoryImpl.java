/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.adapter;

import io.micrometer.core.annotation.Timed;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SpecBuilder;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.server.ui.full.FullFilterBean;
import org.factcast.server.ui.id.IdQueryBean;
import org.factcast.server.ui.metrics.UiMetrics;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.report.ReportFilterBean;
import org.factcast.server.ui.security.SecurityService;
import org.factcast.server.ui.views.filter.FilterBean;

@Timed(value = UiMetrics.TIMER_METRIC_NAME)
@RequiredArgsConstructor
public class FactRepositoryImpl implements FactRepository {

  private final FactStore fs;

  private final SecurityService securityService;

  @Override
  public Optional<Fact> findBy(@NonNull IdQueryBean bean) {
    UUID id = bean.getId();
    if (id == null) {
      return Optional.empty();
    }

    int v = Optional.ofNullable(bean.getVersion()).orElse(0);
    return fs.fetchByIdAndVersion(id, v).filter(securityService::canRead);
  }

  @Override
  public List<String> namespaces(@Nullable String optionalInput) {
    Stream<String> ns = fs.enumerateNamespaces().stream();
    if (optionalInput != null) {
      Pattern ptn = Pattern.compile(".*" + optionalInput + ".*");
      ns = ns.filter(s -> ptn.matcher(s).matches());
    }
    return ns.filter(securityService::canRead).sorted().toList();
  }

  @Override
  public List<String> types(@NonNull String namespace, @Nullable String optionalInput) {
    if (!securityService.canRead(namespace)) {
      return Collections.emptyList();
    }

    Stream<String> type = fs.enumerateTypes(namespace).stream();
    if (optionalInput != null) {
      Pattern ptn = Pattern.compile(".*" + optionalInput + ".*");
      type = type.filter(s -> ptn.matcher(s).matches());
    }

    return type.sorted().toList();
  }

  @Override
  public long latestSerial() {
    return fs.latestSerial();
  }

  @Override
  public Optional<UUID> findIdOfSerial(long longValue) {
    return fs.fetchBySerial(longValue).map(Fact::id);
  }

  @SneakyThrows
  @Override
  public List<Fact> fetchChunk(FullFilterBean bean) {
    final var observer = new ListObserver(bean.getLimitOrDefault(), bean.getOffsetOrDefault());
    return fetch(bean, observer);
  }

  @SneakyThrows
  public List<Fact> fetchAll(ReportFilterBean bean) {
    final var observer = new UnlimitedListObserver(0);
    return fetch(bean, observer);
  }

  @SneakyThrows
  private List<Fact> fetch(FilterBean bean, AbstractListObserver obs) {
    Set<FactSpec> specs = securityService.filterReadable(bean.createFactSpecs());

    SpecBuilder sr = SubscriptionRequest.catchup(specs);
    long ser = Optional.ofNullable(bean.getFrom()).orElse(BigDecimal.ZERO).longValue();
    SubscriptionRequest request;

    if (ser > 0) {
      request = sr.fromNullable(findIdOfSerial(ser).orElse(null));
    } else {
      request = sr.fromScratch();
    }

    final SubscriptionRequestTO requestTO = SubscriptionRequestTO.forFacts(request);
    setDebugInfo(requestTO);

    try (Subscription subscription = fs.subscribe(requestTO, obs)) {
      subscription.awaitCatchup();
    } catch (Exception e) {
      // in case the limit is reached, it makes no sense to stream the rest of the
      // factstream into the ListObserver. Leaving the try-with-resources, the
      // subscription will be closed

      if (!LimitReachedException.matches(e)) {
        // something else happened, we probably need to escalate and notify
        throw ExceptionHelper.toRuntime(e);
      }
    }
    return obs.list();
  }

  private void setDebugInfo(SubscriptionRequestTO req) {
    req.debugInfo(securityService.getAuthenticatedUser().getUsername());
  }

  @Override
  public OptionalLong lastSerialBefore(@NonNull LocalDate date) {
    return OptionalLong.of(fs.lastSerialBefore(date));
  }
}
