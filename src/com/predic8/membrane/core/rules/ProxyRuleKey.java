package com.predic8.membrane.core.rules;

public class ProxyRuleKey extends AbstractRuleKey {
	
	public ProxyRuleKey(int port) {
		super(port);
	}

	@Override
	public String toString() {
		return "Proxy on port " + port;
	}
	
}
