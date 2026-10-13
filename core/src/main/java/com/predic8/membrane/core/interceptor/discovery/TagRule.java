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
package com.predic8.membrane.core.interceptor.discovery;

import com.predic8.membrane.annot.*;

import java.util.*;

/**
 * Selects the apis a discovery plugin publishes by their tags. Rules apply in order and the first
 * one whose tag the api carries decides. An api no rule matches is left out.
 */
public abstract class TagRule {

    private String tag;

    public String getTag() {
        return tag;
    }

    @MCTextContent
    public void setTag(String tag) {
        this.tag = tag.strip();
    }

    abstract boolean includes();

    /**
     * Whether an api with the given tags is published. Without rules, every api is.
     */
    static boolean isListed(List<TagRule> rules, List<String> tags) {
        if (rules.isEmpty())
            return true;
        for (TagRule rule : rules)
            if (tags.contains(rule.getTag()))
                return rule.includes();
        return false;
    }

    /**
     * @description Publishes the apis that carry this tag, unless an earlier rule already decided.
     */
    @MCElement(name = "include", collapsed = true, mixed = true, component = false, id = "apiDiscovery-include")
    public static class Include extends TagRule {
        @Override
        boolean includes() {
            return true;
        }
    }

    /**
     * @description Leaves out the apis that carry this tag, unless an earlier rule already decided.
     */
    @MCElement(name = "exclude", collapsed = true, mixed = true, component = false, id = "apiDiscovery-exclude")
    public static class Exclude extends TagRule {
        @Override
        boolean includes() {
            return false;
        }
    }
}
