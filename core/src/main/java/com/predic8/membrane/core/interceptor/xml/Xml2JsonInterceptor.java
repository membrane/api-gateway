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

package com.predic8.membrane.core.interceptor.xml;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.json.XML;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * @description If enabled converts body content from xml to json.
 * @explanation Can be used for both request and response. Xml file assumed to be in UTF-8. If input is invalid it returns
 * empty json object.
 * @topic 4. Interceptors/Features
 */
@MCElement(name="xml2Json")
public class Xml2JsonInterceptor extends AbstractInterceptor {

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) {
        if(!msg.isXML()){
            return CONTINUE;
        }
        msg.getHeader().setContentType(MimeType.APPLICATION_JSON_UTF8);
        msg.setBodyContent(xml2json(msg.getBodyAsStream()));
        return CONTINUE;
    }


    private byte[] xml2json(InputStream body){
        return XML.toJSONObject(new InputStreamReader(body)).toString().getBytes(UTF_8);
    }
}
