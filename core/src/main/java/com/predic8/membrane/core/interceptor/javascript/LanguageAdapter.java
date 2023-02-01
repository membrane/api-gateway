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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;
import java.util.function.*;

public abstract  class LanguageAdapter {

    private static final Logger log = LoggerFactory.getLogger(JavascriptInterceptor.class);

    protected LanguageSupport languageSupport;
    protected Router router;

    public LanguageAdapter(Router router) {
        this.router = router;
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
            Class.forName("org.mozilla.javascript.engine.RhinoScriptEngine");
            log.info("Found Rhino Javascript engine.");
            return new RhinoJavascriptLanguageAdapter(router);
        } catch (Exception e) {
            // Ignore
        }
        throw new ConfigurationException("""
            Fatal Error: No Javascript Engine!
                    
            Membrane is configured to use Javascript, maybe in the proxies.xml file.
            The needed Javascript engine is not shipped with Membrane to spare size
            and to avoid security risks. However you can easiliy install an engine.
            
            We recommend to install the GraalVM Javascript Engine:
            
            1.) Download:
            
            https://repo1.maven.org/maven2/org/graalvm/js/js-scriptengine/22.3.0/js-scriptengine-22.3.0-javadoc.jar
            
            2.) Drop the JAR-file into MEMBRANE_HOME/libs
            3.) Start Membrane.
                   
            But you can also use the old Rhino Engine:
            
            https://repo1.maven.org/maven2/org/mozilla/rhino-engine/""");
    }

    public Function<Map<String, Object>, Object> compileScript(String script) throws IOException, ClassNotFoundException {
        return languageSupport.compileScript(router.getBackgroundInitializator(), router.getBeanFactory().getClassLoader(), prepareScript(script));
    }

    protected abstract String prepareScript(String src);
}
