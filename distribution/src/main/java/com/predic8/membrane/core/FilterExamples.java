/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FilterExamples {

    /**
     * Args:
     *  args[0] = source directory (examples)
     *  args[1] = destination directory (examples-filtered)
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: FilterExamples <srcDir> <destDir>");
            System.exit(1);
        }

        Path src = Paths.get(args[0]);
        Path dest = Paths.get(args[1]);

        if (!Files.exists(src)) {
            System.err.println("Source directory does not exist: " + src);
            System.exit(1);
        }

        // Clean destination
        if (Files.exists(dest)) {
            try (var stream = Files.walk(dest)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to delete " + path, e);
                            }
                        });
            }
        }

        // Copy tree src -> dest
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir)));
                return CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dest.resolve(src.relativize(file)), REPLACE_EXISTING, COPY_ATTRIBUTES);
                return CONTINUE;
            }
        });

        // Remove proxies.xml in directories that contain apis.yaml
        List<Path> toRemove = new ArrayList<>();
        try (var stream = Files.walk(dest)) {
            stream.filter(p -> p.getFileName().toString().equals("apis.yaml"))
                    .forEach(apis -> {
                        Path dir = apis.getParent();
                        Path proxies = dir.resolve("proxies.xml");
                        if (Files.exists(proxies)) {
                            toRemove.add(proxies);
                        }
                    });
        }
        toRemove.forEach(proxies -> {
            try {
                Files.delete(proxies);
                System.out.println("Deleted " + proxies);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete " + proxies, e);
            }
        });
    }
}
