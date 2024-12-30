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
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;

import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ScriptingUtils.*;

public class GroovyExchangeExpression implements ExchangeExpression {

    private final Function<Map<String, Object>, Boolean> condition;
    private final Router router;

    public GroovyExchangeExpression(Router router, String source) {
        this.router = router;
        condition = new GroovyLanguageSupport().compileExpression(router.getBackgroundInitializator(), null, source);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        return type.cast(condition.apply(getParametersForGroovy(exchange, flow)));
    }

    private HashMap<String, Object> getParametersForGroovy(Exchange exc, Interceptor.Flow flow) {
        return new HashMap<>() {{
            put("Outcome", Outcome.class);
            put("RETURN", RETURN);
            put("CONTINUE", CONTINUE);
            put("ABORT", ABORT);
            put("spring", router.getBeanFactory());
            put("exc", exc);
            put("exchange", exc);
            putAll(createParameterBindings(router.getUriFactory(), exc, flow, false));
        }};
    }
}
