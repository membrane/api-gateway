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

    public static final ContentType APPLICATION_JSON_CONTENT_TYPE = new ContentType(APPLICATION,"json",null);



    public static final ContentType APPLICATION_X_WWW_FORM_URLENCODED_CONTENT_TYPE = new ContentType(APPLICATION,APPLICATION_X_WWW_FORM_URLENCODED,null);

    public static boolean isXML(String mediaType) {
        try {
            ContentType contentType = new ContentType(mediaType);
            if (contentType.getSubType().contains("xml")) {
                return true;
            }
            if (contentType.getSubType().equals("xhtml")) {
                return true;
            }
            if (contentType.getSubType().equals("svg")) {
                return true;
            }
        } catch (ParseException e) {
           // ignore
        }
        return false;
    }

    public static boolean isBinary(String mediaType) {
        try {
            ContentType contentType = new ContentType(mediaType);

            // Primarytypes
            if (contentType.getPrimaryType().equals("image")) {
                return true;
            }
            if (contentType.getPrimaryType().equals("audio")) {
                return true;
            }
            if (contentType.getPrimaryType().equals("video")) {
                return true;
            }

            // Subtypes
            if (contentType.getSubType().equals("octet-stream")) {
                return true;
            }
            if (contentType.getSubType().equals("zip")) {
                return true;
            }
        } catch (ParseException e) {
            // ignore
        }
        return false;
    }

    public static boolean isWWWFormUrlEncoded(String mediaType) {
        try {
            return new ContentType(mediaType).match(APPLICATION_X_WWW_FORM_URLENCODED);
        } catch (ParseException e) {
            // ignore
        }
        return false;
    }
}
