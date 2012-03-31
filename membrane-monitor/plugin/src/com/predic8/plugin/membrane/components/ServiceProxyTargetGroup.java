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

package com.predic8.plugin.membrane.components;

import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;
import com.predic8.plugin.membrane.util.SWTUtil;

public class ServiceProxyTargetGroup {

	private Text textTargetPort;

	private Text textTargetHost;

	Pattern pHost = Pattern.compile("^([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])(\\.([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9]))*$");

	Pattern pIp = Pattern.compile("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z]|[A-Za-z][A-Za-z0-9\\-]*[A-Za-z0-9])$");
	
	private boolean dataChanged;
	
	private String originalTargetHost;
	
	private String originalTargetPort;
	
	public ServiceProxyTargetGroup(Composite parent, int style) {
		Group group = createGroup(parent, style);

		
		Label label = createSpanLabel(group);
		label.setText("Membrane Monitor will forward the messages to the host on the specified port");
		
		createSpanLabel(group).setText(" ");
		
		new Label(group, SWT.NONE).setText("Host");

		textTargetHost = createTargetHostText(group);

		createDummyLabel(group).setText(" ");
		
		createDummyLabel(group).setText(" ");
		
		new Label(group, SWT.NONE).setText("Port");

		textTargetPort = createTargetPortText(group);
		
		createDummyLabel(group).setText(" ");
		createDummyLabel(group).setText(" ");
		
	}

	private Label createSpanLabel(Composite parent) {
		Label label = new Label(parent, SWT.NONE);
		GridData gData = SWTUtil.getGreedyHorizontalGridData();
		gData.horizontalSpan = 4;
		gData.verticalSpan = 2;
		label.setLayoutData(gData);
		return label;
	}
	
	private Text createTargetHostText(Group group) {
		Text text = new Text(group, SWT.BORDER);
		text.setText(Router.getInstance().getRuleManager().getDefaultTargetHost());
		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (originalTargetHost == null)
					return;
				
				Text t = (Text)e.widget;
				if (!t.getText().equals(originalTargetHost)) {
					dataChanged = true;
				}
			}
		});
		
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		return text;
	}

	private Text createTargetPortText(Group group) {
		Text text = new Text(group,SWT.BORDER);
		text.setText("" + Router.getInstance().getRuleManager().getDefaultTargetPort());
		text.addVerifyListener(new PortVerifyListener());
		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (originalTargetPort == null)
					return;
				
				Text t = (Text)e.widget;
				if (!t.getText().equals(originalTargetPort)) {
					dataChanged = true;
				}
			}
		});
		
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		return text;
	}

	private Group createGroup(Composite parent, int style) {
		Group group = new Group(parent, style);
		group.setText("Target");
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		group.setLayout(SWTUtil.createGridLayout(4, 5));
		return group;
	}

	private Label createDummyLabel(Composite parent) {
		Label lb = new Label(parent, SWT.NONE);
		lb.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		return lb;
	}

	public void clear() {
		textTargetHost.setText("");
		textTargetPort.setText("");
	}

	public String getTargetHost() {
		return textTargetHost.getText().trim();
	}

	public String getTargetPort() {
		return textTargetPort.getText().trim();
	}

	public void setTargetHost(String host) {
		originalTargetHost = host;
		textTargetHost.setText(host);
	}

	public void setTargetPort(String port) {
		originalTargetPort = port;
		textTargetPort.setText(port);
	}

	public void setTargetPort(int port) {
		originalTargetPort = "" + port;
		textTargetPort.setText(Integer.toString(port));
	}
	
	public Text getTextTargetHost() {
		return textTargetHost;
	}
	
	public Text getTextTargetPort() {
		return textTargetPort;
	}
	
	public boolean isValidHostNameInput() {
		String txt = textTargetHost.getText();
		if ("".equals(txt))
			return false;

		if ("localhost".equals(txt))
			return true;

		return pHost.matcher(txt).matches() || pIp.matcher(txt).matches();
	}
	
	public boolean isDataChanged() {
		return dataChanged;
	}
}