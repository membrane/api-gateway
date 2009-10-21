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
import com.predic8.membrane.core.Core;
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
		
		Configuration config = Core.getConfigurationManager().getConfiguration();
		
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
