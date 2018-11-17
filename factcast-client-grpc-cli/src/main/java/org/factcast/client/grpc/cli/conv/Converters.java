/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.client.grpc.cli.conv;

import java.util.UUID;
import java.util.function.Function;

import com.beust.jcommander.IStringConverterInstanceFactory;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.BaseConverter;

public class Converters {

    public static IStringConverterInstanceFactory factory() {
        return (param, clazz, name) -> {
            if (clazz == UUID.class)
                return new SimpleConverter<>(param.description(), UUID.class, UUID::fromString);
            if (clazz == ExistingJsonFile.class)
                return new SimpleConverter<>(param.description(), ExistingJsonFile.class,
                        ExistingJsonFile::new);
            return null;
        };
    }

    static class SimpleConverter<T> extends BaseConverter<T> {

        private final Function<String, T> l;

        private final Class<T> clazz;

        public SimpleConverter(String optionName, Class<T> clazz, Function<String, T> l) {
            super(optionName);
            this.clazz = clazz;
            this.l = l;
        }

        @Override
        public T convert(String value) {
            try {
                return l.apply(value);
            } catch (Exception e) {
                throw new ParameterException(getErrorString(value, clazz.getCanonicalName()) + " : "
                        + e.getMessage());
            }
        }
    }
}
