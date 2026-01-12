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

package com.predic8.membrane.core.util;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;

public class WebServerUtil {

    public static String getContentType(String uri) {
        if (uri.endsWith(".css"))
            return "text/css";
        if (uri.endsWith(".js"))
            return "application/javascript";
        if (uri.endsWith(".wsdl"))
            return "text/xml";
        if (uri.endsWith(".xml"))
            return "text/xml";
        if (uri.endsWith(".xsd"))
            return "text/xml";
        if (uri.endsWith(".html"))
            return "text/html";
        if (uri.endsWith(".jpg"))
            return "image/jpeg";
        if (uri.endsWith(".png"))
            return "image/png";
        if (uri.endsWith(".json"))
            return APPLICATION_JSON;
        if (uri.endsWith(".svg"))
            return "image/svg+xml";
        return null;
    }

}
