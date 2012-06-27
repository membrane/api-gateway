package com.predic8.plugin.membrane.dialogs.rule.composites;

import javax.xml.stream.XMLStreamReader;

import org.eclipse.swt.widgets.Composite;

import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;

public class ProxyXMLConfComposite extends AbstractProxyXMLConfTabComposite {

	public ProxyXMLConfComposite(Composite parent) {
		super(parent);
		
	}

	@Override
	protected Rule parseRule(XMLStreamReader reader) throws Exception {
		return (ProxyRule)new ProxyRule().parse(reader);
	}

}
