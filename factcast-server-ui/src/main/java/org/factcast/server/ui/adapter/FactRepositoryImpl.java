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

import com.helger.commons.functional.Predicates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
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
  public List<Fact> findBy(FullQueryBean query) {
    throw new NotImplementedException();
  }

  @Override
  public List<String> namespaces(Optional<String> input) {
    List<String> ns = List.copyOf(fs.enumerateNamespaces());
    if (input.isPresent()) {
      Pattern ptn = Pattern.compile(".*" + input.get() + ".*");
      ns = ns.stream().filter(s -> ptn.matcher(s).matches()).toList();
    }
    return ns.stream().filter(securityService::canRead).toList();
  }

  @Override
  public List<String> types(@NonNull String namespace, @NonNull Optional<String> input) {
    if (!securityService.canRead(namespace)) {
      return List.of();
    }

    Predicate<String> f = Predicates.all();
    if (input.isPresent()) {
      Pattern ptn = Pattern.compile(".*" + input.get() + ".*");
      f = s -> ptn.matcher(s).matches();
    }

    return fs.enumerateTypes(namespace).stream().filter(f).toList();
  }

  @Override
  public long latestSerial() {
    return 199L;
  }

  @Override
  public Optional<UUID> findIdOfSerial(long longValue) {
    // TODO
    return Optional.of(UUID.randomUUID());
    // return fs.fetchBySerial(longValue).map(Fact::id);
  }

  @Override
  public List<Fact> fetchChunk(FullQueryBean bean) {

    List<FactSpec> specs = bean.createFactSpecs();

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
    // TODO
    return OptionalLong.of(3);
  }

  class ListObserver implements FactObserver {
    private int limit;
    private int offset;

    public ListObserver(int limit, int offset) {
      this.limit = limit;
      this.offset = offset;
    }

    @Getter private List<Fact> list = new LinkedList<>();

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
