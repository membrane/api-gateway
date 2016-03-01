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

package com.predic8.membrane.core.rules;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.interceptor.apimanagement.apiconfig.EtcdRegistryApiConfig;

/**
 * @description <p>
 *              A service proxy can be deployed on front of a Web server, Web Service or a REST resource. It conceals
 *              the server and offers the same interface as the target server to its clients.
 *              </p>
 * @topic 2. Proxies
 */
@MCElement(name="serviceProxy")
public class ServiceProxy extends AbstractServiceProxy {

	private String externalHostname;

	@Override
	public void init() throws Exception {
		if(externalHostname != null){
			if(!hasAdminConsole() || !etcdRegistryApiConfigExists())
				throw new RuntimeException("externalHostname is only usable in combination with AdminConsoleInterceptor and EtcdRegistryApiConfig");

		}
	}

	public ServiceProxy() {
		this.key = new ServiceProxyKey(80);
	}

	public ServiceProxy(ServiceProxyKey ruleKey, String targetHost, int targetPort) {
		this.key = ruleKey;
		setTargetHost(targetHost);
		setTargetPort(targetPort);
	}

	public boolean hasAdminConsole(){
		for(Interceptor i : interceptors)
			if(i instanceof AdminConsoleInterceptor)
				return true;
		return false;
	}

	public boolean etcdRegistryApiConfigExists(){
		try {
			if (router.getBeanFactory().getBean(EtcdRegistryApiConfig.class) != null)
				return true;
		}catch(Exception ignored){
		}
		return false;
	}


	@Override
	protected AbstractProxy getNewInstance() {
		return new ServiceProxy();
	}

	public String getMethod() {
		return ((ServiceProxyKey)key).getMethod();
	}

	/**
	 * @description If set, Membrane will only consider this rule, if the method (GET, PUT, POST, DELETE, etc.)
	 *              header of incoming HTTP requests matches. The asterisk '*' matches any method.
	 * @default *
	 * @example GET
	 */
	@MCAttribute
	public void setMethod(String method) {
		((ServiceProxyKey)key).setMethod(method);
	}

	public String getExternalHostname() {return externalHostname;}

	@MCAttribute
	public void setExternalHostname(String externalHostname){
		this.externalHostname = externalHostname;
	}

	public Target getTarget() {
		return target;
	}

	@MCChildElement(order=150)
	public void setTarget(Target target) {
		this.target = target;
	}

	public void setTargetHost(String targetHost) {
		this.target.setHost(targetHost);
	}

	public void setTargetPort(int targetPort) {
		this.target.setPort(targetPort);
	}

	public void setTargetURL(String targetURL) {
		this.target.setUrl(targetURL);
	}

}
