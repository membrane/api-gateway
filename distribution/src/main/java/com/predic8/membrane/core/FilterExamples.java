package com.predic8.membrane.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

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
            System.out.println("Source directory does not exist: " + src);
            return;
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
                Path targetDir = dest.resolve(src.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = dest.resolve(src.relativize(file));
                Files.copy(file, targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });

        // Remove proxies.xml in directories that contain apis.yaml
        try (var stream = Files.walk(dest)) {
            stream.filter(p -> p.getFileName().toString().equals("apis.yaml"))
                    .forEach(apis -> {
                        Path dir = apis.getParent();
                        Path proxies = dir.resolve("proxies.xml");
                        if (Files.exists(proxies)) {
                            try {
                                Files.delete(proxies);
                                System.out.println("Deleted " + proxies);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to delete " + proxies, e);
                            }
                        }
                    });
        }
    }
}
