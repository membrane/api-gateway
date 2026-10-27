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

package com.predic8.membrane.core.interceptor.jms;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;

import java.io.IOException;
import java.util.Map;

/**
 * Sends messages to a JMS broker. The Membrane JMS Extension provides the implementation;
 * without it the interceptor fails on startup with a ConfigurationException.
 * <p>
 * Settings that are fixed for one jmsProducer are passed to {@link #init(Map)}. Values that can change from
 * message to message are read from the exchange, so the signature of {@link #send(Exchange, Flow)} stays stable.
 */
public interface JmsProducerExtension {

    /**
     * Called once before any call to {@link #send(Exchange, Flow)}.
     *
     * @param properties Connection and producer settings. Unset optional settings are missing from the map.
     *                   <ul>
     *                   <li><code>connection.factory</code>: class of the <code>jakarta.jms.ConnectionFactory</code></li>
     *                   <li><code>factory.NAME</code>: properties to set on the connection factory, e.g.
     *                   <code>factory.brokerURL</code> means <code>setBrokerURL(...)</code></li>
     *                   <li><code>username</code>, <code>password</code>: credentials for creating the connection</li>
     *                   <li>either <code>queue</code> or <code>topic</code>: name of the destination</li>
     *                   <li><code>delivery.mode</code>: PERSISTENT or NON_PERSISTENT</li>
     *                   <li><code>send.timeout</code>: milliseconds a send may take before it fails, 0 for no
     *                   limit</li>
     *                   </ul>
     */
    void init(Map<String, String> properties);

    /**
     * Called concurrently from multiple request threads on the same instance. JMS
     * <code>Session</code>s and <code>MessageProducer</code>s are not thread-safe, so the implementation
     * must not share them between concurrent calls without synchronization, e.g. it can use one
     * session and producer per thread or a pool.
     *
     * @param exchange Exchange whose request or response body is the message body
     * @param flow     REQUEST to send the body of the request, RESPONSE to send the body of the response
     */
    void send(Exchange exchange, Flow flow) throws IOException;

    /**
     * Releases the connections. Called when the router stops.
     */
    void stop();
}
