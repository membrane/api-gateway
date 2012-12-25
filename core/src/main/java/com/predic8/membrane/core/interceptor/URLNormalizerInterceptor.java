/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;

/**
 * Replaces "/./" in the request URI's path by "/".
 * 
 * Necessary, as old SOA model versions do not normalize URIs before requesting
 * them. Our WSDLPublisher links to XSD Schemas using relative paths (as we want
 * the links to work under any servlet's context root). The SOA model then
 * combines "http://foo/material/ArticleService?wsdl" and
 * "./ArticleService?xsd=1" to "http://foo/material/./ArticleService?xsd=1".
 * This URI is sent to Membrane's new soapProxy which has configured a
 * serviceProxy-path of "\Q/material/ArticleService\E.*" which does not match.
 */
@MCInterceptor(xsd="" +
		"	<xsd:element name=\"urlNormalizer\" type=\"EmptyElementType\" />\r\n" + 
		"")
public class URLNormalizerInterceptor extends AbstractInterceptor {
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = exc.getRequestURI();
		if (uri.contains("/./")) {
			StringBuilder sb = new StringBuilder(uri.length());
			OUTER:
			for (int i = 0; i < uri.length(); i++) {
				int c = uri.codePointAt(i);
				switch (c) {
				case '?':
					sb.append(uri.substring(i));
					break OUTER;
				case '/':
					sb.appendCodePoint(c);
					while (i < uri.length() - 2 && uri.codePointAt(i+1) == '.' && uri.codePointAt(i+2) == '/')
						i += 2;
					break;
				default:
					sb.appendCodePoint(c);
					break;
				}
			}
			exc.getRequest().setUri(sb.toString());
		}
		return Outcome.CONTINUE;
	}

}
