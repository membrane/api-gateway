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

package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.*;
import org.slf4j.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.balancer.BalancerUtil.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Receives control messages to dynamically modify the configuration of a {@link LoadBalancingInterceptor}.
 * @explanation See also examples/loadbalancer-client-2 in the Membrane API Gateway distribution.
 * @topic 7. Clustering and Loadbalancing
 */
@MCElement(name="clusterNotification")
public class ClusterNotificationInterceptor extends AbstractInterceptor {
	private static final Logger log = LoggerFactory.getLogger(ClusterNotificationInterceptor.class.getName());

	private final Pattern urlPattern = Pattern.compile("/clustermanager/(up|down|takeout)/?\\??(.*)");

	private boolean validateSignature = false;
	private int timeout = 0;
	private String keyHex;

	public ClusterNotificationInterceptor() {
		name = "ClusterNotifcationInterceptor";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug(exc.getOriginalRequestUri());

		Matcher m = urlPattern.matcher(exc.getOriginalRequestUri());

		if (!m.matches()) return Outcome.CONTINUE;

		log.debug("request received: "+m.group(1));
		if (validateSignature && !getParams(exc).containsKey("data")) {
			exc.setResponse(Response.forbidden().build());
			return Outcome.ABORT;
		}

		Map<String, String> params = validateSignature?
				getDecryptedParams(getParams(exc).get("data")):
					getParams(exc);

				if ( isTimedout(params) ) {
					exc.setResponse(Response.forbidden().build());
					return Outcome.ABORT;
				}

				updateClusterManager(m, params);

				exc.setResponse(Response.noContent().build());
				return Outcome.RETURN;
	}

	private void updateClusterManager(Matcher m, Map<String, String> params) {
		if ("up".equals(m.group(1))) {
			up(
					router,
					getBalancerParam(params),
					getClusterParam(params),
					params.get("host"),
					getPortParam(params));
		} else if ("down".equals(m.group(1))) {
			down(
					router,
					getBalancerParam(params),
					getClusterParam(params),
					params.get("host"),
					getPortParam(params));
		} else {
			takeout(
					router,
					getBalancerParam(params),
					getClusterParam(params),
					params.get("host"),
					getPortParam(params));
		}
	}

	private boolean isTimedout(Map<String, String> params) {
		return timeout > 0 && System.currentTimeMillis()-Long.parseLong(params.get("time")) > timeout;
	}

	private Map<String, String> getDecryptedParams(String data) throws Exception {
		// two issues, here not sure
		Cipher cipher = Cipher.getInstance("AES");
		SecretKeySpec skeySpec = new SecretKeySpec(Hex.decodeHex(keyHex.toCharArray()), "AES");
		cipher.init(Cipher.DECRYPT_MODE, skeySpec);
		return parseQueryString(new String(cipher.doFinal(Base64.decodeBase64(data.getBytes(UTF_8))), UTF_8), ERROR);
	}

	private int getPortParam(Map<String, String> params) {
		return Integer.parseInt(params.get("port"));
	}

	private String getClusterParam(Map<String, String> params) {
		return params.get("cluster") == null ? Cluster.DEFAULT_NAME : params.get("cluster");
	}

	private String getBalancerParam(Map<String, String> params) {
		return params.get("balancer") == null ? Balancer.DEFAULT_NAME : params.get("balancer");
	}

	private Map<String, String> getParams(Exchange exc) {

		String uri = exc.getOriginalRequestUri();
		int qStart = uri.indexOf('?');
		if (qStart == -1 || qStart + 1 == uri.length())
			return new HashMap<>();
		return parseQueryString(exc.getOriginalRequestUri().substring(
				qStart + 1), ERROR);
	}

	public boolean isValidateSignature() {
		return validateSignature;
	}

	/**
	 * @description Set Push Interface to encrypted mode.
	 */
	@MCAttribute
	public void setValidateSignature(boolean validateSignature) {
		this.validateSignature = validateSignature;
	}

	public int getTimeout() {
		return timeout;
	}

	/**
	 * @description Timestamp invalidation period. (0=unlimited)
	 * @example 5000
	 */
	@MCAttribute
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public String getKeyHex() {
		return keyHex;
	}

	/**
	 * @description Key used by encryption as hex string
	 * @example 6f488a642b740fb70c5250987a284dc0
	 */
	@MCAttribute
	public void setKeyHex(String keyHex) {
		this.keyHex = keyHex;
	}

	@Override
	public String getShortDescription() {
		return "Sets the status of load-balancer nodes to UP or DOWN, based on the request attributes.";
	}

}
