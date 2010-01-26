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
	
	private Text txtHost;
	private Text txtPort;
	private Label lblHost;
	private Label lblPort;
	private Button useproxy;
	
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
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new RowLayout(SWT.FILL));
		
		Configuration config = Router.getInstance().getConfigurationManager().getConfiguration();
		
		useproxy = new Button(comp, SWT.CHECK);
		useproxy.setText("Use Proxy Server");
		if (config.props.get(Configuration.PROXY_USE) != null) {
			useproxy.setSelection((Boolean) config.props.get(Configuration.PROXY_USE));
		} 

		Group proxyGroup = new Group(comp, SWT.NONE);
		proxyGroup.setText("Proxy Settings");
		GridLayout g2 = new GridLayout();
		g2.numColumns = 2;
		proxyGroup.setLayout(g2);

	
		lblHost = new Label(proxyGroup, SWT.NONE);
		lblHost.setText("Host");
		
		txtHost = new Text(proxyGroup, SWT.BORDER);
		GridData gdHost = new GridData(GridData.FILL_BOTH);
		gdHost.widthHint = 200;
		txtHost.setLayoutData(gdHost);
		if (config.props.get(Configuration.PROXY_HOST) != null) {
			if("".equals((String)config.props.get(Configuration.PROXY_HOST)))
				txtHost.setText((String) config.props.get(Configuration.PROXY_HOST));
			else
				txtHost.setText((String) config.props.get(Configuration.PROXY_HOST));
		} 
		

		lblPort = new Label(proxyGroup, SWT.NONE);
		lblPort.setText("Port");

		txtPort = new Text(proxyGroup, SWT.BORDER);
		GridData gdPort = new GridData(GridData.FILL_BOTH);
		gdPort.widthHint = 70;
		txtPort.setLayoutData(gdPort);
		if (config.props.get(Configuration.PROXY_PORT) != null) {
			if("".equals((String)config.props.get(Configuration.PROXY_PORT)))
				txtPort.setText((String) config.props.get(Configuration.PROXY_PORT));
			else
				txtPort.setText((String) config.props.get(Configuration.PROXY_PORT));
		}
		
		
		return comp;
	}

	public void init(IWorkbench workbench) {
		setPreferenceStore(MembraneUIPlugin.getDefault().getPreferenceStore());
	}

}
