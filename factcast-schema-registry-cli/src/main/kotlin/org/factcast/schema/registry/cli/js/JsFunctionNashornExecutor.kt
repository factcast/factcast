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
package org.factcast.schema.registry.cli.js

import org.factcast.schema.registry.cli.fs.FileSystemService
import java.nio.file.Path
import javax.inject.Singleton
import javax.script.Invocable
import javax.script.ScriptEngineManager

@Singleton
class JsFunctionNashornExecutor(
    private val fileSystemService: FileSystemService
) : JsFunctionExecutor {
    override fun <T> execute(functionName: String, pathToFile: Path, data: T): T {
        val code = fileSystemService.readToString(pathToFile.toFile())
        return execute(functionName, code, data)
    }

    override fun <T> execute(functionName: String, code: String, data: T): T {
        val engine = ScriptEngineManager().getEngineByName("nashorn")
        engine.eval(code)
        val invocable = engine as Invocable

        invocable.invokeFunction(functionName, data)

        return data
    }
}