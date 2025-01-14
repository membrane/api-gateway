/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.lang.groovy;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.util.ConfigurationException;
import groovy.lang.*;
import org.codehaus.groovy.control.*;

import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.lang.ScriptingUtils.*;

public class GroovyExchangeExpression extends AbstractExchangeExpression {

    private final Function<Map<String, Object>, Object> script;
    private final Router router;

    public GroovyExchangeExpression(Router router, String source) {
        super(source);
        this.router = router;
        try {
            script = new GroovyLanguageSupport().compileScript(router.getBackgroundInitializator(), null, source);
        } catch (MultipleCompilationErrorsException e) {
            throw new ConfigurationException("Cannot compile Groovy Script.",e);
        }
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        Object o = null;
        try {
            o = script.apply(createParameterBindings(router, exchange, flow, false));
        } catch (MissingPropertyException mpe) {
            if (type.getName().equals(Object.class.getName())) {
                return null;
            }
            if (type.isAssignableFrom(String.class)) {
                return type.cast("");
            }
        }
        if (type.getName().equals(String.class.getName())) {
            if (o == null) {
                return type.cast("");
            }
            if (type.isInstance(o))
                return type.cast(o);
        }
        if (o == null) {
            return null;
        }
        return type.cast(o);
    }
}
