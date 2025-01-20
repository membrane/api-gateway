/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
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
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.lang.javascript.*;
import org.graalvm.polyglot.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;

public class GraalVMJavascriptLanguageAdapter extends LanguageAdapter {

    public GraalVMJavascriptLanguageAdapter(Router router) {
        super(router);
        languageSupport = new GraalVMJavascriptSupport();
    }

    @Override
    public ProblemDetails getProblemDetails(Exception e) {
        ProblemDetails pd = internal(router.isProduction(),"javascript");
        if (e instanceof PolyglotException pe) {
            pd.internal("column",  pe.getSourceLocation().getStartColumn());
            pd.internal("line", pe.getSourceLocation().getStartLine() - preScriptLineLength );
            pd.exception(pe);
        }

        return pd;
    }

    protected String getPreScript() {
        return """
                var FileClass = Java.type("java.io.File");
                var Request = Java.type("com.predic8.membrane.core.http.Request");
                var Response = Java.type("com.predic8.membrane.core.http.Response");
                """;
    }


}
