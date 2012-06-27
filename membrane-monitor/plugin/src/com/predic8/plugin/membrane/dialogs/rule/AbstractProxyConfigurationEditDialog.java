/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */


package com.predic8.plugin.membrane.dialogs.rule;

import java.net.URL;

import javax.xml.stream.XMLStreamReader;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.dialogs.rule.composites.AbstractProxyXMLConfTabComposite;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class AbstractProxyConfigurationEditDialog extends Dialog {

	public static final String LINK_CONFIGURATION_REFERENCE = "http://www.membrane-soa.org/soap-router/doc/configuration/reference/";
	
	public static final String LINK_EXAMPLES_REFERENCE = "http://www.membrane-soa.org/soap-monitor-doc/interceptors/examples.htm";
	
	protected Rule rule;
	
	protected AbstractProxyXMLConfTabComposite featuresTabComposite;
	
	protected AbstractProxyConfigurationEditDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(getTitle());
		shell.setSize(520, 500);
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.BACK_ID, "Reset", false);
		createButton(parent, IDialogConstants.OK_ID, "OK", false);
		createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", true);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(SWTUtil.createGridLayout(1, 10));		
		
		createLink(composite, getText());
	
//		featuresTabComposite = new AbstractProxyXMLConfTabComposite(composite);
//		featuresTabComposite.setFocus();
		
		return composite;
	}
	
	private String getText() {
		return "Here you can configure advanced features like loadbalancing or routing for a proxy using" + System.getProperty("line.separator")  + "the XML based DSL. Have a look at the <A href=\"" +  LINK_CONFIGURATION_REFERENCE  + "\"> configuration reference </A> "  + "or the <A href=\"" + LINK_EXAMPLES_REFERENCE + "\"> examples </A> for " + System.getProperty("line.separator") + "reference.";
	}
	
	private Link createLink(Composite composite, String linkText) {
		Link link = new Link(composite, SWT.NONE | SWT.NO_FOCUS);
		link.setText(linkText);
		link.addListener(SWT.Selection, new Listener() {
		      public void handleEvent(Event event) {
		    	  try {
					PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(event.text));
				} catch (Exception e) {
					openErrorDialog("Unable to open external browser or specified URL.");
				} 
		      }
		});
		return link;
	}
	
	public abstract String getTitle();
	
	public void setInput(Rule rule) {
		if (rule == null)
			return;
		
		this.rule = rule;
		featuresTabComposite.setRule(rule);
	}
	
	protected void openErrorDialog(String msg) {
		MessageDialog.openError(this.getShell(), "Error", msg);
	}

	protected void openWarningDialog(String msg) {
		MessageDialog.openWarning(this.getShell(), "Warning", msg);
	}

	protected boolean openConfirmDialog(String msg) {
		return MessageDialog.openConfirm(this.getShell(), "Confirm", msg);
	}
		
	@Override
	protected void okPressed() {
		try {
			XMLStreamReader reader = featuresTabComposite.getStreamReaderForContent();
			Rule newRule = parseRule(reader);
			replaceRule(rule, newRule);
			close();
		} catch (Exception e) {
			 Status status = new Status(IStatus.ERROR, MembraneUIPlugin.PLUGIN_ID, 0, e.getMessage(), null);
			 ErrorDialog.openError(Display.getCurrent().getActiveShell(),  "Configuration Update Error", "Updating configuration failed! \n Make sure the data you entered is valid or cancel the editing.", status);
		}
	}
	
	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.BACK_ID) {
			setInput(rule);
		}
		super.buttonPressed(buttonId);
	}
	
	private void replaceRule(Rule oldRule, Rule newRule) throws Exception {
		getRuleManager().removeRule(oldRule);
		getRuleManager().addProxyAndOpenPortIfNew(newRule);
	}
	
	protected abstract Rule parseRule(XMLStreamReader reader) throws Exception;
		
	protected RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}
	
	protected void createFeaturesTab(Composite composite) {
		//featuresTabComposite = new AbstractProxyXMLConfTabComposite(composite);
	}
	
}
