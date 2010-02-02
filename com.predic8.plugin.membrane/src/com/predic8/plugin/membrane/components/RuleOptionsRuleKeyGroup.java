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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;

public class RuleOptionsRuleKeyGroup {

	private Text textListenPort;

	private Combo ruleMethodCombo;

	private Text ruleOptionsHostTextField;

	private Button btAnyPath;

	private Button btPathPattern;

	private Button btSubstring, btRegularExpression;

	private Text rulePathTextField;

	private Composite compPattern;

	public RuleOptionsRuleKeyGroup(Composite parent, int style) {

		Group ruleKeyGroup = new Group(parent, style);
		
		ruleKeyGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4Group = new GridLayout();
		gridLayout4Group.numColumns = 2;
		ruleKeyGroup.setLayout(gridLayout4Group);

		Label ruleOptionsHostLabel = new Label(ruleKeyGroup, SWT.NONE);
		ruleOptionsHostLabel.setText("Client Host:");

		GridData gridData4ListenHostLabel = new GridData();
		ruleOptionsHostLabel.setLayoutData(gridData4ListenHostLabel);

		ruleOptionsHostTextField = new Text(ruleKeyGroup, SWT.BORDER);
		GridData gridData4HostTextField = new GridData(GridData.FILL_HORIZONTAL);
		ruleOptionsHostTextField.setLayoutData(gridData4HostTextField);
		ruleOptionsHostTextField.setText(Router.getInstance().getRuleManager().getDefaultHost());

		Label lbListenPort = new Label(ruleKeyGroup, SWT.NONE);
		lbListenPort.setText("Listen Port:");
		GridData gridData4ListenPortLabel = new GridData();
		lbListenPort.setLayoutData(gridData4ListenPortLabel);

		textListenPort = new Text(ruleKeyGroup, SWT.BORDER);
		textListenPort.setText(Router.getInstance().getRuleManager().getDefaultListenPort());
		textListenPort.addVerifyListener(new PortVerifyListener());
		GridData gridData4FieldShort = new GridData();
		gridData4FieldShort.widthHint = 100;
		textListenPort.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		textListenPort.setLayoutData(gridData4FieldShort);

		Label ruleOptionsMethodLabel = new Label(ruleKeyGroup, SWT.NONE);
		ruleOptionsMethodLabel.setText("HTTP Method:");
		GridData gridData4MethodLabel = new GridData();
		ruleOptionsMethodLabel.setLayoutData(gridData4MethodLabel);

		ruleMethodCombo = new Combo(ruleKeyGroup, SWT.READ_ONLY);
		ruleMethodCombo.setItems(new String[] { "POST", "GET", "DELETE", "PUT", "<<All methods>>" });
		ruleMethodCombo.setLayoutData(gridData4FieldShort);
		ruleMethodCombo.select(Router.getInstance().getRuleManager().getDefaultMethod());

		btAnyPath = new Button(ruleKeyGroup, SWT.RADIO);
		btAnyPath.setText("Any path");
		btAnyPath.setSelection(true);
		btAnyPath.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						rulePathTextField.setVisible(false);
						compPattern.setVisible(false);
					}
				});
			}
		});

		Label lbDummy1 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridDataForLbDummy1 = new GridData(GridData.FILL_HORIZONTAL);
		gridDataForLbDummy1.grabExcessHorizontalSpace = true;
		lbDummy1.setLayoutData(gridDataForLbDummy1);
		lbDummy1.setText(" ");

		btPathPattern = new Button(ruleKeyGroup, SWT.RADIO);
		btPathPattern.setText("Path pattern");
		btPathPattern.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						rulePathTextField.setVisible(true);
						compPattern.setVisible(true);
					}
				});
			}
		});

		rulePathTextField = new Text(ruleKeyGroup, SWT.BORDER);
		rulePathTextField.setLayoutData(gridData4HostTextField);
		rulePathTextField.setText(Router.getInstance().getRuleManager().getDefaultPath());
		rulePathTextField.setVisible(false);

		compPattern = new Composite(ruleKeyGroup, SWT.NONE);
		GridData gdCompPattern = new GridData();
		gdCompPattern.horizontalSpan = 2;
		gdCompPattern.grabExcessHorizontalSpace = true;
		compPattern.setLayoutData(gdCompPattern);
		compPattern.setVisible(false);

		GridLayout layoutPattern = new GridLayout();
		layoutPattern.marginLeft = 25;
		layoutPattern.numColumns = 2;
		compPattern.setLayout(layoutPattern);

		Label lbInterpret = new Label(compPattern, SWT.NONE);
		GridData gridData4LbInterpret = new GridData(GridData.FILL_HORIZONTAL);
		lbInterpret.setLayoutData(gridData4LbInterpret);
		lbInterpret.setText("Interpret Pattern as");

		Label lbDummy3 = new Label(compPattern, SWT.NONE);
		lbDummy3.setLayoutData(gridDataForLbDummy1);
		lbDummy3.setText(" ");

		btSubstring = new Button(compPattern, SWT.RADIO);
		btSubstring.setText("Substring");

		Label lbDummy4 = new Label(compPattern, SWT.NONE);
		lbDummy4.setLayoutData(gridDataForLbDummy1);
		lbDummy4.setText(" ");

		Label lbSubstringExample = new Label(compPattern, SWT.NONE);
		GridData gridData4LbExample = new GridData();
		gridData4LbExample.horizontalIndent = 30;
		lbSubstringExample.setLayoutData(gridData4LbExample);
		lbSubstringExample.setText("Examples: ");

		Label lbSubstringExampleA = new Label(compPattern, SWT.NONE);
		lbSubstringExampleA.setText("/axis2/     matches all URI containing /axis2/");
		// lbSubstringExampleA.setLayoutData(gridDataForLbDummy1);

		btRegularExpression = new Button(compPattern, SWT.RADIO);
		btRegularExpression.setText("Regular Expression");
		btRegularExpression.setSelection(true);

		Label lbDummy5 = new Label(compPattern, SWT.NONE);
		lbDummy5.setLayoutData(gridDataForLbDummy1);
		lbDummy5.setText(" ");

		Label lbRefExpressExample = new Label(compPattern, SWT.NONE);
		lbRefExpressExample.setLayoutData(gridData4LbExample);
		lbRefExpressExample.setText("Examples: ");

		Label lbRefExpressExampleA = new Label(compPattern, SWT.NONE);
		lbRefExpressExampleA.setText(".*   matches any URI");

		Label lbRefExpressExampleEmpty = new Label(compPattern, SWT.NONE);
		lbRefExpressExampleEmpty.setLayoutData(gridData4LbExample);
		lbRefExpressExampleEmpty.setText("         ");

		Label lbRefExpressExampleB = new Label(compPattern, SWT.NONE);
		lbRefExpressExampleB.setText(".*FooService   matches any URI terminating");

	}

	public void clear() {
		textListenPort.setText("");
		rulePathTextField.setText("");
		ruleOptionsHostTextField.setText("");
		ruleMethodCombo.clearSelection();
	}

	public ForwardingRuleKey getUserInput() {
		if (textListenPort.getText().trim().length() == 0 || getMethod(ruleMethodCombo.getSelectionIndex()).length() == 0) {
			return null;
		}

		if (btAnyPath.getSelection()) {
			ForwardingRuleKey rulekey = new ForwardingRuleKey(getHost(), getMethod(ruleMethodCombo.getSelectionIndex()), ".*", getListenPort());
			rulekey.setUsePathPattern(false);
			return rulekey;
		}
		
		if (getPath().length() == 0) 
			return null;
		
		ForwardingRuleKey rulekey = new ForwardingRuleKey(getHost(), getMethod(ruleMethodCombo.getSelectionIndex()), getPath(), getListenPort());
		rulekey.setUsePathPattern(true);
		rulekey.setPathRegExp(btRegularExpression.getSelection());
		
		return rulekey;

	}

	private String getHost() {
		return ruleOptionsHostTextField.getText().trim();
	}

	private String getPath() {
		return rulePathTextField.getText().trim();
	}

	private int getListenPort() {
		return Integer.parseInt(textListenPort.getText().trim());
	}

	private String getMethod(int index) {
		String method;
		if (index < 0) {
			method = "";
		} else {
			if (index == 4) {
				method = "*";
			} else {
				method = ruleMethodCombo.getItem(index);
			}
		}
		return method;
	}

	public void setInput(RuleKey ruleKey) {
		textListenPort.setText(Integer.toString(ruleKey.getPort()));

		String method = ruleKey.getMethod();
		if ("*".equals(method.trim())) {
			ruleMethodCombo.select(4);
		} else {
			String[] methods = ruleMethodCombo.getItems();
			for (int i = 0; i < methods.length; i++) {
				if (method.trim().equals(methods[i].trim())) {
					ruleMethodCombo.select(i);
					break;
				}
			}
		}

		if (ruleKey.isUsePathPattern()) {
			btPathPattern.setSelection(true);
			btAnyPath.setSelection(false);
			btPathPattern.notifyListeners(SWT.Selection, null);
			if (ruleKey.isPathRegExp()) {
				btRegularExpression.setSelection(true);
				btRegularExpression.notifyListeners(SWT.Selection, null);
				btSubstring.setSelection(false);
			} else {
				btSubstring.setSelection(true);
				btSubstring.notifyListeners(SWT.Selection, null);
				btRegularExpression.setSelection(false);
			}
			rulePathTextField.setText(ruleKey.getPath());
		} else {
			btAnyPath.setSelection(true);
			btAnyPath.notifyListeners(SWT.Selection, null);
			btPathPattern.setSelection(false);
		}

		ruleOptionsHostTextField.setText(ruleKey.getHost());
	}

}