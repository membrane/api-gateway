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

package com.predic8.membrane.annot.yaml.parsing.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import com.predic8.membrane.annot.beanregistry.BeanDefinitionContext;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.McYamlIntrospector;
import com.predic8.membrane.annot.yaml.ParsingContext;
import com.predic8.membrane.annot.yaml.parsing.MethodSetter;
import com.predic8.membrane.annot.yaml.parsing.support.BeanLifecycleInvoker;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.annot.yaml.McYamlIntrospector.findRequiredSetters;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.findSingleSetterOrNullForAnnotation;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.getSingleChildSetter;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.isCollapsed;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.isNoEnvelope;
import static com.predic8.membrane.annot.yaml.NodeValidationUtils.ensureMappingStart;

public final class ObjectBinder {

    private static final Logger log = LoggerFactory.getLogger(ObjectBinder.class);

    private static final BeanLifecycleInvoker LIFECYCLE_INVOKER = new BeanLifecycleInvoker();
    private static final ReferenceResolver REFERENCE_RESOLVER = new ReferenceResolver();

    public static <T> T bind(ParsingContext<?> pc, Class<T> clazz, JsonNode node) throws ConfigurationParsingException {
        try {
            T configObj = clazz.getConstructor().newInstance();
            BeanDefinition currentBeanDefinition = BeanDefinitionContext.current();
            if (currentBeanDefinition != null && pc.getRegistry() != null) {
                pc.getRegistry().rememberBeanDefinition(configObj, currentBeanDefinition);
            }

            if (node.isArray())
                return LIFECYCLE_INVOKER.apply(pc, handleNoEnvelopeList(pc, clazz, node, configObj));

            if (isCollapsed(clazz))
                return handleCollapsed(pc, clazz, node, configObj);

            ensureMappingStart(node);
            if (isNoEnvelope(clazz)) {
                log.error("Class {} is annotated with @MCElement(noEnvelope=true), but the YAML/JSON structure does not contain a list.", clazz.getName());
                throw new ConfigurationParsingException("Class %s is annotated with @MCElement(noEnvelope=true), but the YAML/JSON structure does not contain a list.".formatted(clazz.getName()), null, pc);
            }

            JsonNode refNode = node.get("$ref");
            if (refNode != null)
                REFERENCE_RESOLVER.applyObjectLevelRef(pc, clazz, node, refNode, configObj);

            List<Method> required = findRequiredSetters(clazz);
            PropertyBinder.populate(pc, clazz, node, required, configObj);

            if (!required.isEmpty())
                throw new ConfigurationParsingException("Missing required fields: " + required.stream().map(McYamlIntrospector::getSetterName).toList());
            return LIFECYCLE_INVOKER.apply(pc, configObj);
        } catch (NoClassDefFoundError e) {
            if (e.getCause() != null) {
                String missingClass = e.getCause().getMessage();
                String msg = "Could not create bean with class: %s\nMissing class: %s\n".formatted(clazz, missingClass);
                log.error(msg);
                var cpe = new ConfigurationParsingException(msg);
                applyCurrentSourceFileIfMissing(cpe);
                throw cpe;
            }
            var cpe = new ConfigurationParsingException(e);
            applyCurrentSourceFileIfMissing(cpe);
            throw cpe;
        } catch (ConfigurationParsingException e) {
            applyCurrentSourceFileIfMissing(e);
            if (e.getParsingContext() == null)
                e.setParsingContext(pc);
            throw e;
        } catch (Throwable cause) {
            log.debug("", cause);
            var cpe = new ConfigurationParsingException(cause);
            applyCurrentSourceFileIfMissing(cpe);
            throw cpe;
        }
    }

    private static <T> @NotNull T handleCollapsed(ParsingContext<?> ctx, Class<T> clazz, JsonNode node, T configObj) {
        if (node.isNull())
            throw new ConfigurationParsingException("Collapsed element must not be null.");
        if (node.isArray() || node.isObject())
            throw new ConfigurationParsingException("Element is collapsed; expected an inline scalar value, not an %s.".formatted(node.isArray() ? "array" : "object"));
        applyCollapsedScalar(clazz, node, configObj);
        return LIFECYCLE_INVOKER.apply(ctx, configObj);
    }

    private static <T> T handleNoEnvelopeList(ParsingContext<?> pc, Class<T> clazz, JsonNode node, T configObj) throws IllegalAccessException, InvocationTargetException {
        Method childSetter = getSingleChildSetter(pc, clazz);
        childSetter.invoke(configObj, CollectionBinder.parseListExcludingStartEvent(pc, node, MethodSetter.getCollectionElementType(childSetter)));
        return configObj;
    }

    @SuppressWarnings("ConstantValue")
    private static <T> void applyCollapsedScalar(Class<T> clazz, JsonNode node, T target) {
        Method attributeSetter = findSingleSetterOrNullForAnnotation(clazz, MCAttribute.class);
        Method textSetter = findSingleSetterOrNullForAnnotation(clazz, MCTextContent.class);
        Method scalarSetter = attributeSetter != null ? attributeSetter : textSetter;

        if (scalarSetter == null) {
            throw new ConfigurationParsingException(
                    "@MCElement(collapsed=true) requires exactly one @MCAttribute or exactly one @MCTextContent.");
        }

        Class<?> paramType = Objects.requireNonNull(scalarSetter).getParameterTypes()[0];

        Object value;
        try {
            value = ScalarValueConverter.convertScalarOrSpel(node, paramType);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationParsingException("Cannot convert inline value to %s.".formatted(paramType.getSimpleName()));
        }

        try {
            scalarSetter.setAccessible(true);
            scalarSetter.invoke(target, value);
        } catch (InvocationTargetException e) {
            throw new ConfigurationParsingException(e.getTargetException());
        } catch (Throwable t) {
            throw new ConfigurationParsingException(t);
        }
    }

    private static void applyCurrentSourceFileIfMissing(ConfigurationParsingException e) {
        if (e.getSourceFile() != null)
            return;
        BeanDefinition currentBeanDefinition = BeanDefinitionContext.current();
        if (currentBeanDefinition == null || currentBeanDefinition.getSourceMetadata() == null || currentBeanDefinition.getSourceMetadata().sourceFile() == null)
            return;
        e.setSourceFile(currentBeanDefinition.getSourceMetadata().sourceFile().toAbsolutePath().normalize());
    }
}
