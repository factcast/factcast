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
package org.factcast.core;

import java.util.*;

import lombok.*;
import lombok.experimental.*;

@UtilityClass
class FactValidation {

    private boolean lacksRequiredNamespace(Fact f) {
        return f.ns() == null || f.ns().trim().isEmpty();
    }

    private boolean lacksRequiredId(Fact f) {
        return f.id() == null;
    }

    public void validate(@NonNull List<? extends Fact> facts) {
        List<String> errors = new LinkedList<>();
        facts.forEach(f -> {
            if (lacksRequiredNamespace(f))
                errors.add("Fact " + f.id() + " lacks required namespace.");
            if (lacksRequiredId(f))
                errors.add("Fact " + f.jsonHeader() + " lacks required id.");
        });
        if (!errors.isEmpty())
            throw new FactValidationException(errors);
    }
}

class FactValidationException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public FactValidationException(List<String> errors) {
        super(render(errors));
    }

    private static String render(List<String> errors) {
        return String.join("\n", errors);
    }

}
