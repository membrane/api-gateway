/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import jakarta.mail.internet.*;
import org.springframework.http.*;

import java.util.*;

import static java.util.Collections.reverse;
import static java.util.Comparator.*;
import static org.apache.commons.lang3.StringUtils.*;
import static org.springframework.http.MediaType.*;

/**
 * Use javax.mail.internet.ContentType to parse a mime type or the methods using
 * ContenType below.
 */
public class MimeType {

    public static final String APPLICATION = "application";

    public static final String APPLICATION_SOAP = "application/soap+xml";
    public static final String APPLICATION_XML = "application/xml";
    public static final String TEXT_XML = "text/xml";
    public static final String TEXT_HTML = "text/html";
    public static final String TEXT_XML_UTF8 = TEXT_XML + ";charset=UTF-8";

    public static final String TEXT_HTML_UTF8 = "text/html;charset=UTF-8";
    public static final String TEXT_PLAIN = "text/plain";
    public static final String TEXT_PLAIN_UTF8 = "text/plain;charset=UTF-8";


    public static final String APPLICATION_JSON = "application/json";

    public static final String APPLICATION_JSON_UTF8 = "application/json;charset=utf-8";

    public static final String APPLICATION_JOSE_JSON = "application/jose+json";

    public static final String APPLICATION_X_YAML = "application/x-yaml";

    /**
     * See <a href="https://www.rfc-editor.org/rfc/rfc7807">Problem Details for HTTP APIs</a>
     */
    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    public static final String APPLICATION_APPLY_PATCH_YAML = "application/apply-patch+yaml";

    public static final String APPLICATION_GRAPHQL = "application/graphql";
    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";

    public static final String APPLICATION_X_JAVASCRIPT = "application/x-javascript";

    public static final String TEXT_JAVASCRIPT = "text/javascript";
    public static final String TEXT_X_JAVASCRIPT = "text/x-javascript";
    public static final String TEXT_X_JSON = "text/x-json";

    public static final ContentType APPLICATION_JSON_CONTENT_TYPE = new ContentType(APPLICATION, "json", null);

    public static final ContentType APPLICATION_X_WWW_FORM_URLENCODED_CONTENT_TYPE = new ContentType(APPLICATION, APPLICATION_X_WWW_FORM_URLENCODED, null);

    public static boolean isXML(String mediaType) {
        try {
            return isXML(new jakarta.activation.MimeType(mediaType));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isXML(jakarta.activation.MimeType mediaType) {
        if (mediaType == null) {
            return false;
        }
        if (mediaType.getSubType().contains("xml")) {
            return true;
        }
        if (mediaType.getSubType().contains("xhtml")) {
            return true;
        }
        if (mediaType.getSubType().contains("svg")) {
            return true;
        }
        return false;
    }

    public static boolean isText(String mediaType) {
        try {
            return isText(new jakarta.activation.MimeType(mediaType));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isText(jakarta.activation.MimeType mediaType) {
        if (mediaType == null) {
            return false;
        }
        if (mediaType.getPrimaryType().equals("text")) {
            return true;
        }
        String st = mediaType.getSubType();
        return st.contains("text");
    }

    public static boolean isJson(String mediaType) {
        try {
            return isJson(new jakarta.activation.MimeType(mediaType));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isJson(jakarta.activation.MimeType type) {
        if (type == null)
            return false;
        return containsIgnoreCase(type.getSubType(), "json");
    }

    public static boolean isImage(String mediaType) {
        try {
            return isImage(new jakarta.activation.MimeType(mediaType));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isImage(jakarta.activation.MimeType mediaType) {
        if (mediaType == null) {
            return false;
        }
        if (mediaType.getPrimaryType().equals("image")) {
            return true;
        }
        return false;
    }

    public static boolean isHtml(String mediaType) {
        try {
            return isHtml(new jakarta.activation.MimeType(mediaType));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isHtml(jakarta.activation.MimeType mediaType) {
        if (mediaType == null) {
            return false;
        }
        if (mediaType.getSubType().contains("html")) {
            return true;
        }
        return false;
    }

    public static boolean isBinary(String mediaType) {
        try {
            return isBinary(new jakarta.activation.MimeType(mediaType));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isBinary(jakarta.activation.MimeType mediaType) {
        if (mediaType == null) {
            return false;
        }
        switch (mediaType.getPrimaryType()) {
            case "image", "video", "audio":
                return true;
            default:
        }
        String st = mediaType.getSubType();
        return st.contains("octet-stream") || st.contains("zip");
    }

    public static boolean isWWWFormUrlEncoded(String mediaType) {
        return isOfMediaType(APPLICATION_X_WWW_FORM_URLENCODED, mediaType);
    }

    public static boolean isOfMediaType(String expectedType, String actualType) {
        try {
            return new ContentType(actualType).match(expectedType);
        } catch (ParseException e) {
            // ignore
        }
        return false;
    }

    /**
     * Sorts a string of media types like the one in Accept
     *
     * @param s with MediaTypes e.g. text/html;q=0.9, application/json, application/xml;q=0.9, image/webp;q=0.8
     * @return List of sorted MediaTypes by quality
     */
    public static List<MediaType> sortMimeTypeByQualityFactorAscending(String s) {
        List<MediaType> m = parseMediaTypes(s);
        m.sort(comparingDouble(MediaType::getQualityValue));
        reverse(m);
        return m;
    }
}