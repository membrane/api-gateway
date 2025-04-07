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

package com.predic8.membrane.core.interceptor.adminApi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.transport.ws.WebSocketConnectionCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;

import static com.google.common.collect.ImmutableMap.of;

public class WebSocketExchangeWatcher implements IExchangesStoreListener {

    private final static Logger LOG = LoggerFactory.getLogger(WebSocketExchangeWatcher.class.getName());

    private static final ObjectMapper om = new ObjectMapper();

    private WebSocketConnectionCollection connections;

    public void init(WebSocketConnectionCollection connections) {
        this.connections = connections;
    }

    @Override
    public void addExchange(Proxy proxy, AbstractExchange exc) {
       if (connections != null) {
            try {
                StringWriter writer = new StringWriter();

                JsonGenerator gen = om.getFactory().createGenerator(writer);
                gen.writeStartArray();

                //writeExchange(exc, gen);

                gen.writeEndArray();
                gen.close();

                connections.broadcast(of(
                        "subject", "liveUpdate",
                        "data", writer.toString()));
            } catch (IOException e) {
                LOG.error("", e);
            }
        }
    }

        @Override
        public void removeExchange (AbstractExchange exchange){
        }

        @Override
        public void removeExchanges (Proxy parent, AbstractExchange[]exchanges){
        }

        @Override
        public void removeExchanges (AbstractExchange[]exchanges){
        }

        @Override
        public void setExchangeFinished (AbstractExchange exchange){
        }

        @Override
        public void setExchangeStopped (AbstractExchange exchange){
        }

        @Override
        public void refresh () {
        }
    }
