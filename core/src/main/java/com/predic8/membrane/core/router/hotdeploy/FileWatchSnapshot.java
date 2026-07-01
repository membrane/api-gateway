/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.router.hotdeploy;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Snapshot of the currently watched YAML files and their last-modified timestamps.
 */
final class FileWatchSnapshot {

    private final List<WatchedPath> files;

    private FileWatchSnapshot(List<WatchedPath> files) {
        this.files = files;
    }

    static FileWatchSnapshot capture(Collection<Path> files) {
        LinkedHashSet<Path> trackedPaths = new LinkedHashSet<>();
        for (Path file : files) {
            trackedPaths.add(file.toAbsolutePath().normalize());
        }

        return new FileWatchSnapshot(trackedPaths.stream()
                .map(path -> new WatchedPath(path, lastModified(path)))
                .toList());
    }

    boolean hasChanged() {
        for (WatchedPath file : files) {
            if (lastModified(file.path()) != file.lastModified()) {
                return true;
            }
        }
        return false;
    }

    private static long lastModified(Path path) {
        return path.toFile().lastModified();
    }

    private record WatchedPath(Path path, long lastModified) {}
}
