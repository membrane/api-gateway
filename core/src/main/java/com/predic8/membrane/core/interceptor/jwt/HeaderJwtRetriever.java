/*
 * Copyright 2021 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.jwt;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import org.springframework.beans.factory.annotation.Required;

@MCElement(name = "headerJwtRetriever")
public class HeaderJwtRetriever implements JwtRetriever{

    String header;
    String removeFromValue;


    public HeaderJwtRetriever() {
    }

    public HeaderJwtRetriever(String header, String removeFromValue) {
        this.header = header;
        this.removeFromValue = removeFromValue;
    }

    @Override
    public String get(Exchange exc) {
        String[] replace = removeFromValue.split(" ");
        String header = exc.getRequest().getHeader().getFirstValue(this.header);
        for (String replaceMe : replace) {
            header = header.replace(replaceMe.trim(),"");
        }
        return header.trim();
    }

    public String getHeader() {
        return header;
    }

    @Required
    @MCAttribute
    public HeaderJwtRetriever setHeader(String header) {
        this.header = header;
        return this;
    }

    public String getRemoveFromValue() {
        return removeFromValue;
    }

    @MCAttribute
    public HeaderJwtRetriever setRemoveFromValue(String removeFromValue) {
        this.removeFromValue = removeFromValue;
        return this;
    }
}
