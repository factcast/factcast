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
package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.digest.DigestUtils
import org.factcast.schema.registry.cli.fs.FileSystemService
import java.nio.file.Path
import javax.inject.Singleton

@Singleton
class ChecksumServiceImpl(
    private val fileSystemService: FileSystemService,
    private val om: ObjectMapper
) : ChecksumService {

    override fun createMd5Hash(json: JsonNode): String {
        return createMd5Hash(om.writeValueAsBytes(json))
    }

    override fun createMd5Hash(file: Path): String {
        return createMd5Hash(fileSystemService.readToBytes(file))
    }

    private fun createMd5Hash(byteArray: ByteArray): String {
        return DigestUtils.md5Hex(byteArray)
    }
}
