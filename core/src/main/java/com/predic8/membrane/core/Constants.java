/* Copyright 2009, 2012, 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.rest.*;

import java.io.*;
import java.util.*;

public class Constants {

	public static final String MEMBRANE_HOME = "MEMBRANE_HOME";

	public static final String CRLF = "" + ((char) 13) + ((char) 10);

	public static final byte[] CRLF_BYTES = { 13, 10 };

	public static final String VERSION;

	static {
		String version = "5.3"; // fallback
		try {
			Properties p = new Properties(); // Production
			p.load(Constants.class.getResourceAsStream("/META-INF/maven/org.membrane-soa/service-proxy-core/pom.properties"));
			version = p.getProperty("version");
		} catch (Exception e) {
			try {
				Properties p = new Properties(); // Development
				p.load(new FileInputStream("target/maven-archiver/pom.properties"));
				version = p.getProperty("version") + " - DEVELOPMENT";
			} catch (Exception e2) {
			}
		}
		VERSION = version;
	}

	public static final String ISO_8859_1 = "ISO-8859-1";

	public static final String UNKNOWN = "unknown";

	public static final String N_A = "N/A";

	public static final String HTTP_VERSION_11 = "1.1";

	public static final String WSDL_SOAP11_NS = "http://schemas.xmlsoap.org/wsdl/soap/";
	public static final String WSDL_SOAP12_NS = "http://schemas.xmlsoap.org/wsdl/soap12/";
	public static final String WSDL_HTTP_NS = "http://schemas.xmlsoap.org/wsdl/http/";
	public static final String WADL_NS = "http://wadl.dev.java.net/2009/02";
	public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

	public static final String SOAP11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
	public static final String SOAP12_NS = "http://www.w3.org/2003/05/soap-envelope";
	public static final String SOAP11_VERION = "1.1";
	public static final String SOAP12_VERION = "1.2";

	public static final String PROTOCOL_SOAP11 = "SOAP11";

	public static final String PROTOCOL_SOAP12 = "SOAP12";

	public static final String PRODUCT_NAME = "Membrane API Gateway";
	public static final String PRODUCT_WEBSITE = "http://www.membrane-soa.org/api-gateway/";
	public static final String PRODUCT_WEBSITE_DOC = "http://www.membrane-soa.org/api-gateway-doc/";
	public static final String PRODUCT_CONTACT_EMAIL = "info@predic8.de";

	public static final String HTML_FOOTER =
			"Copyright ©2009-2023 " +
					"<a href=\"http://predic8.com/\">predic8 GmbH</a>" +
					". All Rights Reserved. See " +
					"<a href=\"http://www.membrane-soa.org/api-gateway/\">http://www.membrane-soa.org/api-gateway/</a>" +
					" for documentation and updates.";

	/**
	 * Used for {@link Request}-to-XML and XML-to-{@link Response} conversions.
	 * See {@link REST2SOAPInterceptor}.
	 */
	public static final String HTTP_NS = "http://membrane-soa.org/schemas/http/v1/";

	/**
	 * The user agent string that will be sent when identifying as Membrane
	 */
	public static final String USERAGENT = "Membrane " + VERSION;
}
