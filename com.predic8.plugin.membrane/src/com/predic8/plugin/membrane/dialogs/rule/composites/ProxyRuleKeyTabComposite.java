package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.rules.RuleKey;

public class ProxyRuleKeyTabComposite extends Composite {

	protected Text textListenPort;
	
	public ProxyRuleKeyTabComposite(Composite parent) {
		super(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.marginTop = 20;
		gridLayout.marginLeft = 20;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 20;
		setLayout(gridLayout);
		
		Label lbListenPort = new Label(this, SWT.NONE);
		lbListenPort.setText("Listen Port: ");
		
		textListenPort = new Text(this, SWT.BORDER);
		GridData gridData4PortText = new GridData();
		gridData4PortText.widthHint = 150;
		textListenPort.setLayoutData(gridData4PortText);
	}

	public void setInput(RuleKey ruleKey) {
		if (ruleKey == null)
			return;
		textListenPort.setText(Integer.toString(ruleKey.getPort()));
	}
	
	public String getListenPort() {
		return textListenPort.getText().trim();
	}
}
