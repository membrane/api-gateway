package com.predic8.plugin.membrane.dialogs.rule.composites;

import java.io.*;
import java.net.URL;

import javax.xml.stream.*;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.TextUtil;
import com.predic8.plugin.membrane.listeners.HighligtingLineStyleListner;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class AbstractProxyXMLConfTabComposite extends AbstractProxyFeatureComposite {

	public static final String LINK_CONFIGURATION_REFERENCE = "http://www.membrane-soa.org/soap-router/doc/configuration/reference/";
	
	public static final String LINK_EXAMPLES_REFERENCE = "http://www.membrane-soa.org/soap-monitor-doc/interceptors/examples.htm";
	
	private StyledText text;

	private String originalXml;
	
	public AbstractProxyXMLConfTabComposite(final Composite parent) {
		super(parent);
		setLayout(SWTUtil.createGridLayout(1, 10));
		this.setLayoutData(SWTUtil.getGreedyGridData());
		
		createLink(this, getText());
		
		text = createStyledText();
		text.addLineStyleListener(new HighligtingLineStyleListner());
	}

	private StyledText createStyledText() {
		StyledText text = new StyledText(this, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		text.setLayoutData(SWTUtil.getGreedyGridData());
		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (originalXml == null)
					return;
				
				StyledText t = (StyledText)e.widget;
				if (!t.getText().equals(originalXml)) {
					dataChanged = true;
					System.err.println("xml tab reported data change");
				}
					
			}
		});
		
		return text;
	}

	public String getContent() {
		return text.getText();
	}
	
	@Override
	public void setRule(Rule rule) {
		super.setRule(rule);
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(baos, Constants.UTF_8);
			rule.write(writer);
			ByteArrayInputStream stream = new ByteArrayInputStream(baos.toByteArray());
			InputStreamReader reader = new InputStreamReader(stream, Constants.UTF_8);
			originalXml = TextUtil.formatXML(reader);
			text.setText(originalXml);
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
	
	public XMLStreamReader getStreamReaderForContent() throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
	    ByteArrayInputStream stream = new ByteArrayInputStream(text.getText().getBytes());
	    return factory.createXMLStreamReader(stream);
	}
	
	@Override
	public boolean setFocus() {
		text.setFocus();
		return true;
	}
	
	private Link createLink(Composite composite, String linkText) {
		Link link = new Link(composite, SWT.NONE | SWT.NO_FOCUS);
		link.setText(linkText);
		link.addListener(SWT.Selection, new Listener() {
		      public void handleEvent(Event event) {
		    	  try {
					PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(event.text));
				} catch (Exception e) {
					MessageDialog.openWarning(AbstractProxyXMLConfTabComposite.this.getShell(), "Warning", "Unable to open external browser or specified URL.");
				} 
		      }
		});
		return link;
	}
	
	private String getText() {
		return "Here you can configure advanced features like loadbalancing or routing for a proxy" + System.getProperty("line.separator")  + "using the XML based DSL. Have a look at the <A href=\"" +  LINK_CONFIGURATION_REFERENCE  + "\"> configuration reference </A> "  + "or the <A href=\"" + LINK_EXAMPLES_REFERENCE + "\"> examples </A> " + System.getProperty("line.separator") + "for reference.";
	}
	
	@Override
	public String getTitle() {
		return "XML Configuration";
	}
	
	@Override
	public void commit() {
		if (rule == null)
			return;
		
		try {
			XMLStreamReader reader = getStreamReaderForContent();
			rule = parseRule(reader);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	
	}
	
	protected abstract Rule parseRule(XMLStreamReader reader) throws Exception;
	
}
