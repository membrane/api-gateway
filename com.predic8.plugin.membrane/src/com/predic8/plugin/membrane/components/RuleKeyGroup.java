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
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;
import com.predic8.plugin.membrane.util.SWTUtil;

public class RuleKeyGroup {

	public static final String SELECTION_INPUT_CHANGED = "SelectionInputChanged";
	
	private Text textListenPort;

	private Combo comboRuleMethod;

	private Text textRuleHost;

	private Button btAnyPath;

	private Button btPathPattern;

	private Button btSubstring, btRegExp;

	private Text textRulePath;

	private Composite compPattern;

	private boolean dataChanged;
	
	private RuleKey ruleKey;
	
	public RuleKeyGroup(Composite parent, int style) {

		Group keyGroup = createKeyGroupe(parent);

		createWidgetLabel(keyGroup, "Host:");
		textRuleHost = createRuleHostText(keyGroup);

		createWidgetLabel(keyGroup, "Listen Port:");
		textListenPort = createListenPortText(keyGroup);

		createWidgetLabel(keyGroup, "HTTP Method:");
		comboRuleMethod = createRuleMethodCombo(keyGroup);

		btAnyPath = createAnyPathButton(keyGroup);

		addDummyLabel(keyGroup);

		btPathPattern = createPathPatternButton(keyGroup);

		textRulePath = createRulePathText(keyGroup);

		compPattern = createPatternComposite(keyGroup);

		Label lbInterpret = new Label(compPattern, SWT.NONE);
		GridData gridData4LbInterpret = new GridData(GridData.FILL_HORIZONTAL);
		lbInterpret.setLayoutData(gridData4LbInterpret);
		lbInterpret.setText("Interpret Pattern as");

		addDummyLabel(compPattern);

		btSubstring = createButtonSubstring();
		
		addDummyLabel(compPattern);

		GridData gridData4LbExample = new GridData();
		gridData4LbExample.horizontalIndent = 20;
		
		Label lbSubstringExample = new Label(compPattern, SWT.NONE);
		lbSubstringExample.setLayoutData(gridData4LbExample);
		lbSubstringExample.setText("Examples:        " + "/axis2/         matches all URIs containing /axis2/");

		Label lbSubstringExampleA = new Label(compPattern, SWT.NONE);
		lbSubstringExampleA.setText("");
		
		btRegExp = createButtonRegExp();
		
		addDummyLabel(compPattern);

		Label lbRefExpressExample = new Label(compPattern, SWT.NONE);
		lbRefExpressExample.setLayoutData(gridData4LbExample);
		lbRefExpressExample.setText("Examples:             " + ".*            matches any URI");

		new Label(compPattern, SWT.NONE).setText("");    //(".*   matches any URI");

		Label lbRefExpressExampleEmpty = new Label(compPattern, SWT.NONE);
		lbRefExpressExampleEmpty.setLayoutData(gridData4LbExample);
		lbRefExpressExampleEmpty.setText(".*FooService   matches any URI terminating with FooService");

	}

