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
import com.predic8.membrane.core.router.Router;

import java.util.Map;

/**
 * Receives messages from a JMS broker. The Membrane JMS Extension provides the implementation;
 * without it the jmsConsumer fails on startup with a ConfigurationException.
 * <p>
 * A message is acknowledged after the handler returned {@link Result#ACK}. After {@link Result#ROLLBACK} the
 * implementation does not acknowledge it, e.g. it rolls back the transacted session or recovers the session, so the
 * broker delivers the message again according to its redelivery policy and finally moves it to its dead-letter queue.
 */
public interface JmsConsumerExtension {

    /**
     * Version of this interface. It is increased whenever the interface or the meaning of the properties passed to
     * {@link #init(Router, Map)} changes incompatibly. An extension reports the version it was built against in
     * {@link #apiVersion()}.
     */
    int API_VERSION = 1;

    /**
     * Name of the exchange property holding the {@link JmsMessageInfo} of the received message, so that a flow reads
     * e.g. <code>${property.jms.messageId}</code>.
     */
    String JMS_PROPERTY = "jms";

    /**
     * What the implementation does with a message after the handler returned.
     */
    enum Result {
        /**
         * Delivered. Acknowledge the message.
         */
        ACK,
        /**
         * Not delivered. Do not acknowledge the message; roll back or recover the session so that the broker
         * delivers it again.
         */
        ROLLBACK
    }

    /**
     * Called for every received message. The consumer implementation creates the exchange.
     */
    interface MessageHandler {
        /**
         * The request is sent to the target of the jmsConsumer like a request that came in over HTTP, so it
         * must look like one: method POST and URI <code>/</code>, so that the path of the target URL is used
         * unchanged.
         *
         * @param exchange Exchange created from the message: the message body is the request body with its
         *                 Content-Type as the only header. The header fields and properties of the message are in the
         *                 {@link JmsMessageInfo} in the exchange property {@link #JMS_PROPERTY}. They are not passed
         *                 as request headers, so the flow decides what reaches the target.
         * @return what to do with the message. Does not throw; an unexpected error is returned as
         * {@link Result#ROLLBACK}.
         */
        Result handle(Exchange exchange);
    }

    /**
     * Called before {@link #init(Router, Map)}. The jmsConsumer refuses to use the extension unless the returned
     * value equals {@link #API_VERSION}.
     *
     * @return The value of {@link #API_VERSION} the extension was built against.
     */
    int apiVersion();

    /**
     * @param router     Router the consumer belongs to
     * @param properties Connection and consumer settings. Unset optional settings are missing from the map.
     *                   <ul>
     *                   <li><code>connection.factory</code>: class of the <code>jakarta.jms.ConnectionFactory</code></li>
     *                   <li><code>factory.NAME</code>: properties to set on the connection factory, e.g.
     *                   <code>factory.brokerURL</code> means <code>setBrokerURL(...)</code></li>
     *                   <li><code>username</code>, <code>password</code>: credentials for creating the connection</li>
     *                   <li>either <code>queue</code> or <code>topic</code>: name of the destination</li>
     *                   <li><code>subscription</code>: name of the durable shared subscription, only with a topic</li>
     *                   <li><code>concurrency</code>: number of sessions receiving messages in parallel</li>
     *                   </ul>
     */
    void init(Router router, Map<String, String> properties);

    /**
     * Starts consuming. Every message of the destination is passed to the handler. The handler is called
     * concurrently from the threads of the sessions; each message is handled by one thread only.
     * <p>
     * Not to be forgotten by the implementation:
     * <ul>
     * <li>This method is called from the init of the jmsConsumer, before the interceptors of its flow and the
     * other APIs are initialized. Do not hand messages to the handler before the router is running, see
     * {@link Router#isRunning()}.</li>
     * <li>The method must not let an exception escape that could harm the caller, for example from an unreachable
     * broker or wrong credentials. Catch, log and retry or give up.</li>
     * <li>{@link #stop()} can run while this method is still starting. Make sure no consumer is left running
     * after stop.</li>
     * </ul>
     *
     * @param handler Receives the messages
     */
    void start(MessageHandler handler);

    /**
     * Stops consuming and releases the connections. Called when the router stops, and when the jmsConsumer is
     * initialized again and replaces this instance. Can be called more than once and before {@link #start} finished.
     */
    void stop();
}
