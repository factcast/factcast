package org.factcast.schema.registry.cli

import java.nio.file.Path
import java.nio.file.Paths

fun fixture(file: String): Path =
    Paths.get("src","test", "resources", "fixtures", file)