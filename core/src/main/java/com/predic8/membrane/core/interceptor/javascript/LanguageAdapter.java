/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.javascript;

import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.lang.LanguageSupport;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.function.Function;

public abstract  class LanguageAdapter {

    private static final Logger log = LoggerFactory.getLogger(LanguageAdapter.class);

    protected LanguageSupport languageSupport;
    final protected Router router;

    protected static int preScriptLineLength;

    public LanguageAdapter(Router router) {
        this.router = router;
        preScriptLineLength = Math.toIntExact(getPreScript().lines().count());
    }

    protected String getPreScript() {
        return "";
    }

    protected String getPostScript() {
        return "";
    }

    protected String prepareScript(String script) {
        return getPreScript() + script + getPostScript();
    }

    public static LanguageAdapter instance(Router router) {
        try {
            Class.forName("org.graalvm.polyglot.Context");
            log.info("Found GraalVM Javascript engine.");
            return new GraalVMJavascriptLanguageAdapter(router);
        } catch (Exception e) {
            // Ignore
        }
        try {
            Class.forName("org.mozilla.javascript.Context");
            log.info("Found Rhino Javascript engine.");
            return new RhinoJavascriptLanguageAdapter(router);
        } catch (Exception e) {
            // Ignore
        }
        throw new ConfigurationException("""
            No Javascript engine found.

            The javascript plugin needs Rhino or the GraalVM Javascript engine. Rhino is
            part of the Membrane distribution (lib/rhino-*.jar). It may have been removed
            or, when Membrane is embedded, excluded from the dependencies.

            To use the GraalVM Javascript engine instead, see:

            https://membrane-api.io/plugins/javascript.html""");
    }

    public Function<Map<String, Object>, Object> compileScript(String script) {
        return languageSupport.compileScript(getClassLoader(), prepareScript(script));
    }

    private @Nullable ClassLoader getClassLoader() {
        ApplicationContext bf = router.getBeanFactory();
        if (bf == null) {
           return this.getClass().getClassLoader();
        }
        return bf.getClassLoader();
    }

    public abstract ProblemDetails getProblemDetails(Exception e);

}
