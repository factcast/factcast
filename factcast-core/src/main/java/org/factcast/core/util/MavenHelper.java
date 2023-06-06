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
package org.factcast.core.util;

import com.google.common.annotations.VisibleForTesting;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.factcast.core.FactCast;

@UtilityClass
public class MavenHelper {
  public static Optional<String> getVersion() {
    return getVersion("factcast-core", FactCast.class);
  }

  public static Optional<String> getVersion(
      @NonNull String artifact, @NonNull Class<?> contextClass) {
    return getImplVersion(
        contextClass.getResource("/META-INF/maven/org.factcast/" + artifact + "/pom.properties"));
  }

  @VisibleForTesting
  static Optional<String> getImplVersion(URL resource) {
    String implVersion = null;

    Properties buildProperties = new Properties();
    if (resource != null) {
      try (InputStream is = resource.openStream()) {
        if (is != null) {
          buildProperties.load(is);
          String v = buildProperties.getProperty("version");
          if (v != null) {
            implVersion = v;
          }
        }
      } catch (Exception ignore) {
        // we'll fall back to empty
      }
    }
    return Optional.ofNullable(implVersion);
  }
}
