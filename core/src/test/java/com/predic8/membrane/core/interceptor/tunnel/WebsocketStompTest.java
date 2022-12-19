/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.tunnel;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.Test;

import javax.jms.*;
import java.util.Scanner;

public class WebsocketStompTest {
    @Test
    public void testWebsocketStomp() throws Exception{
        // Preparation: tart the Websocket-Stomp example from an external source

        // Starts embedded ActiveMQ and places "Hello world!" into the "foo" queue
        // Verification: If you open "localhost:4333" with running example the message appears -> Server part works

        // Missing: Client that receives the message and validates that it is "Hello world!"


        BrokerService broker = new BrokerService();
        broker.addConnector("ws://localhost:61614");
        broker.addConnector("tcp://localhost:61615");
        broker.start();

        MessageProducer();

        boolean isRunning = true;
        Scanner inputScanner = new Scanner(System.in);
        while(isRunning) {
            String input = inputScanner.nextLine();
            if(input.toLowerCase().equals("exit"))
                isRunning = false;
            Thread.sleep(100);
        }
    }

    private void MessageProducer() throws JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61615");

        Connection connection = connectionFactory.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination destination = session.createQueue("foo");

        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        String text = "Hello world!";
        TextMessage message = session.createTextMessage(text);

        producer.send(message);

        session.close();
        connection.close();
    }

}
