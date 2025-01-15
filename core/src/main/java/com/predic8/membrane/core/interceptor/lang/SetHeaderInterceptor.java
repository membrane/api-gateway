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
package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;

@MCElement(name = "setHeader")
public class SetHeaderInterceptor extends AbstractSetterInterceptor {

    @SuppressWarnings("rawtypes")
    @Override
    protected Class getExpressionReturnType() {
        return String.class;
    }

    @Override
    protected boolean shouldSetValue(Exchange exc, Flow flow) {
        if (ifAbsent) {
            return !exc.getMessage(flow).getHeader().contains(fieldName);
        }
        return true;
    }

    @Override
    protected void setValue(Exchange exc, Flow flow, Object value) {
        exc.getMessage(flow).getHeader().setValue(fieldName, value.toString());
    }

    @Override
    public String getDisplayName() {
        return "setHeader";
    }

    @Override
    public String getShortDescription() {
        return "Sets the value of the HTTP header '%s' to the expression: %s.".formatted(fieldName, expression);
    }
}
