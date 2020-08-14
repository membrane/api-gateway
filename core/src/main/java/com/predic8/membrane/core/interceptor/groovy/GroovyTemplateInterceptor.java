/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.groovy;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name = "groovyTemplate", mixed = true)
public class GroovyTemplateInterceptor extends AbstractInterceptor {

    String src = "";

    GroovyInterceptor groovyInterceptor;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        groovyInterceptor = new GroovyInterceptor();
        groovyInterceptor.setSrc(createGroovyScript());
        groovyInterceptor.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        groovyInterceptor.handleRequest(exc);
        String html = (String) exc.getProperty("GROOVY_TEMPLATE");
        exc.setResponse(Response.ok(html).contentType(MimeType.TEXT_HTML_UTF8).build());
        return Outcome.RETURN;
    }

    private String createGroovyScript() {
        StringBuilder sb = new StringBuilder();
        sb
                .append("import groovy.text.markup.*").append("\n")
                .append("def markupEngine = new MarkupTemplateEngine()").append("\n")
                .append("def writer = new StringWriter()").append("\n")
                .append("def markup = '''").append(src).append("'''").append("\n")
                .append("def output = markupEngine.createTemplate(markup).make(['spring':spring, 'exc':exc,'flow':flow]).writeTo(writer)").append("\n")
                .append("exc.setProperty('GROOVY_TEMPLATE',output.toString())").append("\n")
                .append("CONTINUE");
        return sb.toString();
    }

    public String getSrc() {
        return src;
    }

    @MCTextContent
    public void setSrc(String src) {
        this.src = src;
    }
}
