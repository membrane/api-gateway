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
import com.predic8.membrane.core.util.*;

import java.util.*;
import java.util.regex.*;

/**
 * Selects the apis a discovery plugin publishes by their tags. Rules apply in order and the first
 * one whose tag the api carries decides. An api no rule matches is left out.
 */
public abstract class TagRule {

    private String tag;
    private Pattern pattern;

    public String getTag() {
        return tag;
    }

    @MCTextContent
    public void setTag(String tag) {
        this.tag = tag.strip();
        try {
            this.pattern = Pattern.compile(this.tag);
        } catch (PatternSyntaxException e) {
            throw new ConfigurationException("Invalid regular expression \"%s\" in a tags rule.".formatted(this.tag), e);
        }
    }

    private boolean matches(String apiTag) {
        return pattern.matcher(apiTag).matches();
    }

    abstract boolean includes();

    static boolean isListed(List<TagRule> rules, List<String> tags) {
        if (rules.isEmpty())
            return true;
        for (TagRule rule : rules)
            if (tags.stream().anyMatch(rule::matches))
                return rule.includes();
        return false;
    }

    /**
     * @description Publishes the apis that carry a tag this regular expression matches as a whole,
     * unless an earlier rule already decided. A plain name such as <code>external</code> matches
     * just that tag.
     * @example external
     */
    @MCElement(name = "include", collapsed = true, mixed = true, component = false, id = "apiDiscovery-include")
    public static class Include extends TagRule {
        @Override
        boolean includes() {
            return true;
        }
    }

    /**
     * @description Leaves out the apis that carry a tag this regular expression matches as a whole,
     * unless an earlier rule already decided. A plain name such as <code>internal</code> matches
     * just that tag.
     * @example internal|staging-.*
     */
    @MCElement(name = "exclude", collapsed = true, mixed = true, component = false, id = "apiDiscovery-exclude")
    public static class Exclude extends TagRule {
        @Override
        boolean includes() {
            return false;
        }
    }
}
