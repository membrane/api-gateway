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

package com.predic8.membrane.core.interceptor.headerfilter;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.*;

import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.Action.KEEP;
import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.Action.REMOVE;
import static java.util.regex.Pattern.*;

public class HeaderFilterRule {

    private final Action action;

    private String pattern;
    private Pattern p;

    public HeaderFilterRule(Action action) {
        this.action = action;
    }

    public HeaderFilterRule(String pattern, Action action) {
        this(action);
        setPattern(pattern);
    }

    public static HeaderFilterRule remove(String pattern) {
        return new HeaderFilterRule(pattern, REMOVE);
    }

    public static HeaderFilterRule keep(String pattern) {
        return new HeaderFilterRule(pattern, KEEP);
    }

    public String getPattern() {
        return pattern;
    }

    @MCTextContent
    public void setPattern(String pattern) {
        this.pattern = pattern;
        p = Pattern.compile(pattern, CASE_INSENSITIVE);
    }

    public boolean matches(HeaderField hf) {
        return matches(hf.getHeaderName().getName());
    }

    public boolean matches(String header) {
        return p.matcher(header).matches();
    }

    public Action getAction() {
        return action;
    }

}
