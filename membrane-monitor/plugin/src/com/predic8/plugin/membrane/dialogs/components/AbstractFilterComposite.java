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

import com.predic8.plugin.membrane.components.GridPanel;
import com.predic8.plugin.membrane.filtering.ExchangesFilter;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class AbstractFilterComposite extends GridPanel {

	protected List<Button> buttons = new ArrayList<Button>();

	protected ExchangesFilter filter;

	private Button btShowAll;

	private Button btShowSelectedOnly;

	public AbstractFilterComposite(Composite parent, ExchangesFilter aFilter) {
		super(parent, 20, 1);
		filter = aFilter;

		Group rulesGroup = createRulesGroup();

		btShowAll = createShowAllButton(rulesGroup);

		btShowSelectedOnly = createShowSelectedButton(rulesGroup);

		Composite controls = createControlsComposite(rulesGroup);

		controls.setLayout(new GridLayout());

		initializeButtons(controls);

		if (filter.isShowAll()) {
			btShowAll.setSelection(true);
			btShowAll.notifyListeners(SWT.Selection, null);
		} else {
			btShowSelectedOnly.setSelection(true);
			btShowSelectedOnly.notifyListeners(SWT.Selection, null);
		}

	}

	private Composite createControlsComposite(Group rulesGroup) {
		Composite composite = new Composite(rulesGroup, SWT.BORDER);
		composite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));
		return composite;
	}

	private Button createShowSelectedButton(Group rulesGroup) {
		final Button bt = new Button(rulesGroup, SWT.RADIO);
		bt.setText(getShowSelectedOnlyText());
		bt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (!bt.getSelection())
					return;
				
				Set<Object> toDisplay = filter.getDisplayedItems();
				for (Button button : buttons) {
					button.setEnabled(true);
					button.setSelection(toDisplay.contains(button.getData()));
				}
				filter.setShowAll(false);
			}
		});
		return bt;
	}

	private Button createShowAllButton(Group rulesGroup) {
		final Button bt = new Button(rulesGroup, SWT.RADIO);
		bt.setText(getShowAllText());
		bt.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				if (bt.getSelection()) {
					btShowSelectedOnly.setSelection(false);
					for (Button button : buttons) {
						button.setEnabled(false);
						filter.setShowAll(true);
					}
				}
			}
		});
		return bt;
	}

	private Group createRulesGroup() {
		Group group = new Group(this, SWT.NONE);
		group.setText(getGroupText());
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		group.setLayout(SWTUtil.createGridLayout(1, 10));
		return group;
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
