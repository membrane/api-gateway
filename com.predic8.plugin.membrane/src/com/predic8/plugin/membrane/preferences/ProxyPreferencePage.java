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

package com.predic8.plugin.membrane.preferences;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;

public class ProxyPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	public static final String PAGE_ID = "com.predic8.plugin.membrane.preferences.ProxyPreferencePage";
	
	protected Text txtHost;
	protected Text txtPort;
	private Label lblHost;
	private Label lblPort;
	protected Button btUseProxy;
	
	protected Text txtUsername;
	
	protected Text txtPassword;
	
	protected Button btUseAuthentification; 
	
	public ProxyPreferencePage() {
		
	}

	public ProxyPreferencePage(String title) {
		super(title);
		setDescription("Provides settings for Proxy options.");
	}

	public ProxyPreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new RowLayout(SWT.FILL));
		
		Configuration config = Router.getInstance().getConfigurationManager().getConfiguration();
		
		btUseProxy = createUseProxyButton(composite, config); 

		Group proxyGroup = createProxyGroup(composite);

		lblHost = new Label(proxyGroup, SWT.NONE);
		lblHost.setText("Host");
		
		txtHost = createHostText(config, proxyGroup); 
		
		lblPort = new Label(proxyGroup, SWT.NONE);
		lblPort.setText("Port");

		txtPort = createPortText(config, proxyGroup);
		
		new Label(composite, SWT.NONE).setText(" ");
		
		btUseAuthentification = new Button(composite, SWT.CHECK);
		btUseAuthentification.setText("Use Proxy Authentification");
		
		Group groupAuth = createProxyAuthentificationGroup(composite);
		
		Label lbUsername = new Label(groupAuth, SWT.NONE);
		lbUsername.setText("Username: ");
		
		txtUsername = createText(groupAuth, SWT.NONE, 200, 1); 
		
		Label lbPassword = new Label(groupAuth, SWT.NONE);
		lbPassword.setText("Password: ");
		
		txtPassword = createText(groupAuth, SWT.PASSWORD, 200, 1); 
		
		return composite;
	}

	private Text createText(Composite parent, int type, int width, int span) {
		Text text = new Text(parent, type | SWT.BORDER);
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.widthHint = width;
		gData.horizontalSpan = span;
		text.setLayoutData(gData);
		
		return text;
	}
	
	private Button createUseProxyButton(Composite composite, Configuration config) {
		Button bt = new Button(composite, SWT.CHECK);
		bt.setText("Use Proxy Server");
		if (config.getUseProxy()) {
			bt.setSelection(true);
		}
		return bt;
	}

	private Text createPortText(Configuration config, Group proxyGroup) {
		Text text = new Text(proxyGroup, SWT.BORDER);
		text.addVerifyListener(new PortVerifyListener());
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.widthHint = 70;
		text.setLayoutData(gData);
		try {
			if (config.getProxyPort() != null) {
				text.setText("" + config.getProxyPort());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return text;
	}

	private Text createHostText(Configuration config, Group proxyGroup) {
		Text text = new Text(proxyGroup, SWT.BORDER);
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.widthHint = 200;
		text.setLayoutData(gData);
		if (config.getProxyHost() != null) {
			text.setText("" + config.getProxyHost());
		}
		return text;
	}

	private Group createProxyGroup(Composite composite) {
		Group group = new Group(composite, SWT.NONE);
		group.setText("Proxy Settings");
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		group.setLayout(layout);
		return group;
	}

	private Group createProxyAuthentificationGroup(Composite composite) {
		Group group = new Group(composite, SWT.NONE);
		group.setText("");
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		group.setLayout(layout);
		return group;
	}
	
	@Override
	protected void performApply() {
		setAndSaveConfig();
	}
	
	@Override
	public boolean performOk() {
		setAndSaveConfig();
		return true;
	}

	private void setAndSaveConfig() {
		if (btUseProxy.getSelection()) {
			if (isValidProxyParams()) {
				saveWidgetValues(true);
			} else {
				MessageDialog.openWarning(Display.getCurrent().getActiveShell(), "Warning", "Invaled configuration: please check proxy host and proxy port values");
				return; 
			}
		} else {
			saveWidgetValues(false);
		}
		
		try {
			Router.getInstance().getConfigurationManager().saveConfiguration(Router.getInstance().getConfigurationManager().getDefaultConfigurationFile());
		} catch (Exception e) {
			e.printStackTrace();
			MessageDialog.openError(Display.getCurrent().getActiveShell(), "Error", "Unable to save configuration: " + e.getMessage());
		}
	}

	private void saveWidgetValues(boolean selected) {
		Router.getInstance().getConfigurationManager().getConfiguration().setUseProxy(selected);
		Router.getInstance().getConfigurationManager().getConfiguration().setProxyHost(txtHost.getText());
		Router.getInstance().getConfigurationManager().getConfiguration().setProxyPort(txtPort.getText());
		
		Router.getInstance().getConfigurationManager().getConfiguration().setUseProxyAuthentification(btUseAuthentification.getSelection());
		Router.getInstance().getConfigurationManager().getConfiguration().setProxyAuthentificationUsername(txtUsername.getText());
		Router.getInstance().getConfigurationManager().getConfiguration().setProxyAuthentificationPassword(txtPassword.getText());
	}
	
	private boolean isValidProxyParams() {
		return txtHost.getText().trim().length() != 0 && txtPort.getText().trim().length() != 0;
	}
	
	public void init(IWorkbench workbench) {
		setPreferenceStore(MembraneUIPlugin.getDefault().getPreferenceStore());
	}

}
