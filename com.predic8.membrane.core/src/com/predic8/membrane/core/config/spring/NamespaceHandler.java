package com.predic8.membrane.core.config.spring;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class NamespaceHandler extends NamespaceHandlerSupport {
    
    public void init() {
        registerBeanDefinitionParser("router", new RouterParser());        
        registerBeanDefinitionParser("transport", new HttpTransportParser());
        registerBeanDefinitionParser("transform", new TransformInterceptorParser());
        registerBeanDefinitionParser("soapValidator", new SoapValidatorInterceptorParser());
        registerBeanDefinitionParser("regExUrlRewriter", new RegExUrlRewriterInterceptorParser());
        registerBeanDefinitionParser("balancer", new BalancerInterceptorParser());
        registerBeanDefinitionParser("adminConsole", new AdminConsoleInterceptorParser());
        registerBeanDefinitionParser("clusterNotification", new ClusterNotificationInterceptorParser());
        registerBeanDefinitionParser("webServer", new WebServerInterceptorParser());
        registerBeanDefinitionParser("forgetfulExchangeStore", new ForgetfulExchangeStoreParser());
        registerBeanDefinitionParser("memoryExchangeStore", new MemoryExchangeStoreParser());
        registerBeanDefinitionParser("fileExchangeStore", new FileExchangeStoreParser());
        registerBeanDefinitionParser("counter", new CounterInterceptorParser());
        registerBeanDefinitionParser("basicAuthentication", new BasicAuthenticationInterceptorParser());
        registerBeanDefinitionParser("accessControl", new AccessControlInterceptorParser());
        registerBeanDefinitionParser("wsdlRewriter", new WsdlRewriterInterceptorParser());
        registerBeanDefinitionParser("statisticsCSV", new StatisticsCSVInterceptorParser());        
        registerBeanDefinitionParser("statisticsJDBC", new StatisticsJDBCInterceptorParser());        
        registerBeanDefinitionParser("rest2Soap", new Rest2SoapInterceptorParser());        
        registerBeanDefinitionParser("cbr", new CbrInterceptorParser());        
        registerBeanDefinitionParser("regExReplacer", new RegExReplacerInterceptorParser());        
    }
}