/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.http.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.http.MimeType.TEXT_HTML_UTF8;
import static org.apache.commons.text.StringEscapeUtils.escapeXml11;

public class FormPostGenerator {

    private final String url;
    private final Map<String, String> parameters = new HashMap<>();

    public FormPostGenerator(String url) {
        this.url = url;
    }

    public Response build() {
        return Response.ok().contentType(TEXT_HTML_UTF8).body(
                "<html><head><title>Submit This Form</title></head>"
                        + "<body onload=\"javascript:document.forms[0].submit()\"><form method=\"post\" action=\""
                        + escapeXml11(url) + "\">" +
                        parameters.entrySet().stream().map(e ->
                                "<input type=\"hidden\" name=\""
                                        + escapeXml11(e.getKey()) + "\" value=\""
                                        + escapeXml11(e.getValue()) + "\"/>").collect(Collectors.joining())
                        + "</form></body></html>").build();
    }

    public FormPostGenerator withParameter(String key, String value) {
        parameters.put(key, value);
        return this;
    }
}
