/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.interceptor.DispatchingInterceptor;
import com.predic8.membrane.core.interceptor.ExchangeStoreInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.RuleMatchingInterceptor;
import com.predic8.membrane.core.interceptor.UserFeatureInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.ReverseProxyingInterceptor;
import com.predic8.membrane.core.transport.http.HttpTransport;

@MCElement(name="defaultConfig")
public class DefaultConfig implements BeanFactoryPostProcessor, Ordered {

	private int order = 100; // the order in which BeanFactoryPostProcessors get executed
	
	public void setOrder(int order) {
		this.order = order;
	}
	
	@Override
	public int getOrder() {
		return order;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;

		defineTransformerFactory(beanDefinitionRegistry);
		defineLimitedMemoryExchangeStore(beanDefinitionRegistry);
		defineTransport(beanDefinitionRegistry);
		defineRouter(beanDefinitionRegistry);
	}

	private void defineRouter(BeanDefinitionRegistry beanDefinitionRegistry) {
		if (!beanDefinitionRegistry.containsBeanDefinition("router")) {
			beanDefinitionRegistry.registerBeanDefinition(
					"router", 
					root().clazz(Router.class).addRef("transport", "transport").addRef("exchangeStore", "memoryExchangeStore").build());
		}
	}

	private void defineTransport(BeanDefinitionRegistry beanDefinitionRegistry) {
		if (!beanDefinitionRegistry.containsBeanDefinition("transport")) {
			beanDefinitionRegistry.registerBeanDefinition(
					"transport", 
					root()
					.clazz(HttpTransport.class)
					.addRef("interceptors[0]", childOf("transport").clazz(RuleMatchingInterceptor.class).build())
					.addRef("interceptors[1]", childOf("transport").clazz(ExchangeStoreInterceptor.class).addRef("exchangeStore", "memoryExchangeStore").build())
					.addRef("interceptors[2]", childOf("transport").clazz(DispatchingInterceptor.class).build())
					.addRef("interceptors[3]", childOf("transport").clazz(ReverseProxyingInterceptor.class).build())
					.addRef("interceptors[4]", childOf("transport").clazz(UserFeatureInterceptor.class).build())
					.addRef("interceptors[5]", childOf("transport").clazz(HTTPClientInterceptor.class).build())
					.build());
		}
	}

	private BeanDefinitionBuilder childOf(String parentBeanName) {
		return new BeanDefinitionBuilder(parentBeanName);
	}

	private BeanDefinitionBuilder root() {
		return new BeanDefinitionBuilder();
	}
	
	private class BeanDefinitionBuilder {
		private AbstractBeanDefinition cbd;

		public BeanDefinitionBuilder() {
			cbd = new RootBeanDefinition();
		}

		public BeanDefinitionBuilder(String parentBeanName) {
			cbd = new GenericBeanDefinition();
		}
		
		public BeanDefinitionBuilder clazz(Class<?> clazz) {
			cbd.setBeanClass(clazz);
			return this;
		}
		
		public BeanDefinitionBuilder addRef(String name, String ref) {
			cbd.getPropertyValues().add(name, new RuntimeBeanReference(ref));
			return this;
		}

		public BeanDefinitionBuilder addRef(String name, AbstractBeanDefinition beanDefinition) {
			cbd.getPropertyValues().add(name, beanDefinition);
			return this;
		}

		public AbstractBeanDefinition build() {
			return cbd;
		}
	}
	
	private void defineLimitedMemoryExchangeStore(BeanDefinitionRegistry beanDefinitionRegistry) {
		if (!beanDefinitionRegistry.containsBeanDefinition("memoryExchangeStore")) {
			beanDefinitionRegistry.registerBeanDefinition(
					"memoryExchangeStore", 
					new RootBeanDefinition(LimitedMemoryExchangeStore.class));
		}
	}

	private void defineTransformerFactory(BeanDefinitionRegistry beanDefinitionRegistry) {
		if (!beanDefinitionRegistry.containsBeanDefinition("transformerFactory")) {
			Class<?> clazz;
			try {
				clazz = Class.forName("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
			} catch (ClassNotFoundException e) {
				throw new RuntimeException("Please define a bean called 'transformerFactory' which extends javax.xml.transform.TransformeFactory to specify which XSLT processor to use.", e);
			}
			RootBeanDefinition def = new RootBeanDefinition(clazz);
			def.setScope("singleton");
			beanDefinitionRegistry.registerBeanDefinition(
					"transformerFactory", 
					def);
		}
	}

}
