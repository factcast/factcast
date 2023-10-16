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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.*;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.LocalFactStore;
import org.factcast.core.subscription.SpecBuilder;
import org.factcast.core.subscription.SubscriptionClosedException;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.server.ui.config.SecurityService;
import org.factcast.server.ui.full.FullQueryBean;
import org.factcast.server.ui.id.IdQueryBean;
import org.factcast.server.ui.port.FactRepository;

@RequiredArgsConstructor
public class FactRepositoryImpl implements FactRepository {

  private final LocalFactStore fs;

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

  public List<String> namespaces(@Nullable String optionalInput) {
    Stream<String> ns = fs.enumerateNamespaces().stream();
    if (optionalInput != null) {
      Pattern ptn = Pattern.compile(".*" + optionalInput + ".*");
      ns = ns.filter(s -> ptn.matcher(s).matches());
    }
    return ns.filter(securityService::canRead).sorted().toList();
  }

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

  @Override
  public List<Fact> fetchChunk(FullQueryBean bean) {

    List<FactSpec> specs =
        bean.createFactSpecs().stream()
            .filter(x -> securityService.canRead(x.ns()))
            .toList(); // TODO not quite sure if this is a good idea here but for now :D

    ListObserver obs =
        new ListObserver(
            Optional.ofNullable(bean.getLimit()).orElse(50),
            Optional.ofNullable(bean.getOffset()).orElse(0));
    SpecBuilder sr = SubscriptionRequest.catchup(specs);
    BigDecimal from = bean.getFrom();
    long ser = Optional.ofNullable(from).orElse(BigDecimal.ZERO).longValue();
    SubscriptionRequest request = null;

    if (ser > 0) {
      request = sr.fromNullable(findIdOfSerial(ser).orElse(null));
    } else {
      request = sr.fromScratch();
    }

    int WAIT_TIME = 20000;
    try {
      fs.subscribe(SubscriptionRequestTO.forFacts(request), obs).awaitCatchup(WAIT_TIME);
    } catch (SubscriptionClosedException | TimeoutException e) {
      // TODO Auto-generated catch block
      throw new RuntimeException(e);
    }

    return obs.list();
  }

  @Override
  public OptionalLong lastSerialBefore(@NonNull LocalDate date) {
    return OptionalLong.of(fs.lastSerialBefore(date));
  }

  public static class ListObserver implements FactObserver {
    private int limit;
    private int offset;

    public ListObserver(int limit, int offset) {
      this.limit = limit;
      this.offset = offset;
    }

    @Getter private final List<Fact> list = new LinkedList<>();

    @Override
    public void onNext(@NonNull Fact element) {
      if (offset > 0) {
        offset--;
      } else {
        if (limit > 0) {
          limit--;
          list.add(element);
        }
      }
    }

    boolean isComplete() {
      return limit == 0;
    }
  }
}
