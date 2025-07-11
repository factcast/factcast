/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.server.ui.id;

import com.vaadin.flow.data.binder.Result;
import com.vaadin.flow.data.binder.ValueContext;
import com.vaadin.flow.data.converter.Converter;

/**
 * Converts the selected version from String to Integer if a specific version was set. Returns 0 if
 * the value equals AS_PUBLISHED which will query for the published version.
 */
public class SelectedVersionConverter implements Converter<String, Integer> {

  static final String AS_PUBLISHED = "as published";

  @Override
  public Result<Integer> convertToModel(String s, ValueContext valueContext) {
    if (s != null && !s.equals(AS_PUBLISHED)) {
      try {
        return Result.ok(Integer.parseInt(s));
      } catch (Exception e) {
        return Result.error(String.format("Failed to convert String %s to selected version", s));
      }
    }
    return Result.ok(0);
  }

  /**
   * Converts the selected version from Integer to String. Maps 0 to AS_PUBLISHED to comply with the
   * items of the version selector.
   */
  @Override
  public String convertToPresentation(Integer selectedVersion, ValueContext valueContext) {
    if (selectedVersion == null || selectedVersion == 0) {
      return AS_PUBLISHED;
    }
    return selectedVersion.toString();
  }
}
