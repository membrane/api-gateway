/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.flow.choice.Case;
import com.predic8.membrane.core.interceptor.flow.choice.ChooseInterceptor;
import com.predic8.membrane.core.interceptor.flow.choice.Otherwise;
import com.predic8.membrane.core.interceptor.flow.invocation.testinterceptors.*;
import com.predic8.membrane.core.interceptor.groovy.*;

import java.util.List;

import static java.util.Arrays.*;

public class FlowTestInterceptors {

    public static final Interceptor A = new FlowTestInterceptor("a");
    public static final Interceptor B = new FlowTestInterceptor("b");
    public static final Interceptor C = new FlowTestInterceptor("c");
    public static final Interceptor D = new FlowTestInterceptor("d");
    public static final Interceptor E = new FlowTestInterceptor("e");
    public static final Interceptor I1 = new FlowTestInterceptor("i1");
    public static final Interceptor I2 = new FlowTestInterceptor("i2");
    public static final Interceptor I3 = new FlowTestInterceptor("i3");
    public static final Interceptor I4 = new FlowTestInterceptor("i4");

    public static final Interceptor RETURN = new ReturnInterceptor();
    public static final Interceptor ABORT = new AbortFlowTestInterceptor();
    public static final Interceptor EXCEPTION = new ExceptionTestInterceptor();
    public static final Interceptor ECHO = new EchoInterceptor();

    public static IfInterceptor IF(String test, Interceptor... nestedInterceptors) {
        return new IfInterceptor() {{
            setTest(test);
            setInterceptors(asList(nestedInterceptors));
        }};
    }

    public static ChooseInterceptor CHOICE(Otherwise otherwise, Case... cases) {
        return new ChooseInterceptor() {{
            setOtherwise(otherwise);
            setCases(asList(cases));
        }};
    }

    public static Case CASE(String test, Interceptor... nestedInterceptors) {
        return new Case() {{
            setTest(test);
            setInterceptors(List.of(nestedInterceptors));
        }};
    }

    public static Otherwise OTHERWISE(Interceptor... nestedInterceptors) {
        return new Otherwise() {{
            setInterceptors(List.of(nestedInterceptors));
        }};
    }

    public static Interceptor GROOVY(String aSrc) {
        return new GroovyInterceptor() {{
            router = new HttpRouter();
            setSrc(aSrc);
            init();
        }};
    }

    public static RequestInterceptor REQUEST(Interceptor... interceptors) {
        RequestInterceptor ai = new RequestInterceptor();
        ai.setInterceptors(asList(interceptors));
        return ai;
    }

    public static ResponseInterceptor RESPONSE(Interceptor... interceptors) {
        ResponseInterceptor ai = new ResponseInterceptor();
        ai.setInterceptors(asList(interceptors));
        return ai;
    }

    public static AbortInterceptor ABORT(Interceptor... interceptors) {
        AbortInterceptor ai = new AbortInterceptor();
        ai.setInterceptors(asList(interceptors));
        return ai;
    }
}