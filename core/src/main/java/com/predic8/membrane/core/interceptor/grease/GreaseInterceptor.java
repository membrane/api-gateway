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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.grease.strategies.GreaseStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.EnumSet.of;

@MCElement(name = "grease")
public class GreaseInterceptor extends AbstractInterceptor {

    private final List<GreaseStrategy> strategies = new ArrayList<>();
    private final Random random = new Random();

    private static final Logger LOG = LoggerFactory.getLogger(GreaseInterceptor.class);
    static final String X_GREASE = "X-Grease";

    private double rate;

    public GreaseInterceptor() {
        name = "Grease";
        setFlow(of(REQUEST, RESPONSE));
    }

    private Message handleInternal(Message msg, String contentType) {
        if (random.nextDouble() >= rate) {
            return msg;
        }
        msg.setBody(strategies.stream()
                .filter(s -> s.getApplicableContentType().equals(contentType))
                .findFirst()
                .map(strategy -> {
                    msg.getHeader().add(X_GREASE, strategy.getGreaseChanges());
                    return strategy.apply((Body) msg.getBody());
                })
                .orElseGet(() -> ((Body) msg.getBody())));
        return msg;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setRequest((Request)
                handleInternal(
                       exc.getRequest(), exc.getRequest().getHeader().getContentType()
                )
        );
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        exc.setResponse((Response)
                handleInternal(
                        exc.getResponse(), exc.getResponse().getHeader().getContentType()
                )
        );
        return CONTINUE;
    }

    @MCAttribute
    public void setRate(double rate) {
        this.rate = Math.max(0, Math.min(1, rate));
    }

    public double getRate() {
        return rate;
    }

    @MCChildElement
    public void setStrategies(List<GreaseStrategy> strategies) {
        this.strategies.addAll(strategies);
    }

    public List<GreaseStrategy> getStrategies() {
        return strategies;
    }

    @Override
    public String getShortDescription() {
        return "Greases data like XML or JSON to stress test data inputs.";
    }
}
