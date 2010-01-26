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
package com.predic8.plugin.membrane.dialogs.components;

import java.util.ArrayList;
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

import com.predic8.plugin.membrane.filtering.ExchangesFilter;

public abstract class AbstractFilterComposite extends Composite {

	protected List<Button> buttons = new ArrayList<Button>();

	protected ExchangesFilter filter;

	private Button btShowAll;

	private Button btShowSelectedOnly;

	public AbstractFilterComposite(Composite parent, ExchangesFilter aFilter) {
		super(parent, SWT.NONE);
		filter = aFilter;

		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		setLayout(layout);

		Group rulesGroup = new Group(this, SWT.NONE);
		rulesGroup.setText(getGroupText());
		rulesGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4RuleGroup = new GridLayout();
		gridLayout4RuleGroup.marginTop = 10;
		gridLayout4RuleGroup.marginLeft = 10;
		gridLayout4RuleGroup.marginRight = 10;
		rulesGroup.setLayout(gridLayout4RuleGroup);

		btShowAll = new Button(rulesGroup, SWT.RADIO);
		btShowAll.setText(getShowAllText());
		btShowAll.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				if (btShowAll.getSelection()) {
					btShowSelectedOnly.setSelection(false);
					for (Button button : buttons) {
						button.setEnabled(false);
						filter.setShowAll(true);
					}
				}
			}
		});

		btShowSelectedOnly = new Button(rulesGroup, SWT.RADIO);
		btShowSelectedOnly.setText(getShowSelectedOnlyText());
		btShowSelectedOnly.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (!btShowSelectedOnly.getSelection())
					return;
				
				Set<Object> toDisplay = filter.getDisplayedItems();
				for (Button button : buttons) {
					button.setEnabled(true);
					button.setSelection(toDisplay.contains(button.getData()));
				}
				filter.setShowAll(false);
			}
		});

		Composite buttonsComposite = new Composite(rulesGroup, SWT.BORDER);
		buttonsComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		GridData rulesGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
		buttonsComposite.setLayoutData(rulesGridData);

		buttonsComposite.setLayout(new GridLayout());

		initializeButtons(buttonsComposite);

		if (filter.isShowAll()) {
			btShowAll.setSelection(true);
			btShowAll.notifyListeners(SWT.Selection, null);
		} else {
			btShowSelectedOnly.setSelection(true);
			btShowSelectedOnly.notifyListeners(SWT.Selection, null);
		}

	}

	protected abstract void initializeButtons(Composite composite);
	
	public abstract String getFilterName();
	
	protected abstract String getGroupText();

	protected abstract String getShowAllText();

	protected abstract String getShowSelectedOnlyText();

	public ExchangesFilter getFilter() {
		return filter;
	}

	public void showAll() {
		btShowAll.setSelection(true);
		btShowAll.notifyListeners(SWT.Selection, null);
	}

}
