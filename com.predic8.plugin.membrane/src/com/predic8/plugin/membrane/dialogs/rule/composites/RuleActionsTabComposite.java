package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import com.predic8.membrane.core.rules.Rule;

public class RuleActionsTabComposite extends Composite {

	private Button btBlockRequest;

	private Button btBlockResponse;

	public RuleActionsTabComposite(Composite parent) {
		super(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 20;
		gridLayout.marginLeft = 20;
		gridLayout.marginBottom = 20;
		gridLayout.marginRight = 20;
		setLayout(gridLayout);

		Group ruleActionGroup = new Group(this, SWT.NONE);
		ruleActionGroup.setText("Action");
		ruleActionGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		ruleActionGroup.setLayout(new GridLayout());

		btBlockRequest = new Button(ruleActionGroup, SWT.CHECK);
		btBlockRequest.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

			}
		});
		btBlockRequest.setText("Block Request");

		btBlockResponse = new Button(ruleActionGroup, SWT.CHECK);
		btBlockResponse.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

			}
		});
		btBlockResponse.setText("Block Response");

	}

	public boolean isRequestBlocked() {
		return btBlockRequest.getSelection();
	}
	
	public boolean isResponseBlocked() {
		return btBlockResponse.getSelection();
	}
	
	public void setInput(Rule rule) {
		btBlockRequest.setSelection((rule.isBlockRequest()));
		btBlockResponse.setSelection(rule.isBlockResponse());
	}
	
}
