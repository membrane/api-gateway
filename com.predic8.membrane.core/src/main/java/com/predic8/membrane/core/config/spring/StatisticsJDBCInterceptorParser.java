package com.predic8.membrane.core.config.spring;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

import com.predic8.membrane.core.interceptor.statistics.StatisticsJDBCInterceptor;

public class StatisticsJDBCInterceptorParser extends AbstractParser {

	protected Class<?> getBeanClass(Element element) {
		return StatisticsJDBCInterceptor.class;
	}

	@Override
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
		setIdIfNeeded(element, "statisticsJDBC");
		
		builder.addPropertyValue("postMethodOnly", element.getAttribute("postMethodOnly"));
		builder.addPropertyValue("soapOnly", element.getAttribute("soapOnly"));
		//builder.addPropertyValue("dataSource", getDataSource(DomUtils.getChildElementByTagName(element, "dataSource")));
		builder.addPropertyReference("dataSource", element.getAttribute("dataSource"));
		builder.setInitMethodName("init");
	}

	private DataSource getDataSource(Element element) {
		BasicDataSource d = new BasicDataSource();
		
		d.setDriverClassName(element.getAttribute("driverClassName"));
		d.setUrl(element.getAttribute("url"));
		d.setPassword(element.getAttribute("password"));
		d.setUsername(element.getAttribute("user"));
		
		return d;
	}
	
}
