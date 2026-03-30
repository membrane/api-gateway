package com.predic8.membrane.annot.yaml.parsing;

import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;


public record IncludeContext(Path sourceFile, Path basePath, Path rootSourceFile, Deque<Path> includeStack, Set<Path> loadedIncludeFiles) {

    static IncludeContext root(Path sourceFile) {
        Path normalizedRootSourceFile = normalizePath(sourceFile);
        return new IncludeContext(normalizedRootSourceFile, null, normalizedRootSourceFile, new ArrayDeque<>(), new HashSet<>());
    }

    IncludeContext withSourceFile(Path sourceFile) {
        return new IncludeContext(normalizePath(sourceFile), basePath, rootSourceFile, includeStack, loadedIncludeFiles);
    }

    BeanDefinition.SourceMetadata sourceMetadata() {
        Path normalizedSourceFile = normalizePath(sourceFile);
        return new BeanDefinition.SourceMetadata(getBasePath(normalizedSourceFile), normalizedSourceFile, rootSourceFile);
    }

    private @Nullable Path getBasePath(Path normalizedSourceFile) {
        return normalizedSourceFile != null && normalizedSourceFile.getParent() != null
                ? normalizePath(normalizedSourceFile.getParent())
                : normalizePath(basePath);
    }

    Path resolveIncludePath(String includeEntry) {
        Path includePath = Path.of(includeEntry);
        if (includePath.isAbsolute())
            return normalizePath(includePath);

        Path baseDir = basePath != null ? basePath
                : sourceFile != null && sourceFile.getParent() != null
                ? sourceFile.getParent()
                : normalizePath(Path.of("."));
        return normalizePath(baseDir.resolve(includePath));
    }

    static Path normalizePath(Path path) {
        if (path == null)
            return null;
        return path.toAbsolutePath().normalize();
    }
}
