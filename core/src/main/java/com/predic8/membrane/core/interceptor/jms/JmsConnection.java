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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.annot.Required;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @description <p>Connection to a JMS broker. Works with every broker that has a Jakarta Messaging (JMS 3)
 * client, e.g. ActiveMQ Artemis, ActiveMQ Classic 6, IBM MQ or AMQP brokers through Qpid JMS. The client
 * library of the broker has to be put into the <code>lib</code> folder of Membrane.</p>
 *
 * <p>Define the connection inline or once under <code>components</code> and reference it with
 * <code>$ref</code> from several places.</p>
 * @yaml
 * <pre><code>
 * components:
 *   orderBroker:
 *     jmsConnection:
 *       connectionFactory: org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
 *       username: membrane
 *       password: secret
 *       # Properties of the connection factory
 *       brokerURL: tcp://mq.example.com:61616
 *       callTimeout: 30000
 *       reconnectAttempts: -1
 *       retryInterval: 2000
 * ---
 * api:
 *   port: 2000
 *   flow:
 *     - jmsProducer:
 *         $ref: '#/components/orderBroker'
 *         queue: orders
 * </code></pre>
 */
@MCElement(name = "jmsConnection")
public class JmsConnection {

    private String connectionFactory;
    private String username;
    private String password;
    private final Map<String, String> factoryProperties = new LinkedHashMap<>();

    /**
     * @description Class of the broker's <code>jakarta.jms.ConnectionFactory</code>.
     * @example org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory | org.apache.activemq.ActiveMQConnectionFactory | com.ibm.mq.jakarta.jms.MQConnectionFactory | org.apache.qpid.jms.JmsConnectionFactory
     */
    @MCAttribute
    @Required
    public void setConnectionFactory(String connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * @description User name for the connection to the broker.
     * @example membrane
     */
    @MCAttribute
    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    /**
     * @description Password for the connection to the broker.
     * @example secret
     */
    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    /**
     * @description Further attributes are set as properties on the connection factory by calling the setter with
     * the same name, e.g. <code>brokerURL</code> calls <code>setBrokerURL</code>. Which properties exist depends on
     * the broker. Membrane does not start if the factory has no such setter. Common properties:
     * <ul>
     * <li>ActiveMQ Artemis: <code>brokerURL</code>, <code>callTimeout</code>, <code>reconnectAttempts</code>,
     * <code>retryInterval</code></li>
     * <li>ActiveMQ Classic: <code>brokerURL</code>, <code>sendTimeout</code>, <code>useAsyncSend</code></li>
     * <li>IBM MQ: <code>hostName</code>, <code>port</code>, <code>queueManager</code>, <code>channel</code>,
     * <code>transportType</code> (1 = client)</li>
     * <li>Qpid JMS (AMQP): <code>remoteURI</code></li>
     * </ul>
     * @example brokerURL: tcp://mq.example.com:61616
     */
    @MCOtherAttributes
    public void setFactoryProperties(Map<String, String> factoryProperties) {
        this.factoryProperties.putAll(factoryProperties);
    }

    public Map<String, String> getFactoryProperties() {
        return factoryProperties;
    }
}
