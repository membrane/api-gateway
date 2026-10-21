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

package com.predic8.membrane.core.lang.groovy;

import groovy.lang.*;
import org.codehaus.groovy.runtime.*;

import static com.predic8.membrane.core.lang.ScriptingUtils.BINDING;

/**
 * Base class of Groovy scripts and expressions. Calls of methods the script does not have are resolved on the
 * built-in functions bound as "fn", so scripts can call e.g. xpath('//id') as well as fn.xpath('//id').
 */
public abstract class BuiltInFunctionsScript extends Script {

    @SuppressWarnings("unused") // Called by Groovy
    public Object methodMissing(String name, Object args) {
        final var arguments = InvokerHelper.asArray(args);
        if (getBinding().hasVariable(BINDING)) {
            final var functions = getBinding().getVariable(BINDING);
            if (!InvokerHelper.getMetaClass(functions).respondsTo(functions, name, arguments).isEmpty())
                return InvokerHelper.invokeMethod(functions, name, arguments);
        }
        throw new MissingMethodException(name, getClass(), arguments);
    }
}
