package com.predic8.membrane.core.rules;

import com.predic8.membrane.annot.MCElement;

/**
 * @description <p>Proxies incoming STOMP CONNECT requests. Use a &lt;stompClient&gt; to forward these requests so some other machine.</p>
 * @topic 2. Proxies
 */
@MCElement(name="stompProxy")
public class STOMPProxy extends SSLableProxy {

	public STOMPProxy() {
		this.key = new ServiceProxyKey(80);
		((ServiceProxyKey)key).setMethod("CONNECT");
	}

	@Override
	protected AbstractProxy getNewInstance() {
		return new STOMPProxy();
	}

}
