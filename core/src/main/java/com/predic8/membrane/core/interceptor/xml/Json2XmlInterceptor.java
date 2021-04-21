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
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ContentTypeDetector;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * @description If enabled converts body content from json to xml.
 * @explanation Can be used for both request and response. Resulting xml will be utf-8. It uses org.json
 *  XML.toString() to do the conversion
 * @topic 4. Interceptors/Features
 */
@MCElement(name="json2Xml")
public class Json2XmlInterceptor extends AbstractInterceptor {

    private final String root = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static Logger log = LoggerFactory.getLogger(Json2XmlInterceptor.class.getName());

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) throws IOException {
        if(!msg.isJSON())
            return CONTINUE;
        msg.getHeader().setContentType(MimeType.TEXT_XML);
        msg.setBodyContent(json2Xml(msg.getBodyAsStream()));

        return CONTINUE;
    }


    private byte[] json2Xml(InputStream body) throws IOException {
        return (root + XML.toString(convertToJsonObject(body))).getBytes(UTF_8);

    }

    private JSONObject convertToJsonObject(InputStream body) {
        return new JSONObject(new JSONTokener(body));
    }

}
