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

package com.predic8.membrane.core.util.text;

import com.predic8.membrane.core.util.xml.*;
import org.w3c.dom.*;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.*;

public class ToURLSerializer {

    private ToURLSerializer() {
    }

    public static String toURL(Object o) {
        if (o instanceof String s)
            return encode(SerializationUtil.identity(s), UTF_8);
        if (o instanceof NodeList nl) {
            return encode(XMLTextUtil.nodeListToString(nl,","), UTF_8);
        }
        return encode(o.toString(), UTF_8);
    }
}
