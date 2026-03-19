package com.predic8.membrane.core.router;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @description TODO
 */
@MCElement(name = "include", topLevel = true, noEnvelope = true, component = false)
public class IncludeList {

    List<String> includes = new ArrayList<>();

    /**
     * @description TODO
     */
    @MCChildElement(allowForeign = true)
    public void setInclude(List<String> include) {
        this.includes = include;
    }

    public List<String> getInclude() {
        return includes;
    }

}
