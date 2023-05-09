package org.factcast.schema.registry.cli

import java.nio.file.Path
import java.nio.file.Paths

fun fixture(path: String): Path =
    Paths.get("src", "test", "resources", "fixtures", path)
