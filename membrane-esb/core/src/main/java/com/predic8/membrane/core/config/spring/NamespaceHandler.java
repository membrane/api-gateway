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

package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class NamespaceHandler extends NamespaceHandlerSupport {

	public void init() {
		registerBeanDefinitionParser("router", new RouterParser());
		registerBeanDefinitionParser("transport", new HttpTransportParser());
		registerBeanDefinitionParser("transform",
				new TransformInterceptorParser());
		registerBeanDefinitionParser("validator",
				new ValidatorInterceptorParser());
		registerBeanDefinitionParser("rewriter",
				new RewriterInterceptorParser());
		registerBeanDefinitionParser("balancer",
				new BalancerInterceptorParser());
		registerBeanDefinitionParser("adminConsole",
				new AdminConsoleInterceptorParser());
		registerBeanDefinitionParser("clusterNotification",
				new ClusterNotificationInterceptorParser());
		registerBeanDefinitionParser("webServer",
				new WebServerInterceptorParser());
		registerBeanDefinitionParser("forgetfulExchangeStore",
				new ForgetfulExchangeStoreParser());
		registerBeanDefinitionParser("memoryExchangeStore",
				new MemoryExchangeStoreParser());
		registerBeanDefinitionParser("fileExchangeStore",
				new FileExchangeStoreParser());
		registerBeanDefinitionParser("counter", new CounterInterceptorParser());
		registerBeanDefinitionParser("basicAuthentication",
				new BasicAuthenticationInterceptorParser());
		registerBeanDefinitionParser("accessControl",
				new AccessControlInterceptorParser());
		registerBeanDefinitionParser("wsdlRewriter",
				new WsdlRewriterInterceptorParser());
		registerBeanDefinitionParser("wadlRewriter",
				new WadlRewriterInterceptorParser());
		registerBeanDefinitionParser("statisticsCSV",
				new StatisticsCSVInterceptorParser());
		registerBeanDefinitionParser("statisticsJDBC",
				new StatisticsJDBCInterceptorParser());
		registerBeanDefinitionParser("rest2Soap",
				new Rest2SoapInterceptorParser());
		registerBeanDefinitionParser("switch", new SwitchInterceptorParser());
		registerBeanDefinitionParser("regExReplacer",
				new RegExReplacerInterceptorParser());
		registerBeanDefinitionParser("groovy", new GroovyInterceptorParser());
		registerBeanDefinitionParser("throttle",
				new ThrottleInterceptorParser());
		registerBeanDefinitionParser("formValidation",
				new FormValidationInterceptorParser());
		registerBeanDefinitionParser("log", new LogInterceptorParser());
		registerBeanDefinitionParser("httpClient",
				new HttpClientInterceptorParser());
		registerBeanDefinitionParser("reverseProxying",
				new ReverseProxyingInterceptorParser());
		registerBeanDefinitionParser("xmlProtection",
				new XmlProtectionInterceptorParser());
	}
}