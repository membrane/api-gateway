/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.springframework.beans.factory.annotation.Required;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @description Based on <a href="https://tools.ietf.org/html/draft-ietf-appsawg-json-pointer-03">JSON pointer.</a>
 * The interceptor takes values from JSON request body and puts them into Exchange object as properties. If the pointer is not found,
 * an exception will be thrown (resulting in {@link Outcome#ABORT}).
 * @topic 4. Interceptors/Features
 */
@MCElement(name="jsonPointerExtractor")
public class JsonPointerExtractorInterceptor extends AbstractInterceptor{



    private List<Property> properties = new ArrayList<>();
    private ObjectMapper mapper = new ObjectMapper();

    public JsonPointerExtractorInterceptor() {
        name = "JsonPointer";
    }
    /**
     * @description Defines a json pointer and name for exchange property.
     */
    @Required
    @MCChildElement(order = 100)
    public void setMappings(List<Property> properties) {
        this.properties = properties;
    }

    public List<Property> getMappings() {
        return properties;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc, exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc, exc.getResponse());
    }

    private Outcome handleInternal(Exchange exc, Message msg) throws IOException {
        if(!msg.isJSON()){
            return Outcome.CONTINUE;
        }
        setProperties(exc, msg.getBodyAsStream());
        return Outcome.CONTINUE;
    }

    private JsonNode parseJson(InputStream body) throws IOException {
        return mapper.readTree(body);
    }

    private void setProperties(Exchange exc, InputStream body) throws IOException {
        JsonNode parsed = parseJson(body);
        for (Property m : properties) {
            exc.setProperty(m.getName(), convertPointedValue(parsed, m));
        }
    }

    private Object convertPointedValue(JsonNode root, Property m){
        JsonNode pointed = root.at(m.getJsonPointer());
        if(pointed.isArray()){
            return StreamSupport.stream(pointed.spliterator(), false)
                    .filter( n -> !n.isContainerNode())
                    .map(JsonNode::asText)
                    .collect(Collectors.toList());
        }
        else{
            return pointed.asText();
        }
    }




    @MCElement(name="property", topLevel=false, id="jsonpointer-map")
    public static class Property {
        String jsonPointer;
        String name;

        public Property() {
        }


        public Property(String jsonPointer, String name) throws XPathExpressionException {
            this.name = name;
            this.jsonPointer = jsonPointer;
        }

        public String getJsonPointer() {
            return jsonPointer;
        }

        /**
         * @description Json pointer expression
         * @example /employee/salary
         */
        @Required
        @MCAttribute
        public void setJsonPointer(String jsonPointer) throws XPathExpressionException {
            this.jsonPointer = jsonPointer;
        }

        public String getName() {
            return name;
        }

        /**
         * @description Name of property to put in exchange properties
         * @example salary
         */
        @Required
        @MCAttribute
        public void setName(String name) {
            this.name = name;
        }
    }
}
