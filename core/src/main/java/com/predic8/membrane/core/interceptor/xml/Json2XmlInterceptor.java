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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.json.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;


/**
 * @description If enabled converts body content from json to xml.
 * @explanation Can be used for both request and response. Resulting xml will be utf-8. It uses org.json
 *  XML.toString() to do the conversion
 * @topic 4. Interceptors/Features
 */
@MCElement(name="json2Xml")
public class Json2XmlInterceptor extends AbstractInterceptor {

    @Override
    public String getDisplayName() {
        return "JSON2XML";
    }

    @Override
    public String getShortDescription() {
        return "Converts JSON message bodies to XML.";
    }

    private static final String ROOT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg){
        if(!msg.isJSON())
            return CONTINUE;
        msg.getHeader().setContentType(TEXT_XML);
        msg.setBodyContent(json2Xml(msg.getBodyAsStream()));

        return CONTINUE;
    }

    private byte[] json2Xml(InputStream body) {
        return (ROOT + XML.toString(convertToJsonObject(body))).getBytes(UTF_8);

    }

    private JSONObject convertToJsonObject(InputStream body){
        return new JSONObject(new JSONTokener(new InputStreamReader(body, UTF_8)));
    }

}
