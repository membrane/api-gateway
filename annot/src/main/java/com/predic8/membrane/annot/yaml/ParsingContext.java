package com.predic8.membrane.annot.yaml;

import com.predic8.membrane.annot.*;

public record ParsingContext(String context, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {

    ParsingContext updateContext(String context) {
        return new ParsingContext(context, registry, k8sHelperGenerator);
    }
}