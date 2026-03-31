/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.yaml.parsing;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.Grammar;
import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import com.predic8.membrane.annot.beanregistry.BeanLifecycleManager;
import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;
import com.predic8.membrane.annot.yaml.parsing.binding.ObjectBinder;
import com.predic8.membrane.annot.yaml.parsing.definition.BeanDefinitionExtractor;
import com.predic8.membrane.annot.yaml.parsing.definition.ComponentDefinitionExtractor;
import com.predic8.membrane.annot.yaml.parsing.source.IncludeResolver;
import com.predic8.membrane.annot.yaml.parsing.source.SourceMetadata;
import com.predic8.membrane.annot.yaml.parsing.source.YamlDocumentReader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.annot.yaml.NodeValidationUtils.ensureSingleKey;
import static com.predic8.membrane.annot.yaml.parsing.source.SourceMetadata.root;
import static java.nio.charset.StandardCharsets.UTF_8;

public class GenericYamlParser {

    private final List<BeanDefinition> beanDefs = new ArrayList<>();

    /**
     * Parses one or more YAML documents into bean definitions.
     *
     * @param grammar        provides schema location and Java type resolution
     * @param yaml           the raw YAML content (may contain multi-document stream)
     * @param rootSourceFile optional path to the root YAML file; used to resolve relative includes
     * @throws IOException if schema loading or validation fails
     */
    public GenericYamlParser(Grammar grammar, String yaml, Path rootSourceFile) throws IOException {
        IncludeResolver includeResolver = new IncludeResolver(new YamlDocumentReader(grammar));
        BeanDefinitionExtractor definitionExtractor = new BeanDefinitionExtractor(new ComponentDefinitionExtractor());
        SourceMetadata rootSourceMetadata = root(rootSourceFile);
        beanDefs.addAll(definitionExtractor.extract(
                new ParseSession(),
                includeResolver.resolve(new ParseSession(), rootSourceMetadata, yaml)));
    }

    /**
     * Parses one or more YAML documents into bean definitions.
     *
     * @param resource       the input stream to parse. The method takes care of closing the stream.
     * @param grammar        the grammar to use for type resolution and schema location
     * @param rootSourceFile optional path to the root YAML file
     * @return list of parsed bean definitions
     */
    public static List<BeanDefinition> parseMembraneResources(@NotNull InputStream resource, Grammar grammar, Path rootSourceFile) throws IOException {
        try (resource) {
            return parseToBeanDefinitions(resource, grammar, rootSourceFile);
        } catch (JsonParseException e) {
            throw new IOException("Invalid YAML: multiple configurations must be separated by '---' (at line %d, column %d).".formatted(e.getLocation().getLineNr(), e.getLocation().getColumnNr()), e);
        }
    }

    private static List<BeanDefinition> parseToBeanDefinitions(@NotNull InputStream resource, Grammar grammar, Path rootSourceFile) throws IOException {
        return new GenericYamlParser(grammar, new String(resource.readAllBytes(), UTF_8), rootSourceFile)
                .getBeanDefinitions();
    }

    public List<BeanDefinition> getBeanDefinitions() {
        return beanDefs;
    }

    /**
     * Parse a top-level Membrane resource of the given {@code kind}.
     * <p>Ensures the node contains exactly one key (the kind), resolves the Java class via the
     * grammar and delegates to {@link #createAndPopulateNode(ParsingContext, Class, JsonNode)}.</p>
     */
    public static <R extends BeanRegistry & BeanLifecycleManager> Object readMembraneObject(String kind, Grammar grammar, JsonNode node, R registry) throws ConfigurationParsingException {
        return createAndPopulateNode(new ParsingContext<>(kind, registry, grammar, node, "$." + kind, null), decideClazz(kind, grammar, node), node.get(kind));
    }

    /**
     * Detects the class that will be selected to represent the node in Java.
     */
    public static Class<?> decideClazz(String kind, Grammar grammar, JsonNode node) {
        ensureSingleKey(new ParsingContext("", null, grammar, node, "$", null), node);
        Class<?> clazz = grammar.getElement(kind);
        if (clazz == null) {
            var pc = new ParsingContext("", null, grammar, node, "$", null).key(kind);
            throw new ConfigurationParsingException("Did not find java class for kind '%s'.".formatted(kind), null, pc);
        }
        return clazz;
    }

    /**
     * Creates and populates an instance of {@code clazz} from the given YAML/JSON node.
     * - Arrays: only valid for {@code @MCElement(noEnvelope=true)}; items are parsed and passed to the single {@code @MCChildElement} list setter.
     * - Objects: each field is mapped to a setter resolved by {@link MethodSetter#getMethodSetter(ParsingContext, Class, String)};
     * values are produced by {@link MethodSetter#getMethodSetter(ParsingContext, Class, String)}. A top-level {@code "$ref"} injects a previously defined bean.
     * All failures are wrapped in a {@link ConfigurationParsingException} with location information.
     */
    public static <T> T createAndPopulateNode(ParsingContext<?> pc, Class<T> clazz, JsonNode node) throws ConfigurationParsingException {
        return ObjectBinder.bind(pc, clazz, node);
    }

}
