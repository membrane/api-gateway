package com.predic8.membrane.core.kubernetes;

import org.yaml.snakeyaml.error.*;

public class PublicMarkedYAMLException extends MarkedYAMLException {
    protected PublicMarkedYAMLException(String context, Mark contextMark, String problem, Mark problemMark, String note) {
        super(context, contextMark, problem, problemMark, note);
    }
}
