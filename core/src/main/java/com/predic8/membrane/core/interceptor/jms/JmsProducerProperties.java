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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @description <p>JMS properties of the messages a jmsProducer sends. Each entry is the name of a property and its
 * value. A value can contain expressions. A value that consists of a single expression keeps the type of its
 * result, e.g. a number, so that a JMS message selector can compare it as a number. Any other value is sent as
 * text.</p>
 * @yaml
 * <pre><code>
 * properties:
 *   orderId: ${json.id}
 *   tenant: acme
 * </code></pre>
 */
@MCElement(name = "properties", component = false, id = "jmsProducer-properties")
public class JmsProducerProperties {

    private final Map<String, String> properties = new LinkedHashMap<>();

    /**
     * @description Name and value of a JMS property. The name must be a Java identifier and must not start with
     * <code>JMS</code>.
     * @example orderId: ${json.id}
     */
    @MCOtherAttributes
    public void setProperties(Map<String, String> properties) {
        this.properties.putAll(properties);
    }

    public Map<String, String> getProperties() {
        return properties;
    }
}
