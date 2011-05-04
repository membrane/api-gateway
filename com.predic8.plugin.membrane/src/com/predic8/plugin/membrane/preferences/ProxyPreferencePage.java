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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
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
import com.predic8.membrane.core.config.Proxy;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;

public class ProxyPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	public static final String PAGE_ID = "com.predic8.plugin.membrane.preferences.ProxyPreferencePage";

	protected Text textHost;
	protected Text textPort;
	protected Button btUseProxy;

	protected Text textUser;

	protected Text textPassword;

	protected Button btUseAuthent;

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
		composite.setLayout(new GridLayout());

		btUseProxy = createUseProxyButton(composite);

		Group proxyGroup = createGroup(composite, "Proxy Settings");

		new Label(proxyGroup, SWT.NONE).setText("Host");

		textHost = createHostText(proxyGroup);
		textHost.setEnabled(false);
		
		new Label(proxyGroup, SWT.NONE).setText("Port");

		textPort = createPortText(proxyGroup);
		textPort.setEnabled(false);
		
		new Label(proxyGroup, SWT.NONE).setText(" ");
		
		createAuthButton(proxyGroup);

		Group groupAuth = createGroup(proxyGroup, "Credentials");
		GridData gdA = new GridData();
		gdA.horizontalSpan = 2;
		groupAuth.setLayoutData(gdA);

		new Label(groupAuth, SWT.NONE).setText("Username: ");
		textUser = createText(groupAuth, SWT.NONE, 200, 1);
		textUser.setEnabled(false);
		
		new Label(groupAuth, SWT.NONE).setText("Password: ");
		textPassword = createText(groupAuth, SWT.PASSWORD, 200, 1);
		textPassword.setEnabled(false);
		
		setWidgets();

		GridData gd = new GridData();
		gd.verticalAlignment = GridData.FILL;
		gd.grabExcessVerticalSpace = true;
		
		Label label = new Label(composite, SWT.NONE);
		label.setText(" ");
		label.setLayoutData(gd);
		
		return composite;
	}

	private void createAuthButton(Group proxyGroup) {
		btUseAuthent = new Button(proxyGroup, SWT.CHECK);
		btUseAuthent.setText("Use Proxy Authentification");
		btUseAuthent.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				enableAuthentificationWidgets(btUseAuthent.getSelection());
			};
		});
		GridData gd = new GridData();
		gd.horizontalSpan = 2;
		btUseAuthent.setLayoutData(gd);
	}

	private void enableAuthentificationWidgets(boolean enabled) {
		textPassword.setEnabled(enabled);
		textUser.setEnabled(enabled);
	}
	
	private void setWidgets() {
		Configuration config = Router.getInstance().getConfigurationManager().getConfiguration();
		
		Proxy proxy = config.getProxy();
		
		if (proxy == null)
			return;
		
		if (proxy.getProxyHost() != null) {
			textHost.setText("" + proxy.getProxyHost());
		}

		textPort.setText("" + proxy.getProxyPort());
		

		if (proxy.getProxyUsername() != null)
			textUser.setText(proxy.getProxyUsername());

		if (proxy.getProxyPassword() != null)
			textPassword.setText(proxy.getProxyPassword());

		btUseAuthent.setSelection(proxy.isUseAuthentication());
		btUseAuthent.notifyListeners(SWT.Selection, null);
		btUseProxy.setSelection(proxy.isUseProxy());
		btUseProxy.notifyListeners(SWT.Selection, null);
	}

	private Text createText(Composite parent, int type, int width, int span) {
		Text text = new Text(parent, type | SWT.BORDER);
		GridData gData = new GridData(GridData.FILL_HORIZONTAL);
		gData.widthHint = width;
		gData.horizontalSpan = span;
		text.setLayoutData(gData);
		return text;
	}

	private Button createUseProxyButton(Composite composite) {
		final Button bt = new Button(composite, SWT.CHECK);
		bt.setText("Use Proxy Server");
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				enableWidgets(bt.getSelection());
			}
		});
		
		return bt;
	}

	private void enableWidgets(boolean enabled) {
		btUseAuthent.setEnabled(enabled);
		enableAuthentificationWidgets(enabled && btUseAuthent.getSelection());
		textHost.setEnabled(enabled);
		textPort.setEnabled(enabled);
	}
	
	private Text createPortText(Group proxyGroup) {
		Text text = new Text(proxyGroup, SWT.BORDER);
		text.addVerifyListener(new PortVerifyListener());
		GridData gData = new GridData();
		gData.widthHint = 70;
		text.setLayoutData(gData);
		return text;
	}

	private Text createHostText(Group proxyGroup) {
		Text text = new Text(proxyGroup, SWT.BORDER);
		GridData gData = new GridData(GridData.FILL_HORIZONTAL);
		gData.widthHint = 200;
		text.setLayoutData(gData);
		return text;
	}

	private Group createGroup(Composite composite, String title) {
		Group group = new Group(composite, SWT.NONE);
		group.setText(title);
		GridLayout layout = new GridLayout();
		layout.marginTop = 5;
		layout.marginLeft = 5;
		layout.marginBottom = 5;
		layout.marginRight = 5;
		
		layout.numColumns = 2;
		layout.verticalSpacing = 5;
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

	private void saveWidgetValues(boolean useProxy) {
		Router router = Router.getInstance();
		Proxy proxy = new Proxy(router);
		proxy.setUseProxy(useProxy);
		proxy.setProxyHost(textHost.getText());
		
		
		try {
			proxy.setProxyPort(Integer.parseInt(textPort.getText()));
		} catch (NumberFormatException e) {
			proxy.setProxyPort(0);
		}
		
		proxy.setUseAuthentication(btUseAuthent.getSelection());
		proxy.setProxyUsername(textUser.getText());
		proxy.setProxyPassword(textPassword.getText());
		
		router.getConfigurationManager().getConfiguration().setProxy(proxy);
		
	}

	private boolean isValidProxyParams() {
		return textHost.getText().trim().length() != 0 && textPort.getText().trim().length() != 0;
	}

	public void init(IWorkbench workbench) {
		setPreferenceStore(MembraneUIPlugin.getDefault().getPreferenceStore());
	}

}
