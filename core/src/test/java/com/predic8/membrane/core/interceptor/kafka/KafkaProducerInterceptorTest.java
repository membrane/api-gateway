/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.kafka;

import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaProducerInterceptorTest {

    @Test
    void continuesWithDummyProducer() throws Exception {
        final var i = new KafkaProducerInterceptor();
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        i.setKey("headers['X-Id']");
        i.init(new DummyTestRouter());

        final var exc = post("/").header("X-Id", "42").body("hello").buildExchange();
        assertEquals(CONTINUE, i.handleRequest(exc));
    }

    @Test
    void destroyStopsProducer() {
        final var i = new KafkaProducerInterceptor();
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        i.init(new DummyTestRouter());
        i.destroy();
    }

    @Test
    void worksWithoutKey() throws Exception {
        final var i = new KafkaProducerInterceptor();
        i.setBootstrapServers("localhost:9092");
        i.setTopic("orders");
        i.init(new DummyTestRouter());

        assertEquals(CONTINUE, i.handleRequest(post("/").body("hello").buildExchange()));
    }
}
