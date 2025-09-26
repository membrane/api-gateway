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
package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import org.slf4j.*;

import static java.nio.charset.StandardCharsets.*;

@MCElement(name = "static", mixed = true)
public class StaticInterceptor extends AbstractTemplateInterceptor {

    protected static final Logger log = LoggerFactory.getLogger(StaticInterceptor.class);

    volatile byte[] cache;

    public StaticInterceptor() {
        name = "static";
    }

    @Override
    public void init() {
        super.init();
        cache = null; // Make (re)init() possible
    }

    @Override
    protected byte[] getContent(Exchange exchange, Flow flow) {
        return src.getBytes(UTF_8);
    }

    @Override
    protected byte[] prettify(byte[] bytes) {
        if (cache != null)
            return cache;
        cache = super.prettify(bytes);
        return cache;
    }
}