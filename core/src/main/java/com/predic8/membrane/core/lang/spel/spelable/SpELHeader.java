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

import com.predic8.membrane.core.http.*;
import org.jetbrains.annotations.*;
import org.springframework.expression.*;

import java.util.*;

import static com.predic8.membrane.core.util.TextUtil.*;

public class SpELHeader implements SpELLablePropertyAware {

    private final Header header;

    public SpELHeader(Header header) {
        this.header = header;
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
        List<HeaderField> values = header.getValues(name);
        if (values == null || values.isEmpty()) {
            values = header.getValues(camelToKebab(name));
        }
        if (values == null || values.isEmpty()) {
            return new TypedValue(null);
        }
        return new TypedValue(String.join(", ", convertToStringList(values)));
    }

    private static @NotNull List<String> convertToStringList(List<HeaderField> values) {
        return values.stream().map(HeaderField::getValue).toList();
    }


    @Override
    public Object getValue() {
        return header;
    }
}
