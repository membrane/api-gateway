package com.predic8.membrane.annot;

import java.util.List;

public interface K8sHelperGenerator {

    Class<?> getElement(String key);

    Class<?> getLocal(String context, String key);

    List<String> getCrdSingularNames();

}