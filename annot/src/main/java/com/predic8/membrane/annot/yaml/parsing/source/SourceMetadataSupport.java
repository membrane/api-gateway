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

package com.predic8.membrane.annot.yaml.parsing.source;

import com.predic8.membrane.annot.beanregistry.BeanDefinition.SourceMetadata;

import java.nio.file.Path;

import static com.predic8.membrane.annot.yaml.parsing.ParseSession.normalizePath;

public final class SourceMetadataSupport {

    private SourceMetadataSupport() {
    }

    public static SourceMetadata root(Path rootSourceFile) {
        Path normalized = normalizePath(rootSourceFile);
        return new SourceMetadata(
                determineBasePath(normalized, null),
                normalized,
                normalized);
    }

    public static SourceMetadata withSourceFile(SourceMetadata metadata, Path sourceFile) {
        Path normalizedSourceFile = normalizePath(sourceFile);
        return new SourceMetadata(
                determineBasePath(normalizedSourceFile, metadata == null ? null : metadata.basePath()),
                normalizedSourceFile,
                metadata == null ? null : metadata.rootSourceFile());
    }

    public static Path resolveIncludePath(SourceMetadata metadata, String includeEntry) {
        Path includePath = Path.of(includeEntry);
        if (includePath.isAbsolute())
            return normalizePath(includePath);

        Path baseDir = metadata != null && metadata.basePath() != null
                ? metadata.basePath()
                : metadata != null && metadata.rootSourceFile() != null && metadata.rootSourceFile().getParent() != null
                ? metadata.rootSourceFile().getParent()
                : normalizePath(Path.of("."));
        return normalizePath(baseDir.resolve(includePath));
    }

    private static Path determineBasePath(Path sourceFile, Path fallbackBasePath) {
        if (sourceFile != null && sourceFile.getParent() != null)
            return normalizePath(sourceFile.getParent());
        return normalizePath(fallbackBasePath);
    }
}
