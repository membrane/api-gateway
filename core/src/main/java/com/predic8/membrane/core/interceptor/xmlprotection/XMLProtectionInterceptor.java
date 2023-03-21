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

package com.predic8.membrane.core.interceptor.xmlprotection;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Prohibits XML documents to be passed through that look like XML attacks on older parsers. Too many
 *              attributes, too long element names are such indications. DTD definitions will simply be removed.
 * @topic 6. Security
 */
@MCElement(name="xmlProtection")
public class XMLProtectionInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(XMLProtectionInterceptor.class.getName());

	private int maxAttibuteCount = 1000;
	private int maxElementNameLength = 1000;
	private boolean removeDTD = true;


	public XMLProtectionInterceptor() {
		name = "XML Protection";
		setFlow(Flow.Set.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		if (exc.getRequest().isBodyEmpty()) {
			log.info("body is empty -> request is not scanned by xmlProtection");
			return CONTINUE;
		}

		if (!exc.getRequest().isXML()) {
			log.warn("request discarded by xmlProtection, because it's Content-Type header did not indicate that it is actually XML.");
			return ABORT;
		}

		if (!protectXML(exc)) {
			log.warn("request discarded by xmlProtection, because it is not wellformed or exceeds limits");
			setFailResponse(exc);
			return ABORT;
		}

		log.debug("protected against XML attacks");

		return CONTINUE;
	}

	private void setFailResponse(Exchange exc) {
		exc.setResponse(Response.badRequest("Invalid XML features used in request.").build());
	}

	private boolean protectXML(Exchange exc) throws Exception {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		XMLProtector protector = new XMLProtector(new OutputStreamWriter(stream, getCharset(exc)),
				removeDTD, maxElementNameLength, maxAttibuteCount);

		if (!protector.protect(new InputStreamReader(exc.getRequest().getBodyAsStreamDecoded(), getCharset(exc) )))
			return false;
		exc.getRequest().setBodyContent(stream.toByteArray());
		return true;
	}

	/**
	 * If no charset is specified, use standard XML charset UTF-8.
	 */
	private String getCharset(Exchange exc) {
		String charset = exc.getRequest().getCharset();
		if (charset == null)
			return UTF_8.name();

		return charset;
	}

	/**
	 * @description If an incoming request exceeds this limit, it will be discarded.
	 * @default 1000
	 */
	@MCAttribute
	public void setMaxAttibuteCount(int maxAttibuteCount) {
		this.maxAttibuteCount = maxAttibuteCount;
	}

	/**
	 * @description If an incoming request exceeds this limit, it will be discarded.
	 * @default 1000
	 */
	@MCAttribute
	public void setMaxElementNameLength(int maxElementNameLength) {
		this.maxElementNameLength = maxElementNameLength;
	}

	/**
	 * @description Whether to remove the DTD from incoming requests.
	 * @default true
	 */
	@MCAttribute
	public void setRemoveDTD(boolean removeDTD) {
		this.removeDTD = removeDTD;
	}

	@Override
	public String getShortDescription() {
		return "Protects agains XML attacks.";
	}

}

