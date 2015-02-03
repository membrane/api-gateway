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

    private static  InetAddress localhostIp4 = initV4();
    private static InetAddress initV4() {
        try {
            //this should probably never fail... unless no network available.
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            log.error("Failed resolving localhost IPv4 127.0.0.1!");
            return null;
        }
    }
    private static final InetAddress localhostIp6 = initV6();
    private static InetAddress initV6() {
        try {
            //could this fail if the machine has no IPv6 support?
            return InetAddress.getByName("0:0:0:0:0:0:0:1");
        } catch (UnknownHostException e) {
            log.error("Failed resolving localhost IPv6 0:0:0:0:0:0:0:1!");
            return null;
        }
    }

	private boolean reverseDNS;
	private volatile long lastWarningSlowReverseDNSUsed;

	
	public Hostname(Router router) {
		super(router);
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	public boolean matches(String hostname, String ip) {
		try {
			if (pattern.toString().equals("^localhost$")) {
				InetAddress ia = InetAddress.getByName(ip);
				if (ia.equals(localhostIp4) || ia.equals(localhostIp6)) {
					log.debug("Address to be matched : " + ia + " is being matched to :" + pattern.toString());
					return true;
				}
			}
			if (!reverseDNS) {
				long now = System.currentTimeMillis();
				if (now - lastWarningSlowReverseDNSUsed > 10 * 60 * 1000) {
					log.warn("transport/@reverseDNS=false is incompatible with ACL hostname filtering. (Please use ip filtering instead.) Slow reverse DNS lookup will be performed.");
					lastWarningSlowReverseDNSUsed = now;
				}
			}
			String canonicalHostName = router.getDnsCache().getCanonicalHostName(InetAddress.getByName(ip));
			log.debug("CanonicalHostname for " + hostname + " / " + ip + " is "  + canonicalHostName);
			return pattern.matcher(canonicalHostName).matches();
		} catch (UnknownHostException e) {
			log.warn("Could not reverse lookup canonical hostname for " + hostname + " " + ip + ".", e);
			return false;
		}
	}
	
	@Override
	public void init(Router router) {
		super.init(router);
		reverseDNS = router.getTransport().isReverseDNS();
	}

}
