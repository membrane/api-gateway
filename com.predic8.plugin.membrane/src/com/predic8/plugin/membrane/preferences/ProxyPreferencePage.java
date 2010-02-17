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

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.MembraneUIPlugin;

public class ProxyPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	public static final String PAGE_ID = "com.predic8.plugin.membrane.preferences.ProxyPreferencePage";
	
	protected Text txtHost;
	protected Text txtPort;
	private Label lblHost;
	private Label lblPort;
	protected Button useproxy;
	
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
		
		useproxy = createUseProxyButton(composite, config); 

		Group proxyGroup = createProxyGroup(composite);

		lblHost = new Label(proxyGroup, SWT.NONE);
		lblHost.setText("Host");
		
		txtHost = createHostText(config, proxyGroup); 
		
		lblPort = new Label(proxyGroup, SWT.NONE);
		lblPort.setText("Port");

		txtPort = createPortText(config, proxyGroup);
		
		return composite;
	}

	private Button createUseProxyButton(Composite composite, Configuration config) {
		Button bt = new Button(composite, SWT.CHECK);
		bt.setText("Use Proxy Server");
		if (config.props.get(Configuration.PROXY_USE) != null) {
			bt.setSelection((Boolean) config.props.get(Configuration.PROXY_USE));
		}
		return bt;
	}

	private Text createPortText(Configuration config, Group proxyGroup) {
		Text text = new Text(proxyGroup, SWT.BORDER);
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.widthHint = 70;
		text.setLayoutData(gData);
		if (config.props.get(Configuration.PROXY_PORT) != null) {
			if("".equals((String)config.props.get(Configuration.PROXY_PORT)))
				text.setText((String) config.props.get(Configuration.PROXY_PORT));
			else
				text.setText((String) config.props.get(Configuration.PROXY_PORT));
		}
		return text;
	}

	private Text createHostText(Configuration config, Group proxyGroup) {
		Text text = new Text(proxyGroup, SWT.BORDER);
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.widthHint = 200;
		text.setLayoutData(gData);
		if (config.props.get(Configuration.PROXY_HOST) != null) {
			if("".equals((String)config.props.get(Configuration.PROXY_HOST)))
				text.setText((String) config.props.get(Configuration.PROXY_HOST));
			else
				text.setText((String) config.props.get(Configuration.PROXY_HOST));
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

	public void init(IWorkbench workbench) {
		setPreferenceStore(MembraneUIPlugin.getDefault().getPreferenceStore());
	}

}
