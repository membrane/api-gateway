package com.predic8.membrane.annot;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandler;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AbstractNamespaceHandler implements NamespaceHandler {

	private final Map<String, BeanDefinitionParser> parsers = new HashMap<String, BeanDefinitionParser>();
	
	private final Map<String, Map<String, BeanDefinitionParser>> localParsers = new HashMap<String, Map<String,BeanDefinitionParser>>();

	@Override
	public void init() {
		// do nothing
	}

	@Override
	public BeanDefinitionHolder decorate(Node source, BeanDefinitionHolder definition, ParserContext parserContext) {
		return definition;
	}

	public void registerGlobalBeanDefinitionParser(String elementName, BeanDefinitionParser parser) {
		this.parsers.put(elementName, parser);
	}

	public void registerLocalBeanDefinitionParser(String parentBeanClassName, String elementName, BeanDefinitionParser parser) {
		Map<String, BeanDefinitionParser> lp = localParsers.get(parentBeanClassName);
		if (lp == null) {
			lp = new HashMap<String, BeanDefinitionParser>();
			localParsers.put(parentBeanClassName, lp);
		}
		lp.put(elementName, parser);
	}

	private BeanDefinitionParser findParserForElement(Element element, ParserContext parserContext) {
		String localName = parserContext.getDelegate().getLocalName(element);
		
		System.err.println("looking for " + localName);

		if (parserContext.getContainingBeanDefinition() != null) {
			String beanClassName = parserContext.getContainingBeanDefinition().getBeanClassName();
			System.err.println("in " + beanClassName);
			Map<String, BeanDefinitionParser> parentLocalParsers = localParsers.get(beanClassName);
			if (parentLocalParsers != null) {
				BeanDefinitionParser parser = parentLocalParsers.get(localName);
				if (parser != null)
					return parser;
			}
		}
		
		BeanDefinitionParser parser = this.parsers.get(localName);
		if (parser == null) {
			parserContext.getReaderContext().fatal("Cannot locate BeanDefinitionParser for element [" + localName + "]", element);
		}
		return parser;
	}

	public BeanDefinition parse(Element element, ParserContext parserContext) {
		
		return findParserForElement(element, parserContext).parse(element, parserContext);
	}

	

}
