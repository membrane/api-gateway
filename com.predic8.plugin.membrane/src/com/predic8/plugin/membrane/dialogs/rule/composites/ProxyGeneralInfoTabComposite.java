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
package com.predic8.plugin.membrane.dialogs.rule.composites;

import java.net.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.util.SWTUtil;

public class ProxyGeneralInfoTabComposite extends AbstractProxyFeatureComposite {

	private Text textRuleName;
	
	private Button btInterfaces; 
	
	private Combo comboInterfaces;
	
	private String[] interfaces;
	
	
	
	public ProxyGeneralInfoTabComposite(Composite parent) {
		super(parent);
		setLayout(SWTUtil.createGridLayout(3, 20));
		
		initInterfaces();
		
		createRuleNameLabel(this);
		createRuleNameText(this);
		
		createDummyLabel(this);
		createDummyLabel(this);
		
		createInterfacesButton(this);
		
		createWrapLabel(this);
		
		Composite comp = createInterfacesComposite(this);
		createinterfacesLabel(comp);
		createInterfacesCombo(comp);
		
	}

	private Composite createInterfacesComposite(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(3, false));
		GridData gd = new GridData();
		gd.horizontalSpan = 3;
		comp.setLayoutData(gd);
		return comp;
	}
	
	private void createinterfacesLabel(Composite parent) {
		Label lb = new Label(parent, SWT.NONE);
		lb.setText("Interfaces: ");
		GridData gd = new GridData();
		gd.horizontalIndent = 22;
		lb.setLayoutData(gd);
	}
	
	private void createRuleNameLabel(Composite parent) {
		Label lb = new Label(parent, SWT.NONE);
		lb.setText("Rule Name: ");
		GridData gd = new GridData();
		gd.horizontalSpan = 2;
		lb.setLayoutData(gd);
	}
	
	@Override
	public void setRule(final Rule rule) {
		super.setRule(rule);
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				textRuleName.setText(rule.getName());
				btInterfaces.setSelection(rule.getLocalHost() != null);
				btInterfaces.notifyListeners(SWT.Selection, createSelectionEvent(SELECTION_INPUT_CHANGED));
				if (comboInterfaces.isEnabled()) {
					int idx = comboInterfaces.indexOf(rule.getLocalHost());
					comboInterfaces.select(idx);
				}
			}
		});
	}
	
	private void createRuleNameText(Composite parent) {
		textRuleName = new Text(parent, SWT.BORDER);
		GridData gd = new GridData();
		gd.widthHint = 150;
		textRuleName.setLayoutData(gd);
		textRuleName.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				Text t = (Text)e.widget;
				if (!t.getText().equals(rule.getName())) {
					dataChanged = true;
				}
			}
		});
	}
	
	public String getRuleName() {
		return textRuleName.getText().trim();
	}
	
	public String getLocalHost() {
		if (!comboInterfaces.isEnabled())
			return null;
		
		return comboInterfaces.getItem(comboInterfaces.getSelectionIndex());
	}
	
	private void createInterfacesButton(Composite parent) {
		btInterfaces = new Button(parent, SWT.CHECK);
		GridData gd = new GridData();
		gd.horizontalSpan = 1;
		btInterfaces.setLayoutData(gd);
		
		btInterfaces.setEnabled(interfaces.length > 1);
		
		btInterfaces.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				comboInterfaces.setEnabled(btInterfaces.getSelection());
				//distinguish between selection event when rule is set and user clicked
				if (!SELECTION_INPUT_CHANGED.equals(e.data)) {
					dataChanged = true;
				}
			}
		});
		
	}
	
	private void createWrapLabel(Composite parent) {
		Label lb = new Label(parent, SWT.WRAP);
		lb.setText("If the host has multiple interfaces you can select an interface for outgoing connections.");
		GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1);
		gd.horizontalIndent = 10;
		lb.setLayoutData(gd);
	}
	
	private void createDummyLabel(Composite parent) {
		Label lb = new Label(parent, SWT.NONE);
		lb.setText("");
		GridData gd = new GridData();
		gd.horizontalSpan = 3;
		lb.setLayoutData(gd);
	}
	
	private void createInterfacesCombo(Composite parent) {
		comboInterfaces = new Combo(parent, SWT.READ_ONLY);
		try {
			comboInterfaces.setItems(interfaces);
			if (comboInterfaces.getItemCount() > 0)
				comboInterfaces.select(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
		comboInterfaces.setEnabled(false);
		comboInterfaces.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				dataChanged = true;
			}
		});
	}
	
	@SuppressWarnings("rawtypes")
	private void initInterfaces() {
		List<String> list = new ArrayList<String>();
		try {
			Enumeration ifEnum = NetworkInterface.getNetworkInterfaces();
			while(ifEnum.hasMoreElements()) {
				 NetworkInterface localIf = (NetworkInterface)(ifEnum.nextElement()); 
				 Enumeration ifAddrEnum = localIf.getInetAddresses(); 
				 while(ifAddrEnum.hasMoreElements()) { 
					 InetAddress ifAddr = (InetAddress)(ifAddrEnum.nextElement());
					 list.add(ifAddr.getHostAddress());
	             }
			}
		} catch (SocketException e) {
			e.printStackTrace();
		} 
		interfaces = list.toArray(new String[]{});	
	}
	
	@Override
	public String getTitle() {
		return "General";
	}
	
	@Override
	public void commit() {
		if (rule == null)
			return;
		
		rule.setName(getRuleName());
		rule.setLocalHost(getLocalHost());
	}
	
}
