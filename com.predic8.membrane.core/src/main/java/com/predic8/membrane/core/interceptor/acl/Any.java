package com.predic8.membrane.core.interceptor.acl;

import java.net.InetAddress;

import com.predic8.membrane.core.Router;

public class Any extends AbstractClientAddress {

	public static final String ELEMENT_NAME = "any";
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	public Any(Router router) {
		super(router);
	}

	@Override
	public boolean matches(InetAddress str) {
		return true;
	}

	@Override
	public String toString() {
		return "^.*$";
	}
}
