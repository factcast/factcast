/*
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
package org.factcast.core.lock;

import java.util.UUID;

import lombok.Getter;
import lombok.NonNull;

public final class ExceptionAfterPublish extends IllegalStateException {
    @Getter
    @NonNull
    private final UUID lastFactId;

    public ExceptionAfterPublish(@NonNull UUID lastFactId, @NonNull Throwable e) {
        super("An exception has happened in the 'andThen' part of your publishing attempt. This is a programming error, as the runnable in andThen is not supposed to throw an Exception. Note that publish actually worked, and the id of your last published fact is "
                + lastFactId,
                e);
        this.lastFactId = lastFactId;
    }

    private static final long serialVersionUID = 1L;

}
