package com.predic8.plugin.membrane.viewers;

import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.components.RuleOptionsActionsGroup;
import com.predic8.plugin.membrane.components.RuleOptionsBlockComp;
import com.predic8.plugin.membrane.dialogs.AbstractRuleViewer;

public class ProxyRuleViewer extends AbstractRuleViewer {

	private Text textListenPort;
	
	public ProxyRuleViewer(Composite parent, int style) {
		super(parent);
		
		Composite portComposite = new Composite(this, SWT.NONE);
		GridLayout gridLayoutForPortComposite = new GridLayout();
		gridLayoutForPortComposite.numColumns = 2;
		portComposite.setLayout(gridLayoutForPortComposite);
		
		Label labelPort = new Label(portComposite, SWT.NONE); 
		labelPort.setText("Listen Port: ");
		
		textListenPort = new Text(portComposite, SWT.BORDER);
		GridData gridData4PortText = new GridData();
		gridData4PortText.widthHint = 150;
		textListenPort.setLayoutData(gridData4PortText);
		
		ruleOptionsCommandComp = new Composite(this, SWT.NONE);
		ruleOptionsActionsGroup = new RuleOptionsActionsGroup(this, SWT.NONE);
		ruleOptionsBlockComp = new RuleOptionsBlockComp(ruleOptionsActionsGroup.getRuleOptionsActionGroup(), this, SWT.NONE);
		
		final GridData gridData4CommandComp = new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.GRAB_VERTICAL | GridData.VERTICAL_ALIGN_END);
		gridData4CommandComp.horizontalSpan = 2;
		ruleOptionsCommandComp.setLayoutData(gridData4CommandComp);
		final GridLayout gridLayout4CommandComp = new GridLayout();
		gridLayout4CommandComp.numColumns = 3;
		ruleOptionsCommandComp.setLayout(gridLayout4CommandComp);

		ruleOptionsModifyButton = new Button(ruleOptionsCommandComp, SWT.NONE);
		final GridData gridData4ModifyButton = new GridData();
		gridData4ModifyButton.widthHint = 45;
		ruleOptionsModifyButton.setLayoutData(gridData4ModifyButton);
		ruleOptionsModifyButton.setText("Modify");

		ruleOptionsModifyButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				editSelectedRule();

			}
		});

		ruleOptionsModifyButton.setVisible(false);

		ruleOptionsResetButton = new Button(ruleOptionsCommandComp, SWT.NONE);
		final GridData gridData4ResetButton = new GridData();
		gridData4ResetButton.widthHint = 45;
		ruleOptionsResetButton.setLayoutData(gridData4ResetButton);
		ruleOptionsResetButton.setText("Reset");

		ruleOptionsResetButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				resetValues(rule);
			}
		});
		ruleOptionsResetButton.setVisible(false);
	}
	
	@Override
	public void editSelectedRule() {
		try {
			int port = Integer.parseInt(textListenPort.getText());
			ProxyRuleKey key = new ProxyRuleKey(port);
			if (key.equals(rule.getRuleKey())) {
				rule.setName(nameText.getText());
				Router.getInstance().getRuleManager().ruleChanged(rule);
				return;
			}
			
			if (Router.getInstance().getRuleManager().getRule(key) != null) {
				openErrorDialog("Illeagal input! Your rule key conflict with another existent rule.");
				return;
			}
			
			if (openConfirmDialog("You've changed the rule key, so all the old history will be cleared.")) {

				if (!((HttpTransport) Router.getInstance().getTransport()).isAnyThreadListeningAt(key.getPort())) {
					try {
						((HttpTransport) Router.getInstance().getTransport()).addPort(key.getPort());
					} catch (IOException e1) {
						openErrorDialog("Failed to open the new port. Please change another one. Old rule is retained");
						return;
					}
				}
				Router.getInstance().getRuleManager().removeRule(rule);
				if (!Router.getInstance().getRuleManager().isAnyRuleWithPort(rule.getRuleKey().getPort()) && (rule.getRuleKey().getPort() != key.getPort())) {
					try {
						((HttpTransport) Router.getInstance().getTransport()).closePort(rule.getRuleKey().getPort());
					} catch (IOException e2) {
						openErrorDialog("Failed to close the obsolete port: " + rule.getRuleKey().getPort());
					}
				}
				rule.setName(nameText.getText().trim());
				rule.setRuleKey(key);
				Router.getInstance().getRuleManager().addRuleIfNew(rule);
				Router.getInstance().getRuleManager().ruleChanged(rule);
			}
		} catch (NumberFormatException nfe) {
			openErrorDialog("Illeagal input! Please check listen port again");
			return;
		}
	}

	@Override
	public void resetValues(Rule selectedRule) {
		if ((rule = selectedRule) != null && selectedRule instanceof ProxyRule ) {
			nameText.setText(rule.getName());
			textListenPort.setText(rule.getRuleKey().getPort() + "");
			ruleOptionsBlockComp.setRequestBlock(rule.isBlockRequest());
			ruleOptionsBlockComp.setResponseBlock(rule.isBlockResponse());
		}
	}

}
