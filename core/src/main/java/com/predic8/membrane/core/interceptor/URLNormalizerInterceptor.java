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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.URIUtil.*;

/**
 * @description <p>
 *              Replaces "/./" in the request URI's path by "/".
 *              </p>
 *              <p>
 *              Necessary, as old SOA model versions do not normalize URIs before requesting them. Our WSDLPublisher
 *              links to XSD Schemas using relative paths (as we want the links to work under any servlet's context
 *              root). The SOA model then combines "<a href="http://foo/material/ArticleService?wsdl">http://foo/material/ArticleService?wsdl</a>" and
 *              "./ArticleService?xsd=1" to "<a href="http://foo/material/./ArticleService?xsd=1">http://foo/material/./ArticleService?xsd=1</a>". This URI is sent to Membrane's
 *              new soapProxy which has configured a serviceProxy-path of "\Q/material/ArticleService\E.*" which does
 *              not match.
 *              </p>
 * @topic 6. Misc
 */
@MCElement(name="urlNormalizer")
public class URLNormalizerInterceptor extends AbstractInterceptor {

	public URLNormalizerInterceptor() {
		name = "url path normalizer";
	}

	@Override
	public String getShortDescription() {
		return """
			Replaces /./ in the request URI's path by /. 
			
			e.g. 
			http//api.predic8.de/foo/./bar 
			
			to:
			
			http//api.predic8.de/foo/bar 
			""";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
        exc.getRequest().setUri(normalizeSingleDot(exc.getRequestURI()));
		return CONTINUE;
	}
}
