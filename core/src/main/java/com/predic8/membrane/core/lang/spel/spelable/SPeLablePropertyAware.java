/* Copyright 2023 predic8 GmbH, www.predic8.com

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

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

public interface SPeLablePropertyAware {
    boolean canRead(EvaluationContext context, Object target, String name);
    TypedValue read(EvaluationContext context, Object target, String name);

    default String camelToKebab(String string) {
        return string.replaceAll("([a-z0-9]|(?=[A-Z]))([A-Z])", "$1-$2").toLowerCase();
    }
}
