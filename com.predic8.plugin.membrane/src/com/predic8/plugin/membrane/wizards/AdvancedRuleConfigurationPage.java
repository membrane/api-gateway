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
import com.predic8.plugin.membrane.components.PortVerifyListener;

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
		
		createHostText(ruleKeyGroup, gridData4HostTextField);
			
		Label lbListenPort = new Label(ruleKeyGroup, SWT.NONE);
		lbListenPort.setText("Listen Port:");
		GridData gridData4ListenPortLabel = new GridData();
		lbListenPort.setLayoutData(gridData4ListenPortLabel);
		
		GridData gridData4FieldShort = new GridData();
		gridData4FieldShort.widthHint = 100;
		
		createListenPortText(ruleKeyGroup, gridData4FieldShort);
		
		
		Label labelMethod = new Label(ruleKeyGroup, SWT.NONE);
		labelMethod.setText("HTTP Method:");
		GridData gridData4MethodLabel = new GridData();
		labelMethod.setLayoutData(gridData4MethodLabel);
	

		createMethodCombo(ruleKeyGroup, gridData4FieldShort);

		createAnyPathButton(ruleKeyGroup);
		
		Label lbDummy1 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridDataForLbDummy1 = new GridData(GridData.FILL_HORIZONTAL);
		gridDataForLbDummy1.grabExcessHorizontalSpace = true;
		lbDummy1.setLayoutData(gridDataForLbDummy1);
		lbDummy1.setText(" ");
		
		createPathPatternButton(ruleKeyGroup);
		
		createPathText(ruleKeyGroup, gridData4HostTextField);
		
		
		createPatternComposite(ruleKeyGroup);
		

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
		
		Label lbSubstringExampleA = new Label(compPattern, SWT.NONE);
		lbSubstringExampleA.setText("/axis2/     matches all URIs containing /axis2/");
		
		
		btRegEx = new Button(compPattern, SWT.RADIO);
		btRegEx.setText("Regular Expression");
		btRegEx.setSelection(true);
		
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
		lbRefExpressExampleB.setText(".*FooService   matches any URI terminating with FooService");
		
		setControl(composite);
	}

	private void createMethodCombo(Group ruleKeyGroup, GridData gridData4FieldShort) {
		comboMethod = new Combo(ruleKeyGroup, SWT.READ_ONLY);
		comboMethod.setItems(new String[] { "POST", "GET", "DELETE", "PUT", "<<All methods>>" });
		comboMethod.setLayoutData(gridData4FieldShort);
		comboMethod.select(Router.getInstance().getRuleManager().getDefaultMethod());
	}

	private void createAnyPathButton(Group ruleKeyGroup) {
		btAnyPath = new Button(ruleKeyGroup, SWT.RADIO);
		btAnyPath.setText("Any path");
		btAnyPath.setSelection(true);
		btAnyPath.addSelectionListener(new SelectionAdapter() {
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
	}

	private void createPathPatternButton(Group ruleKeyGroup) {
		btPathPattern = new Button(ruleKeyGroup, SWT.RADIO);
		btPathPattern.setText("Path pattern");
		btPathPattern.addSelectionListener(new SelectionAdapter() {
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
	}

	private void createPathText(Group ruleKeyGroup, GridData gridData4HostTextField) {
		textPath = new Text(ruleKeyGroup, SWT.BORDER);
		textPath.setLayoutData(gridData4HostTextField);
		textPath.setText(Router.getInstance().getRuleManager().getDefaultPath());
		textPath.setVisible(false);
		textPath.addModifyListener(new ModifyListener() {
			
			public void modifyText(ModifyEvent e) {
				if (textPath.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Path pattern must be specified");
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
		});
	}

	private void createPatternComposite(Group ruleKeyGroup) {
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
	}

	private void createListenPortText(Group ruleKeyGroup, GridData gridData4FieldShort) {
		textListenPort = new Text(ruleKeyGroup,SWT.BORDER);
		textListenPort.setText(Router.getInstance().getRuleManager().getDefaultListenPort());
		textListenPort.addVerifyListener(new PortVerifyListener());
		textListenPort.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		textListenPort.setLayoutData(gridData4FieldShort);
		textListenPort.addModifyListener(new ModifyListener() {
			
			public void modifyText(ModifyEvent e) {
				if (textListenPort.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Listen port must be specified");
				} else if (textListenPort.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(textListenPort.getText()) > 65535) {
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
	}

	private void createHostText(Group ruleKeyGroup, GridData gridData4HostTextField) {
		textHost = new Text(ruleKeyGroup, SWT.BORDER);
		textHost.setLayoutData(gridData4HostTextField);
		textHost.setText(Router.getInstance().getRuleManager().getDefaultHost());
		textHost.addModifyListener(new ModifyListener() {
			
			public void modifyText(ModifyEvent e) {
				if (textHost.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Client host must be specified");
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
		});
	}

	private Group createRuleKeyGroup(Composite composite) {
		Group ruleKeyGroup = new Group(composite, SWT.NONE);
		ruleKeyGroup.setText("Rule Key");
		ruleKeyGroup.setLayoutData(new GridData( GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4Group = new GridLayout();
		gridLayout4Group.numColumns = 2;
		ruleKeyGroup.setLayout(gridLayout4Group);
		return ruleKeyGroup;
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
		else
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
			if (((HttpTransport) Router.getInstance().getTransport()).isAnyThreadListeningAt(Integer.parseInt(textListenPort.getText()))) {
				return true;
			}
			new ServerSocket(Integer.parseInt(textListenPort.getText())).close();
			return true;
		} catch (IOException ex) {
			setErrorMessage("Port is already in use. Please choose a different port!");
			return false;
		} 
		
		
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
