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

package com.predic8.membrane.core;

import java.nio.charset.Charset;
import java.util.Properties;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor;

public class Constants {

	public static final String MEMBRANE_HOME = "MEMBRANE_HOME";

	public static final String EMPTY_STRING = "";

	public static final String CRLF = "" + ((char) 13) + ((char) 10);

	public static final byte[] CRLF_BYTES = { 13, 10 };

	public static final String VERSION;
	
	static {
		String version = "3.3"; // fallback
		try {
			Properties p = new Properties();
			p.load(Constants.class.getResourceAsStream("/META-INF/maven/org.membrane-soa/service-proxy-core/pom.properties"));
			version = p.getProperty("version");
		} catch (Exception e) {
		}
		VERSION = version;
	}

	public static final String XML_VERSION = "1.0";

	public static final String UTF_8 = "UTF-8";
	public static final Charset UTF_8_CHARSET = Charset.forName(UTF_8);

	public static final String ISO_8859_1 = "ISO-8859-1";
	public static final Charset ISO_8859_1_CHARSET = Charset.forName(ISO_8859_1);

	public static final String UNKNOWN = "unknown";

	public static final String N_A = "N/A";

	public static final String HTTP_VERSION_11 = "1.1";

	public static final String HTTP_VERSION_10 = "1.0";

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

	public static final String PROTOCOL_HTTP = "HTTP";

	public static final String PRODUCT_NAME = "Membrane Service Proxy";
	public static final String PRODUCT_CONTACT_EMAIL = "info@predic8.de";

	public static final String HTML_FOOTER =
					"Copyright ©2009-2015 " +
			  		"<a href=\"http://predic8.com/\">predic8 GmbH</a>" +
			  		". All Rights Reserved. See " +
					"<a href=\"http://www.membrane-soa.org/service-proxy/\">http://www.membrane-soa.org/service-proxy/</a>" +
			  		" for documentation and updates.";

	/**
	 * Used for {@link Request}-to-XML and XML-to-{@link Response} conversions.
	 * See {@link REST2SOAPInterceptor}.
	 */
	public static final String HTTP_NS = "http://membrane-soa.org/schemas/http/v1/";
}
