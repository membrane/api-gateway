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

public record SourceContext(Path sourceFile, Path rootSourceFile) {

    public static SourceContext root(Path rootSourceFile) {
        Path normalized = normalizePath(rootSourceFile);
        return new SourceContext(normalized, normalized);
    }

    public SourceContext withSourceFile(Path sourceFile) {
        return new SourceContext(normalizePath(sourceFile), rootSourceFile);
    }

    public SourceMetadata sourceMetadata() {
        Path normalizedSourceFile = normalizePath(sourceFile);
        Path basePath = normalizedSourceFile != null && normalizedSourceFile.getParent() != null
                ? normalizePath(normalizedSourceFile.getParent())
                : null;
        return new SourceMetadata(basePath, normalizedSourceFile, rootSourceFile);
    }

    public Path resolveIncludePath(String includeEntry) {
        Path includePath = Path.of(includeEntry);
        if (includePath.isAbsolute())
            return normalizePath(includePath);

        Path baseDir = sourceFile != null && sourceFile.getParent() != null
                ? sourceFile.getParent()
                : rootSourceFile != null && rootSourceFile.getParent() != null
                ? rootSourceFile.getParent()
                : normalizePath(Path.of("."));
        return normalizePath(baseDir.resolve(includePath));
    }
}
