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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.beanregistry.BeanDefinition.SourceMetadata;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;
import com.predic8.membrane.annot.yaml.parsing.ParseSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.predic8.membrane.annot.yaml.parsing.source.SourceMetadataSupport.withSourceFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static java.util.List.of;
import static java.util.Locale.ROOT;

public final class IncludeResolver {

    private static final Logger log = LoggerFactory.getLogger(IncludeResolver.class);

    private final YamlDocumentReader documentReader;

    public IncludeResolver(YamlDocumentReader documentReader) {
        this.documentReader = documentReader;
    }

    public List<ResolvedDocument> resolve(ParseSession session, SourceMetadata sourceMetadata, String yaml) throws IOException {
        try {
            List<ResolvedDocument> documents = new ArrayList<>();
            for (ResolvedDocument document : documentReader.readDocuments(session, sourceMetadata, yaml)) {
                if (document.isIncludeDocument()) {
                    documents.addAll(resolveIncludes(session, document));
                    continue;
                }
                documents.add(document);
            }
            return documents;
        } catch (ConfigurationParsingException e) {
            if (e.getSourceFile() == null && sourceMetadata != null && sourceMetadata.sourceFile() != null) {
                e.setSourceFile(sourceMetadata.sourceFile());
            }
            throw e;
        }
    }

    private List<ResolvedDocument> resolveIncludes(ParseSession session, ResolvedDocument includeDocument) throws IOException {
        List<ResolvedDocument> documents = new ArrayList<>();
        for (IncludeEntry includeEntry : extractIncludeEntries(includeDocument)) {
            documents.addAll(parseIncludedPath(
                    session,
                    SourceMetadataSupport.resolveIncludePath(includeDocument.sourceMetadata(), includeEntry.path()),
                    includeDocument.sourceMetadata(),
                    includeEntry.parsingContext()));
        }
        return documents;
    }

    private List<IncludeEntry> extractIncludeEntries(ResolvedDocument includeDocument) {
        JsonNode includeNode = includeDocument.node().get("include");
        ParsingContext<?> includePc = includeDocument.parsingContext().key("include");
        if (includeNode == null || includeNode.isNull())
            return of();

        if (!includeNode.isArray())
            throw new ConfigurationParsingException("The 'include' value must be an array of strings.", null, includePc);

        List<IncludeEntry> includes = new ArrayList<>();
        ParsingContext<?> includeArrayPc = includePc.addPath(".include");
        for (int i = 0; i < includeNode.size(); i++) {
            JsonNode item = includeNode.get(i);
            if (!item.isTextual())
                throw new ConfigurationParsingException("The 'include' array must only contain strings.", null, includeArrayPc.key(String.valueOf(i)));
            includes.add(new IncludeEntry(item.asText(), includeArrayPc.key(String.valueOf(i))));
        }
        return includes;
    }

    private List<ResolvedDocument> parseIncludedPath(ParseSession session, Path includePath, SourceMetadata sourceMetadata, ParsingContext<?> includePc) throws IOException {
        if (!Files.exists(includePath))
            throw new ConfigurationParsingException("Included path '%s' does not exist.".formatted(includePath), null, includePc);

        if (Files.isDirectory(includePath)) {
            List<ResolvedDocument> documents = new ArrayList<>();
            try (var files = Files.list(includePath)) {
                for (Path file : files.filter(Files::isRegularFile).filter(IncludeResolver::isApisYaml).sorted().toList()) {
                    documents.addAll(parseIncludedFile(session, file, sourceMetadata, includePc));
                }
            }
            return documents;
        }

        if (!Files.isRegularFile(includePath))
            throw new ConfigurationParsingException("Included path '%s' is neither a regular file nor a directory.".formatted(includePath), null, includePc);

        return parseIncludedFile(session, includePath, sourceMetadata, includePc);
    }

    private List<ResolvedDocument> parseIncludedFile(ParseSession session, Path includeFile, SourceMetadata sourceMetadata, ParsingContext<?> includePc) throws IOException {
        Path normalizedFile = ParseSession.normalizePath(includeFile);
        if (session.includeStack().contains(normalizedFile))
            throw new ConfigurationParsingException("Cyclic include detected: " + formatIncludeCycle(session.includeStack(), normalizedFile), null, includePc);
        if (session.loadedIncludeFiles().contains(normalizedFile)) {
            log.debug("Skipping already included file '{}'.", normalizedFile);
            return of();
        }

        session.includeStack().addLast(normalizedFile);
        try {
            String includedYaml;
            try {
                includedYaml = readString(normalizedFile, UTF_8);
            } catch (IOException e) {
                ConfigurationParsingException cpe = new ConfigurationParsingException("Could not read included file '%s'.".formatted(normalizedFile), e, includePc);
                cpe.setSourceFile(normalizedFile);
                throw cpe;
            }

            try {
                List<ResolvedDocument> documents = resolve(session, withSourceFile(sourceMetadata, normalizedFile), includedYaml);
                session.loadedIncludeFiles().add(normalizedFile);
                return documents;
            } catch (JsonParseException e) {
                ConfigurationParsingException cpe = new ConfigurationParsingException(
                        "Invalid YAML in included file '%s' (at line %d, column %d)."
                                .formatted(normalizedFile, e.getLocation().getLineNr(), e.getLocation().getColumnNr()),
                        e,
                        includePc);
                cpe.setSourceFile(normalizedFile);
                throw cpe;
            }
        } finally {
            session.includeStack().removeLast();
        }
    }

    private static boolean isApisYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(ROOT);
        return name.endsWith(".apis.yaml") || name.endsWith(".apis.yml");
    }

    private static String formatIncludeCycle(Deque<Path> includeStack, Path repeatedPath) {
        List<String> cycle = new ArrayList<>();
        for (Path path : includeStack) {
            cycle.add(path.toString());
        }
        cycle.add(repeatedPath.toString());
        return String.join(" -> ", cycle);
    }

    private record IncludeEntry(String path, ParsingContext<?> parsingContext) {
    }
}
