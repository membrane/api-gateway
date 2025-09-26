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

package com.predic8.membrane.core.interceptor.grease;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.grease.strategies.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.lang.Double.parseDouble;
import static java.util.EnumSet.*;

@MCElement(name = "greaser")
public class GreaseInterceptor extends AbstractInterceptor {

    private final List<Greaser> strategies = new ArrayList<>();
    private final Random random = new Random();
    public static final String X_GREASE = "X-Grease";

    private double rate = 1;

    public GreaseInterceptor() {
        name = "grease";
        setAppliedFlow(of(REQUEST, RESPONSE));
    }

    private Message handleInternal(Message msg) {
        if (!shallGrease()) {
            return msg;
        }
        return strategies.stream().reduce(msg,
                (m, strategy) -> strategy.apply(m),
                (ignored, ret) -> ret
        );
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        exc.setRequest((Request) handleInternal(exc.getRequest()));
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        exc.setResponse((Response) handleInternal(exc.getResponse()));
        return CONTINUE;
    }

    @MCAttribute
    public void setRate(String rate) {
        this.rate = Math.max(0, Math.min(1, parseDouble(rate)));
    }

    public double getRate() {
        return rate;
    }

    @MCChildElement
    public void setStrategies(List<Greaser> strategies) {
        this.strategies.addAll(strategies);
    }

    @SuppressWarnings("unused")
    public List<Greaser> getStrategies() {
        return strategies;
    }

    @Override
    public String getShortDescription() {
        return "Greases data like XML or JSON to stress test data inputs.";
    }

    private boolean shallGrease() {
        return rate > random.nextDouble();
    }
}
