package com.predic8.membrane.annot.yaml;

import com.predic8.membrane.annot.*;

public record ParsingContext(String context, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {

    ParsingContext updateContext(String context) {
        return new ParsingContext(context, registry, k8sHelperGenerator);
    }

    public Class<?> resolveClass(String key) {
        Class<?> clazz = k8sHelperGenerator.getLocal(context, key);
        if (clazz == null)
            clazz = k8sHelperGenerator.getElement(key);
        if (clazz == null)
            throw new RuntimeException("Did not find java class for key '%s'.".formatted(key));
        return clazz;
    }
}