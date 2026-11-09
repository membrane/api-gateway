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

package com.predic8.membrane.core.config;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;


/**
 * @description <p>Selects the requests an API handles by their path. A plain path matches every request path that
 * starts with it. In an <code>api</code>, the path can also be a URI template such as <code>/books/{id}</code>; the
 * parameter values are available as <code>pathParam</code>, e.g. <code>${pathParam.id}</code> in the target URL.</p>
 * <p>See tutorials/getting-started/60-Basic-Path-Routing.yaml and
 * tutorials/advanced/20-Path-Parameter-Routing.yaml.</p>
 * <h3>Security Hint</h3>
 * <p>A regular expression runs on every request before authentication. Overlapping quantifiers such as
 * <code>(.*)/(.*)</code> can take a long time on crafted paths. See
 * <a href="https://www.membrane-api.io/security/membrane-redos-protection.html">ReDoS Protection</a>.</p>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   path:
 *     uri: /shop
 *   target:
 *     url: https://api.predic8.de
 * ---
 * api:
 *   port: 2000
 *   path:
 *     uri: /fruits/{id}
 *   target:
 *     url: https://api.predic8.de/shop/v2/products/${pathParam.id}
 * </code></pre>
 */
@MCElement(name="path", component =false, mixed=true)
public class Path {

	private String uri;

	private boolean regExp;

	public Path() {
	}

	public Path(boolean regExp, String uri) {
		this.regExp = regExp;
		this.uri = uri;
	}

	public boolean isRegExp() {
		return regExp;
	}

	/**
	 * @description <p>If true, <code>uri</code> is a Java regular expression that must match the whole path. In an
	 * <code>api</code>, the query is ignored and <code>{name}</code> parameters can be used; in a
	 * <code>serviceProxy</code>, the expression must also match the query. Not supported in a <code>soapProxy</code>.</p>
	 * @default false
	 * @example true
	 */
	@MCAttribute(attributeName="isRegExp")
	public void setRegExp(boolean regExp) {
		this.regExp = regExp;
	}

	public String getUri() {
		return uri;
	}

	/**
	 * @description The path prefix, URI template or regular expression to match.
	 * @example /shop
	 */
	@MCTextContent
	public void setUri(String uri) {
		this.uri = uri;
	}

}
