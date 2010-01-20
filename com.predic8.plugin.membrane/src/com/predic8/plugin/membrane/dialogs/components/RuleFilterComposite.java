package com.predic8.plugin.membrane.dialogs.components;

import java.util.Collection;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.plugin.membrane.filtering.ExchangesFilter;

public class RuleFilterComposite extends AbstractFilterComposite {

	
	public RuleFilterComposite(Composite parent, ExchangesFilter aFilter) {
		super(parent, aFilter);
	}


	@Override
	protected String getGroupText() {
		return "Show Rules";
	}


	@Override
	protected String getShowAllText() {
		return "Display exchanges from all rules";
	}


	@Override
	protected String getShowSelectedOnlyText() {
		return "Display exchanges from selected rules only";
	}


	@Override
	protected void initializeButtons(Composite composite) {
		Collection<Rule> rules = Router.getInstance().getRuleManager().getRules();
		for (Rule rule : rules) {
			final Button bt = new Button(composite, SWT.CHECK);
			bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
			bt.setText(rule.toString());
			bt.setData(rule.getRuleKey());
			if (filter.getDisplayedItems().contains(rule.getRuleKey())) {
				bt.setSelection(true);
			}

			bt.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (bt.getSelection()) {
						filter.getDisplayedItems().add((RuleKey) bt.getData());
					} else {
						filter.getDisplayedItems().remove((RuleKey) bt.getData());
					}
				}
			});
			buttons.add(bt);
		}
	}

	@Override
	public String getFilterName() {
		return "Rule";
	}

}
