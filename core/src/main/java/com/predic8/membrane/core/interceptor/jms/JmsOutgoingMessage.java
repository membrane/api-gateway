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

import java.util.Map;

/**
 * Header fields and properties of a message the jmsProducer sends that can change from message to message. The
 * interceptor evaluates them from its attributes, so the extension only sets them on the JMS message.
 *
 * @param correlationId <code>JMSCorrelationID</code>, or <code>null</code>
 * @param type          <code>JMSType</code>, or <code>null</code>
 * @param replyToQueue  Name of the queue for <code>JMSReplyTo</code>, or <code>null</code>
 * @param replyToTopic  Name of the topic for <code>JMSReplyTo</code>, or <code>null</code>. At most one of
 *                      replyToQueue and replyToTopic is set.
 * @param properties    JMS properties. The values are of a type JMS allows: <code>String</code>,
 *                      <code>Boolean</code>, <code>Byte</code>, <code>Short</code>, <code>Integer</code>,
 *                      <code>Long</code>, <code>Float</code> or <code>Double</code>. Read-only.
 */
public record JmsOutgoingMessage(String correlationId, String type, String replyToQueue, String replyToTopic,
                                 Map<String, Object> properties) {

    public JmsOutgoingMessage {
        properties = Map.copyOf(properties);
    }
}
