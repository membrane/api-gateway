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
