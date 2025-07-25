/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.lang.spel.spelable;

import com.predic8.membrane.core.http.*;
import org.springframework.expression.*;

import static com.predic8.membrane.core.util.TextUtil.*;

public class SpELCookie implements SpELLablePropertyAware {

    private final Header header;

    public SpELCookie(Header header) {
        this.header = header;
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
        var v = header.getFirstCookie(name);
        if (v != null)
            return new TypedValue(v);
        v = header.getFirstCookie(camelToKebab(name));

        // return v even if it is null
        return new TypedValue(v);
    }

    @Override
    public Object getValue() {
        return header;
    }
}
