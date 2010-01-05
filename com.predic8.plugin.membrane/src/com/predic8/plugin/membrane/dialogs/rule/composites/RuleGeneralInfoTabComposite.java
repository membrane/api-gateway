package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class RuleGeneralInfoTabComposite extends Composite {

	private Text textRuleName;
	
	public RuleGeneralInfoTabComposite(Composite parent) {
		super(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.marginTop = 20;
		gridLayout.marginLeft = 20;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 20;
		setLayout(gridLayout);
		
		Label nameLabel = new Label(this, SWT.NONE);
		nameLabel.setText("Rule Name: ");
		
		textRuleName = new Text(this, SWT.BORDER);
		GridData gridData4NameText = new GridData();
		gridData4NameText.widthHint = 150;
		textRuleName.setLayoutData(gridData4NameText);
	}

	public void setRuleName(String ruleName) {
		if (ruleName == null)
			return;
		textRuleName.setText(ruleName);
	}
	
	public String getRuleName() {
		return textRuleName.getText().trim();
	}
}
