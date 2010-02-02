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

package com.predic8.plugin.membrane.wizards;

import java.io.IOException;
import java.net.ServerSocket;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
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
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;

public class AdvancedRuleConfigurationPage extends WizardPage {

	public static final String PAGE_NAME = "Advanced Rule Configuration";
	
	private Text textListenPort;

	private Combo comboMethod;

	private Text textHost;
	
	private Button btAnyPath;
	
	private Button btPathPattern;

	private Button btSubstring, btRegEx;
	
	private Text textPath;
	
	private Composite compPattern;
		
	protected AdvancedRuleConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Advanced Rule");
		setDescription("Specify all rule configuration parameters");
	}

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginTop = 10;
		layout.marginLeft = 2;
		layout.marginBottom = 10;
		layout.marginRight = 10;
		layout.verticalSpacing = 20;
		composite.setLayout(layout);
		
		
		Group ruleKeyGroup = createRuleKeyGroup(composite);
		
		Label ruleOptionsHostLabel = new Label(ruleKeyGroup, SWT.NONE);
		ruleOptionsHostLabel.setText("Client Host:");
		ruleOptionsHostLabel.setLayoutData(new GridData());
		

		GridData gridData4HostTextField = new GridData(GridData.FILL_HORIZONTAL);
		
		textHost = createHostText(ruleKeyGroup, gridData4HostTextField);
			
		Label lbListenPort = new Label(ruleKeyGroup, SWT.NONE);
		lbListenPort.setText("Listen Port:");
		GridData gridData4ListenPortLabel = new GridData();
		lbListenPort.setLayoutData(gridData4ListenPortLabel);
		
		GridData gridData4FieldShort = new GridData();
		gridData4FieldShort.widthHint = 100;
		
		textListenPort = createListenPortText(ruleKeyGroup, gridData4FieldShort);
		
		
		Label labelMethod = new Label(ruleKeyGroup, SWT.NONE);
		labelMethod.setText("HTTP Method:");
		GridData gridData4MethodLabel = new GridData();
		labelMethod.setLayoutData(gridData4MethodLabel);
	

		comboMethod = createMethodCombo(ruleKeyGroup, gridData4FieldShort);

		btAnyPath = createAnyPathButton(ruleKeyGroup);
		btAnyPath.setSelection(true);
		Label lbDummy1 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridDataForLbDummy1 = new GridData(GridData.FILL_HORIZONTAL);
		gridDataForLbDummy1.grabExcessHorizontalSpace = true;
		lbDummy1.setLayoutData(gridDataForLbDummy1);
		lbDummy1.setText(" ");
		
		btPathPattern = createPathPatternButton(ruleKeyGroup);
		
		textPath = createPathText(ruleKeyGroup, gridData4HostTextField);
		
		
		compPattern = createPatternComposite(ruleKeyGroup);
		

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
		gridData4LbExample.horizontalIndent = 20;
		gridData4LbExample.widthHint = 80;
		lbSubstringExample.setLayoutData(gridData4LbExample);
		lbSubstringExample.setText("Examples: ");
		
		new Label(compPattern, SWT.NONE).setText("/axis2/     matches all URIs containing /axis2/");
		
		
		btRegEx = new Button(compPattern, SWT.RADIO);
		btRegEx.setText("Regular Expression");
		btRegEx.setSelection(true);
		
		Label lbDummy5 = new Label(compPattern, SWT.NONE);
		lbDummy5.setLayoutData(gridDataForLbDummy1);
		lbDummy5.setText(" ");
		
		Label lbRefExpressExample = new Label(compPattern, SWT.NONE);
		lbRefExpressExample.setLayoutData(gridData4LbExample);
		lbRefExpressExample.setText("Examples: ");
	
		new Label(compPattern, SWT.NONE).setText(".*   matches any URI");
		
		
		Label lbRefExpressExampleEmpty = new Label(compPattern, SWT.NONE);
		lbRefExpressExampleEmpty.setLayoutData(gridData4LbExample);
		lbRefExpressExampleEmpty.setText("         ");
	
		new Label(compPattern, SWT.NONE).setText(".*FooService   matches any URI terminating with FooService");
		
		setControl(composite);
	}

	private Combo createMethodCombo(Group ruleKeyGroup, GridData gridData4FieldShort) {
		Combo combo = new Combo(ruleKeyGroup, SWT.READ_ONLY);
		combo.setItems(new String[] { "POST", "GET", "DELETE", "PUT", "<<All methods>>" });
		combo.setLayoutData(gridData4FieldShort);
		combo.select(Router.getInstance().getRuleManager().getDefaultMethod());
		return combo;
	}

	private Button createAnyPathButton(Group ruleKeyGroup) {
		Button bt = new Button(ruleKeyGroup, SWT.RADIO);
		bt.setText("Any path");
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						textPath.setVisible(false);
						compPattern.setVisible(false);
					}
				});
			}
		});
		return bt;
	}

	private Button createPathPatternButton(Group ruleKeyGroup) {
		Button bt = new Button(ruleKeyGroup, SWT.RADIO);
		bt.setText("Path pattern");
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						textPath.setVisible(true);
						compPattern.setVisible(true);
					}
				});
			}
		});
		return bt;
	}

	private Text createPathText(Group ruleKeyGroup, GridData gridData4HostTextField) {
		final Text text = new Text(ruleKeyGroup, SWT.BORDER);
		text.setLayoutData(gridData4HostTextField);
		text.setText(Router.getInstance().getRuleManager().getDefaultPath());
		text.setVisible(false);
		text.addModifyListener(new ModifyListener() {
			
			public void modifyText(ModifyEvent e) {
				if (text.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Path pattern must be specified");
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
		});
		return text;
	}

	private Composite createPatternComposite(Group ruleKeyGroup) {
		Composite composite = new Composite(ruleKeyGroup, SWT.NONE);
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

	private Text createListenPortText(Group ruleKeyGroup, GridData gridData4FieldShort) {
		final Text text = new Text(ruleKeyGroup,SWT.BORDER);
		text.setText(Router.getInstance().getRuleManager().getDefaultListenPort());
		text.addVerifyListener(new PortVerifyListener());
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		text.setLayoutData(gridData4FieldShort);
		text.addModifyListener(new ModifyListener() {
			
			public void modifyText(ModifyEvent e) {
				if (text.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Listen port must be specified");
				} else if (text.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(text.getText()) > 65535) {
							setErrorMessage("Listen port number has an upper bound 65535.");
							setPageComplete(false);
						}
					} catch (NumberFormatException nfe) {
						setErrorMessage("Specified listen port must be in decimal number format.");
						setPageComplete(false);
					}
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
		});
		return text;
	}

	private Text createHostText(Group ruleKeyGroup, GridData gridData4HostTextField) {
		final Text text = new Text(ruleKeyGroup, SWT.BORDER);
		text.setLayoutData(gridData4HostTextField);
		text.setText(Router.getInstance().getRuleManager().getDefaultHost());
		text.addModifyListener(new ModifyListener() {
			
			public void modifyText(ModifyEvent e) {
				if (text.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Client host must be specified");
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
		});
		return text;
	}

	private Group createRuleKeyGroup(Composite composite) {
		Group group = new Group(composite, SWT.NONE);
		group.setText("Rule Key");
		group.setLayoutData(new GridData( GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4Group = new GridLayout();
		gridLayout4Group.numColumns = 2;
		group.setLayout(gridLayout4Group);
		return group;
	}

	public String getListenPort() {
		return textListenPort.getText();
	}
	

	public String getListenHost() {
		return textHost.getText();
	}
	
	public String getMethod() {
		int index = comboMethod.getSelectionIndex();		
		if (index == 4) 
			return "*";
		if (index > -1)
			return comboMethod.getItem(index);
		return "";
	}
	
	@Override
	public IWizardPage getNextPage() {
		return getWizard().getPage(TargetHostConfigurationPage.PAGE_NAME);
	}
	
	@Override
	public boolean canFlipToNextPage() {
		if (!isPageComplete())
			return false;
		try {
			if (getTransport().isAnyThreadListeningAt(Integer.parseInt(textListenPort.getText()))) {
				return true;
			}
			new ServerSocket(Integer.parseInt(textListenPort.getText())).close();
			return true;
		} catch (IOException ex) {
			setErrorMessage("Port is already in use. Please choose a different port!");
			return false;
		} 
		
		
	}

	private HttpTransport getTransport() {
		return ((HttpTransport) Router.getInstance().getTransport());
	}

	public boolean getUsePathPatter() {
		return btPathPattern.getSelection();
	}
		
	public boolean isRegExp() {
		return btRegEx.getSelection();
	}
	
	public String getPathPattern() {
		return textPath.getText();
	}
}
