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