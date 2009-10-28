package com.predic8.plugin.membrane.wizards;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class RuleTypeSelectionPage extends WizardPage {

	public static final String PAGE_NAME = "Type Selection";
	
	private Button btSimpleRule;
	
	private Button btAdvancedRule;
	
	private Button btProxyRule;
	
	protected RuleTypeSelectionPage() {
		super(PAGE_NAME);
		setTitle("Rule Type Selection");
	}

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 2;
		gridLayout.marginBottom = 10;
		gridLayout.marginRight = 10;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);
		
		
		Label labelFullDescription = new Label(composite, SWT.WRAP);
		labelFullDescription.setText("Create rule to forward  HTTP and SOAP over HTTP requests.");
		labelFullDescription.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabel = new GridData();
		gridData4ListenDescrLabel.horizontalSpan = 2;
		gridData4ListenDescrLabel.verticalSpan = 1;
		labelFullDescription.setLayoutData(gridData4ListenDescrLabel);
		
		btSimpleRule = new Button(composite, SWT.RADIO);
		btSimpleRule.setText("Simple Forwarding Rule");
		GridData btSimpleGridData = new GridData();
		btSimpleGridData.grabExcessHorizontalSpace = true;
		btSimpleRule.setLayoutData(btSimpleGridData);
		
		
		
		Label labelGap = new Label(composite, SWT.WRAP);
		labelGap.setText(" ");
		labelGap.setBounds(120, 10, 100, 100);
		
		GridData gridData4LabelGap = new GridData();
		gridData4LabelGap.horizontalSpan = 2;
		gridData4LabelGap.verticalSpan = 3;
		labelGap.setLayoutData(gridData4LabelGap);
		
		
		
		
		Label labelFullDescriptionAdvanced = new Label(composite, SWT.WRAP);
		labelFullDescriptionAdvanced.setText("Offers all available options for rules like virtual host,\nHTTP method and request URL.");
		labelFullDescriptionAdvanced.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabelAdvanced = new GridData();
		gridData4ListenDescrLabelAdvanced.horizontalSpan = 2;
		gridData4ListenDescrLabelAdvanced.verticalSpan = 2;
		labelFullDescriptionAdvanced.setLayoutData(gridData4ListenDescrLabelAdvanced);
		
		btAdvancedRule = new Button(composite, SWT.RADIO);
		btAdvancedRule.setText("Advanced Forwarding Rule");
		GridData btAdvancedGridData = new GridData();
		btAdvancedGridData.grabExcessHorizontalSpace = true;
		btAdvancedRule.setLayoutData(btAdvancedGridData);
		
		
		
		
		
		Label labelGap1 = new Label(composite, SWT.WRAP);
		labelGap1.setText(" ");
		labelGap1.setBounds(120, 10, 100, 100);
		
		GridData gridData4LabelGap1 = new GridData();
		gridData4LabelGap1.horizontalSpan = 2;
		gridData4LabelGap1.verticalSpan = 3;
		labelGap1.setLayoutData(gridData4LabelGap1);
		
		
		
		Label labelFullDescriptionProxy = new Label(composite, SWT.WRAP);
		labelFullDescriptionProxy.setText("Works like a regular HTTP Proxy.\nCan a proxy SOAP and HTTP requests.");
		labelFullDescriptionProxy.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabelProxy = new GridData();
		gridData4ListenDescrLabelProxy.horizontalSpan = 2;
		gridData4ListenDescrLabelProxy.verticalSpan = 2;
		labelFullDescriptionProxy.setLayoutData(gridData4ListenDescrLabelProxy);
		
		btProxyRule = new Button(composite, SWT.RADIO);
		btProxyRule.setText("HTTP  Proxy Rule");
		GridData btProxyGridData = new GridData();
		btProxyGridData.grabExcessHorizontalSpace = true;
		btProxyRule.setLayoutData(btProxyGridData);
		
		
		
		setControl(composite);
	}

	@Override
	public IWizardPage getNextPage() {
		if (btSimpleRule.getSelection()) {
			return getWizard().getPage(ListenPortConfigurationPage.PAGE_NAME);
		} else if (btAdvancedRule.getSelection()) {
			return getWizard().getPage(AdvancedRuleConfigurationPage.PAGE_NAME);
		}
		return getWizard().getPage(ProxyRuleConfigurationPage.PAGE_NAME);
	}
	
}
