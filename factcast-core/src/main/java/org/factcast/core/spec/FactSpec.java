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
package org.factcast.core.spec;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NonNull;

/**
 * Defines a Specification of facts to match for a subscription.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FactSpec {

    @NonNull
    @JsonProperty
    final String ns;

    // type & aggId should probably be sets?
    @JsonProperty
    String type = null;

    @JsonProperty
    int version = 0; // 0 means I don't care

    @JsonProperty
    UUID aggId = null;

    @JsonProperty
    @Deprecated
    String jsFilterScript = null;

    @JsonProperty
    FilterScript filterScript = null;

    @NonNull
    @JsonProperty
    final Map<String, String> meta = new HashMap<>();

    public FactSpec meta(@NonNull String k, @NonNull String v) {
        meta.put(k, v);
        return this;
    }

    public static FactSpec ns(String ns) {
        return new FactSpec(ns);
    }

    public FactSpec(@NonNull @JsonProperty("ns") String ns) {
        super();
        this.ns = ns;
    }

    public FilterScript filterScript() {
        if (filterScript != null)
            return filterScript;
        else if (jsFilterScript != null)
            return new FilterScript("js", jsFilterScript);
        else
            return null;
    }

    public FactSpec filterScript(FilterScript script) {
        if (script != null) {
            this.filterScript = script;
            if ("js".equals(script.languageIdentifier()))
                jsFilterScript = script.source();
        } else {
            filterScript = null;
            jsFilterScript = null;
        }

        return this;
    }

    @Deprecated
    public FactSpec jsFilterScript(String script) {
        if (script != null)
            filterScript(new FilterScript("js", script));
        else
            filterScript(null);

        return this;
    }

    @Deprecated
    public String jsFilterScript() {
        if (filterScript != null && "js".equals(filterScript.languageIdentifier()))
            return filterScript.source();
        else if (filterScript == null && jsFilterScript != null)
            return jsFilterScript;
        else
            return null;
    }

    public static <T> FactSpec from(Class<T> clazz) {
        Specification annotationSpec = clazz.getAnnotation(Specification.class);

        if (annotationSpec == null) {
            throw new IllegalArgumentException("You must annotate your Payload class with @"
                    + Specification.class.getSimpleName());
        }

        FactSpec factSpec = new FactSpec(annotationSpec.ns());

        if (!annotationSpec.type().isEmpty()) {
            factSpec.type(annotationSpec.type());
        } else
            factSpec.type(clazz.getSimpleName());

        factSpec.version(annotationSpec.version());

        return factSpec;
    }
}
