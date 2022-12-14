/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.parser;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.NoSuchComponentException;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;

/**
 * Partial implementation of {@link ApplicationContext} to support the methods
 * called by membrane-core in a blueprint-based deployment.
 */
public class BlueprintSimulatedSpringApplicationContext implements ApplicationContext {

	private final BlueprintContainer blueprintContainer;

	public BlueprintSimulatedSpringApplicationContext(BlueprintContainer blueprintContainer) {
		this.blueprintContainer = blueprintContainer;
	}



	@SuppressWarnings("unchecked")
	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> clazz)
			throws BeansException {
		HashMap<String, T> res = new HashMap<String, T>();
		for (String componentName : blueprintContainer.getComponentIds()) {
			ComponentMetadata componentDefinition = blueprintContainer.getComponentMetadata(componentName);
			if (clazz.isAssignableFrom(componentDefinition.getClass())) {
				res.put(componentName, (T) blueprintContainer.getComponentInstance(componentName));
			}
		}
		return res;
	}

	@Override
	public Object getBean(String name) throws BeansException {
		try {
			return blueprintContainer.getComponentInstance(name);
		} catch (NoSuchComponentException e) {
			throw new NoSuchBeanDefinitionException(name);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		Object bean = getBean(name);
		if (requiredType != null && !requiredType.isAssignableFrom(bean.getClass())) {
			throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
		}
		return (T)bean;
	}



	@Override
	public Environment getEnvironment() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public boolean containsBeanDefinition(String arg0) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public <A extends Annotation> A findAnnotationOnBean(String arg0,
			Class<A> arg1) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public <A extends Annotation> A findAnnotationOnBean(String s, Class<A> aClass, boolean b) throws NoSuchBeanDefinitionException {
		return null;
	}

	@Override
	public int getBeanDefinitionCount() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String[] getBeanDefinitionNames() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> aClass, boolean b) {
		return null;
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType resolvableType, boolean b) {
		return null;
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType resolvableType) {
		return new String[0];
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType resolvableType, boolean b, boolean b1) {
		return new String[0];
	}

	@Override
	public String[] getBeanNamesForType(Class<?> arg0) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String[] getBeanNamesForType(Class<?> arg0, boolean arg1,
			boolean arg2) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> arg0, boolean arg1,
			boolean arg2) throws BeansException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> aClass) {
		return new String[0];
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(
			Class<? extends Annotation> arg0) throws BeansException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public boolean containsBean(String arg0) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String[] getAliases(String arg0) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public <T> T getBean(Class<T> arg0) throws BeansException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public Object getBean(String arg0, Object... arg1) throws BeansException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public <T> T getBean(Class<T> aClass, Object... objects) throws BeansException {
		return null;
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> aClass) {
		return null;
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType resolvableType) {
		return null;
	}

	@Override
	public Class<?> getType(String arg0) throws NoSuchBeanDefinitionException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public Class<?> getType(String s, boolean b) throws NoSuchBeanDefinitionException {
		return null;
	}

	@Override
	public boolean isPrototype(String arg0)
			throws NoSuchBeanDefinitionException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public boolean isTypeMatch(String s, ResolvableType resolvableType) throws NoSuchBeanDefinitionException {
		return false;
	}

	@Override
	public boolean isSingleton(String arg0)
			throws NoSuchBeanDefinitionException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public boolean isTypeMatch(String arg0, Class<?> arg1)
			throws NoSuchBeanDefinitionException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public boolean containsLocalBean(String arg0) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public BeanFactory getParentBeanFactory() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getMessage(String code, Object[] args, String defaultMessage,
			Locale locale) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getMessage(String code, Object[] args, Locale locale)
			throws NoSuchMessageException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale)
			throws NoSuchMessageException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public void publishEvent(ApplicationEvent event) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public void publishEvent(Object o) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public Resource[] getResources(String arg0) throws IOException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public ClassLoader getClassLoader() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public Resource getResource(String arg0) {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getId() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getApplicationName() {
		return "Membrane";
	}

	@Override
	public String getDisplayName() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public long getStartupDate() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public ApplicationContext getParent() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory()
			throws IllegalStateException {
		throw new RuntimeException("not implemented");
	}

	@Override
	public <A extends Annotation> Set<A> findAllAnnotationsOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		throw new RuntimeException("not implemented");
	}
}
