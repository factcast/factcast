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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.server.ui.full.FullQueryBean;
import org.factcast.server.ui.id.IdQueryBean;
import org.factcast.server.ui.port.FactRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FactRepositoryImpl implements FactRepository {

  final FactStore fs;

  @Override
  public Optional<Fact> findBy(@NonNull IdQueryBean bean) {

    UUID id = bean.getId();
    if (id == null) {
      return Optional.empty();
    }

    int v = Optional.ofNullable(bean.getVersion()).orElse(0);
    return fs.fetchByIdAndVersion(id, v);
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
    return ns;
  }

  @Override
  public List<String> types(Optional<String> input) {
    // TODO
    List<String> ns =
        List.copyOf(
            fs.enumerateNamespaces().stream()
                .map(n -> fs.enumerateTypes(n))
                .flatMap(Set::stream)
                .toList());
    if (input.isPresent()) {
      Pattern ptn = Pattern.compile(".*" + input.get() + ".*");
      ns = ns.stream().filter(s -> ptn.matcher(s).matches()).toList();
    }
    return ns;
  }
}
