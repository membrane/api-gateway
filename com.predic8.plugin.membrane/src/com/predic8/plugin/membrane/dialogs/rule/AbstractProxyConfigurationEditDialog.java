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

import java.net.*;

import javax.xml.stream.XMLStreamReader;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.dialogs.rule.composites.ProxyFeaturesTabComposite;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class AbstractProxyConfigurationEditDialog extends Dialog {

	public static final String LINK_URL = "http://www.membrane-soa.org/soap-router/doc/configuration/reference/";
	
	protected Rule rule;
	
	protected ProxyFeaturesTabComposite featuresTabComposite;
	
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
		createFeaturesTab(composite);
		
		new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(new GridData(210, 12));
		
		createLink(composite, "Configuration Reference");
		
		return composite;
	}
	
	private void createLink(Composite composite, String linkText) {
		Link link = new Link(composite, SWT.NONE);
		link.setText("<A>" + linkText + "</A>");
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(LINK_URL));
				} catch (Exception e1) {
					e1.printStackTrace();
				} 
			}
		});
	}
	
	public abstract String getTitle();
	
	public void setInput(Rule rule) {
		if (rule == null)
			return;
		
		this.rule = rule;
		featuresTabComposite.setInput(rule);
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
		getRuleManager().addRuleIfNew(newRule);
	}
	
	protected abstract Rule parseRule(XMLStreamReader reader) throws Exception;
		
	protected RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}
	
	protected HttpTransport getTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}



	protected void createFeaturesTab(Composite composite) {
		featuresTabComposite = new ProxyFeaturesTabComposite(composite);
	}
	
}
