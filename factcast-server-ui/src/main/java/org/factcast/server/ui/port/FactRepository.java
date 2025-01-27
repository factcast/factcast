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
package org.factcast.server.ui.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.server.ui.full.FullFilterBean;
import org.factcast.server.ui.id.IdQueryBean;
import org.factcast.server.ui.report.ReportFilterBean;

public interface FactRepository {

  Optional<Fact> findBy(@NonNull IdQueryBean bean);

  List<String> namespaces(@Nullable String optionalInput);

  List<String> types(@NonNull String namespace, @Nullable String optionalInput);

  OptionalLong lastSerialBefore(@NonNull LocalDate date);

  long latestSerial();

  Optional<UUID> findIdOfSerial(long longValue);

  List<Fact> fetchChunk(FullFilterBean bean);

  List<Fact> fetchAll(ReportFilterBean bean);
}
