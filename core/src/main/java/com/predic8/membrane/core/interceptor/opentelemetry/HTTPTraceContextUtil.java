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

package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Arrays;

@SuppressWarnings("NullableProblems")
public class HTTPTraceContextUtil {

    public static TextMapGetter<Exchange> getContextFromRequestHeader() {
        return new TextMapGetter<>() {
            @Override
            public String get(Exchange carrier, String key) {
                return carrier.getRequest().getHeader().getFirstValue(key);
            }

            @Override
            public Iterable<String> keys(Exchange carrier) {
                return Arrays.stream(carrier.getRequest().getHeader().getAllHeaderFields()).map(HeaderField::toString).toList();
            }
        };
    }

    @SuppressWarnings("DataFlowIssue")
    public static TextMapSetter<Exchange> setContextInHeader() {
        return (carrier, key, value) -> carrier.getRequest().getHeader().add(key, value);
    }
}

