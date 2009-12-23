package com.predic8.plugin.membrane.dialogs.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.plugin.membrane.filtering.RulesFilter;

public class RuleFilterComposite extends Composite {

	private List<Button> buttons = new ArrayList<Button>();
	
	private RulesFilter rulesFilter;
	
	private Button btShowAllRules;

	private Button btShowSelectedRulesOnly;

	
	public RuleFilterComposite(Composite parent, RulesFilter filter) {
		super(parent, SWT.NONE);
		rulesFilter = filter;
		
		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		setLayout(layout);

		Group rulesGroup = new Group(this, SWT.NONE);
		rulesGroup.setText("Show Rules");
		rulesGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4RuleGroup = new GridLayout();
		gridLayout4RuleGroup.marginTop = 10;
		gridLayout4RuleGroup.marginLeft = 10;
		gridLayout4RuleGroup.marginRight = 10;
		rulesGroup.setLayout(gridLayout4RuleGroup);

		btShowAllRules = new Button(rulesGroup, SWT.RADIO);
		btShowAllRules.setText("Display exchanges from all rules");
		btShowAllRules.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				if (btShowAllRules.getSelection()) {
					btShowSelectedRulesOnly.setSelection(false);
					for (Button button : buttons) {
						button.setEnabled(false);
						// filter.getDisplayedRules().clear();
						rulesFilter.setShowAllRules(true);
					}
				}
			}
		});

		btShowSelectedRulesOnly = new Button(rulesGroup, SWT.RADIO);
		btShowSelectedRulesOnly.setText("Display exchanges from selected rules only");
		btShowSelectedRulesOnly.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (btShowSelectedRulesOnly.getSelection()) {
					Set<RuleKey> toDisplay = rulesFilter.getDisplayedRules();
					for (Button button : buttons) {
						button.setEnabled(true);
						if (toDisplay.contains(button.getData())) {
							button.setSelection(true);
						} else {
							button.setSelection(false);
						}
					}
					rulesFilter.setShowAllRules(false);
				}
			}
		});

		Composite rulesComposite = new Composite(rulesGroup, SWT.BORDER);
		rulesComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		GridData rulesGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
		rulesComposite.setLayoutData(rulesGridData);

		GridLayout rulesLayout = new GridLayout();
		rulesComposite.setLayout(rulesLayout);

		Collection<Rule> rules = Router.getInstance().getRuleManager().getRules();
		for (Rule rule : rules) {
			final Button bt = new Button(rulesComposite, SWT.CHECK);
			bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			bt.setText(rule.toString());
			bt.setData(rule.getRuleKey());
			if (rulesFilter.getDisplayedRules().contains(rule.getRuleKey())) {
				bt.setSelection(true);
			}

			bt.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (bt.getSelection()) {
						rulesFilter.getDisplayedRules().add((RuleKey) bt.getData());
					} else {
						rulesFilter.getDisplayedRules().remove((RuleKey) bt.getData());
					}
				}
			});
			buttons.add(bt);
		}

		if (rulesFilter.isShowAllRules()) {
			btShowAllRules.setSelection(true);
			btShowAllRules.notifyListeners(SWT.Selection, null);
		} else {
			btShowSelectedRulesOnly.setSelection(true);
			btShowSelectedRulesOnly.notifyListeners(SWT.Selection, null);
		}

	}


	public RulesFilter getRulesFilter() {
		return rulesFilter;
	}


	public void showAllRules() {
		btShowAllRules.setSelection(true);
		btShowAllRules.notifyListeners(SWT.Selection, null);
	}


}
