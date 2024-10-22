/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.core.snap.local.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Iterator;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

public class PathWithException implements Path {
  private final String path;

  public PathWithException(String path) {
    this.path = path;
  }

  @Override
  @SneakyThrows
  public @NotNull FileSystem getFileSystem() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public boolean isAbsolute() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public Path getRoot() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public Path getFileName() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public Path getParent() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public int getNameCount() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path getName(int index) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path subpath(int beginIndex, int endIndex) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public boolean startsWith(@NotNull Path other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public boolean startsWith(@NotNull String other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public boolean endsWith(@NotNull Path other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public boolean endsWith(@NotNull String other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path normalize() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path resolve(@NotNull Path other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path resolve(@NotNull String other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path resolveSibling(@NotNull Path other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path resolveSibling(@NotNull String other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path relativize(@NotNull Path other) {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull URI toUri() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Path toAbsolutePath() {
    throw new IOException();
  }

  @Override
  public @NotNull Path toRealPath(@NotNull LinkOption... options) throws IOException {
    throw new IOException();
  }

  @Override
  public @NotNull File toFile() {
    // Intentionally not throwing an exception here for the sake of testing
    return new File(path);
  }

  @Override
  public @NotNull WatchKey register(
      @NotNull WatchService watcher,
      @NotNull WatchEvent.Kind<?>[] events,
      WatchEvent.Modifier... modifiers)
      throws IOException {
    throw new IOException();
  }

  @Override
  public @NotNull WatchKey register(
      @NotNull WatchService watcher, @NotNull WatchEvent.Kind<?>... events) throws IOException {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public @NotNull Iterator<Path> iterator() {
    throw new IOException();
  }

  @Override
  @SneakyThrows
  public int compareTo(@NotNull Path other) {
    throw new IOException();
  }

  @Override
  public String toString() {
    return path;
  }
}
