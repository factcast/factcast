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
package org.factcast.schema.registry.cli.registry.templates

import org.factcast.schema.registry.cli.registry.templates.data.TransformationTemplateData

fun transformationTemplate(data: TransformationTemplateData): String {
    return """
+++
draft = false
title = "Transformations"
weight = -${data.maxVersion + 1}

[menu.main]
parent = "${data.ns}/${data.type}"
identifier = "${data.ns}/${data.type}/transformations"
+++
${data.transformations.joinToString("\n") {
        """
## From ${it.from} to ${it.to}
```javascript
${it.code}
```
        """.trimIndent()
    }}
    """.trimIndent()
}