	private Button createButtonRegExp() {
		Button bt = new Button(compPattern, SWT.RADIO);
		bt.setText("Regular Expression");
		bt.setSelection(true);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!SELECTION_INPUT_CHANGED.equals(e.data)) {
					dataChanged = true;
				}
			}
		});
		return bt;
	}
	
	private Button createButtonSubstring() {
		Button bt = new Button(compPattern, SWT.RADIO);
		bt.setText("Substring");
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!SELECTION_INPUT_CHANGED.equals(e.data)) {
					dataChanged = true;
				}
			}
		});
		
		return bt;
	}
	
	private Button createPathPatternButton(Group keyGroup) {
		Button bt = new Button(keyGroup, SWT.RADIO);
		bt.setText("Path pattern");
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!SELECTION_INPUT_CHANGED.equals(e.data)) {
					dataChanged = true;
				}
				
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						textRulePath.setVisible(true);
						compPattern.setVisible(true);
					}
				});
			}
		});
		return bt;
	}

	private Button createAnyPathButton(Group keyGroup) {
		Button bt = new Button(keyGroup, SWT.RADIO);
		bt.setText("Any path");
		bt.setSelection(true);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!SELECTION_INPUT_CHANGED.equals(e.data)) {
					dataChanged = true;
				}

				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						textRulePath.setVisible(false);
						compPattern.setVisible(false);
					}
				});				
			}
		});
		return bt;
	}

	private Text createRulePathText(Group keyGroup) {
		Text text = new Text(keyGroup, SWT.BORDER);
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		text.setText(Router.getInstance().getRuleManager().getDefaultPath());
		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (ruleKey == null)
					return;
				
				Text t = (Text)e.widget;
				if (!t.getText().equals(ruleKey.getPath())) {
					dataChanged = true;
				}
			}
		});
		text.setVisible(false);
		return text;
	}

	private Combo createRuleMethodCombo(Group keyGroup) {
		Combo combo = new Combo(keyGroup, SWT.READ_ONLY);
		combo.setItems(new String[] { "POST", "GET", "DELETE", "PUT", "<<All methods>>" });
		GridData gData = new GridData();
		gData.widthHint = 100;
		combo.setLayoutData(gData);
		combo.select(Router.getInstance().getRuleManager().getDefaultMethod());
		combo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				dataChanged = true;
			}
		});
		
		return combo;
	}

	private Text createListenPortText(Group keyGroup) {
		Text text = new Text(keyGroup, SWT.BORDER);
		text.setText(""+ Router.getInstance().getRuleManager().getDefaultListenPort());
		text.addVerifyListener(new PortVerifyListener());
		text.addModifyListener(new ModifyListener() {	
			@Override
			public void modifyText(ModifyEvent e) {
				if (ruleKey == null)
					return;
				
				Text t = (Text)e.widget;
				if (!t.getText().equals("" + ruleKey.getPort())) {
					dataChanged = true;
				}
				
			}
		});
		
		GridData gData = new GridData();
		gData.widthHint = 100;
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		text.setLayoutData(gData);
		return text;
	}

	private Text createRuleHostText(Group keyGroup) {
		Text text = new Text(keyGroup, SWT.BORDER);
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		text.setText(Router.getInstance().getRuleManager().getDefaultHost());
		text.addModifyListener(new ModifyListener() {	
			@Override
			public void modifyText(ModifyEvent e) {
				if (ruleKey == null)
					return;
				
				Text t = (Text)e.widget;
				if (!t.getText().equals(ruleKey.getHost())) {
					dataChanged = true;
				}
				
			}
		});
		return text;
	}
	
	private void createWidgetLabel(Composite parent, String text) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(text);
		label.setLayoutData(new GridData());
	}

	private Composite createPatternComposite(Group keyGroup) {
		Composite composite = new Composite(keyGroup, SWT.NONE);
		GridData gData = new GridData();
		gData.horizontalSpan = 2;
		gData.grabExcessHorizontalSpace = true;
		composite.setLayoutData(gData);
		composite.setVisible(false);

		GridLayout layout = new GridLayout();
		layout.marginLeft = 25;
		layout.numColumns = 2;
		composite.setLayout(layout);
		return composite;
	}

	private Group createKeyGroupe(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText("Service Proxy Key");
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		group.setLayout(SWTUtil.createGridLayout(2, 5));
		return group;
	}

	public void clear() {
		textListenPort.setText("");
		textRulePath.setText("");
		textRuleHost.setText("");
		comboRuleMethod.clearSelection();
	}

	public ServiceProxyKey getUserInput() {
		if (textListenPort.getText().trim().length() == 0 || getMethod(comboRuleMethod.getSelectionIndex()).length() == 0) {
			return null;
		}

		if (btAnyPath.getSelection()) {
			ServiceProxyKey rulekey = new ServiceProxyKey(getHost(), getMethod(comboRuleMethod.getSelectionIndex()), ".*", getListenPort());
			rulekey.setUsePathPattern(false);
			return rulekey;
		}
		
		if (getPath().length() == 0) 
			return null;
		
		ServiceProxyKey rulekey = new ServiceProxyKey(getHost(), getMethod(comboRuleMethod.getSelectionIndex()), getPath(), getListenPort());
		rulekey.setUsePathPattern(true);
		rulekey.setPathRegExp(btRegExp.getSelection());
		
		return rulekey;

	}

	private String getHost() {
		return textRuleHost.getText().trim();
	}

	private String getPath() {
		return textRulePath.getText().trim();
	}

	private int getListenPort() {
		return Integer.parseInt(textListenPort.getText().trim());
	}

	private String getMethod(int index) {
		if (index < 0) {
			return "";
		} else {
			if (index == 4) {
				return "*";
			} else {
				return comboRuleMethod.getItem(index);
			}
		}
	}

	public void setInput(RuleKey ruleKey) {
		this.ruleKey = ruleKey;
		
		textListenPort.setText(Integer.toString(ruleKey.getPort()));

		setSelectionForMethodCombo(ruleKey);

		if (ruleKey.isUsePathPattern()) {
			btPathPattern.setSelection(true);
			btAnyPath.setSelection(false);
			btPathPattern.notifyListeners(SWT.Selection, SWTUtil.createSelectionEvent(SELECTION_INPUT_CHANGED, btPathPattern));
			if (ruleKey.isPathRegExp()) {
				btRegExp.setSelection(true);
				btRegExp.notifyListeners(SWT.Selection, SWTUtil.createSelectionEvent(SELECTION_INPUT_CHANGED, btRegExp));
				btSubstring.setSelection(false);
			} else {
				btSubstring.setSelection(true);
				btSubstring.notifyListeners(SWT.Selection, SWTUtil.createSelectionEvent(SELECTION_INPUT_CHANGED, btSubstring));
				btRegExp.setSelection(false);
			}
			textRulePath.setText(ruleKey.getPath());
		} else {
			btAnyPath.setSelection(true);
			btAnyPath.notifyListeners(SWT.Selection, SWTUtil.createSelectionEvent(SELECTION_INPUT_CHANGED, btAnyPath));
			btPathPattern.setSelection(false);
		}

		textRuleHost.setText(ruleKey.getHost());
	}

	private void setSelectionForMethodCombo(RuleKey ruleKey) {
		// do not fire selection listener here
		String method = ruleKey.getMethod().trim();
		if ("*".equals(method)) {
			comboRuleMethod.select(4);
		} else {
			String[] methods = comboRuleMethod.getItems();
			for (int i = 0; i < methods.length; i++) {
				if (method.equals(methods[i].trim())) {
					comboRuleMethod.select(i);
					break;
				}
			}
		}
	}
	
	private void addDummyLabel(Composite composite) {
		Label label = new Label(composite, SWT.NONE);
		GridData gData = new GridData(GridData.FILL_HORIZONTAL);
		gData.grabExcessHorizontalSpace = true;
		label.setLayoutData(gData);
		label.setText(" ");
	}

	public Text getTextListenPort() {
		return textListenPort;
	}

	public Combo getComboRuleMethod() {
		return comboRuleMethod;
	}

	public Text getTextRuleHost() {
		return textRuleHost;
	}

	public Text getTextRulePath() {
		return textRulePath;
	}

	public Button getBtAnyPath() {
		return btAnyPath;
	}

	public Button getBtPathPattern() {
		return btPathPattern;
	}

	public Button getBtSubstring() {
		return btSubstring;
	}

	public Button getBtRegExp() {
		return btRegExp;
	}
	
	public boolean isDataChanged() {
		return dataChanged;
	}

}