/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.acl;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Router;



public class Hostname extends AbstractClientAddress {

	private static Log log = LogFactory.getLog(Hostname.class.getName());

	public static final String ELEMENT_NAME = "hostname";

	private static InetAddress localhostIp4;
	private static InetAddress localhostIp6;

	
	public Hostname(Router router) {
		super(router);
		try {
			localhostIp4 = InetAddress.getByName("127.0.0.1");
			localhostIp6 = InetAddress.getByName("0:0:0:0:0:0:0:1");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	public boolean matches(InetAddress ia) {
		if(pattern.toString().equals("^localhost$") && (ia.equals(localhostIp4) || ia.equals(localhostIp6))){
			log.debug("Address to be matched : " + ia + " is being matched to :" + pattern.toString());
			return true;
		}
		String canonicalHostName = router.getDnsCache().getCanonicalHostName(ia);
		log.debug("CanonicalHostname for " + ia.getHostAddress() + " is "  + canonicalHostName);
		return pattern.matcher(canonicalHostName).matches();
	}

}
