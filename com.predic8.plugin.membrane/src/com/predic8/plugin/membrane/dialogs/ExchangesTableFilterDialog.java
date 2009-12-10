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

package com.predic8.plugin.membrane.dialogs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.plugin.membrane.filtering.MethodFilter;
import com.predic8.plugin.membrane.filtering.RulesFilter;
import com.predic8.plugin.membrane.views.ExchangesView;

public class ExchangesTableFilterDialog extends Dialog {

	private ExchangesView exchangesView;

	private List<Button> buttons = new ArrayList<Button>();

	private RulesFilter rulesFilter;

	private MethodFilter methodFilter;

	private Button btShowAllRules;

	private Button btShowAllMethods;

	private Button btShowSelectedRulesOnly;

	private Button btShowSelectedMethodsOnly;

	private TabFolder tabFolder;

	private Button btGet, btPost, btPut, btDelete, btHead, btTrace;

	public ExchangesTableFilterDialog(Shell parentShell, ExchangesView parent) {
		super(parentShell);
		this.exchangesView = parent;

	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Exchange Filters");
		shell.setSize(440, 560);
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		 Composite composite = new Composite(parent, SWT.NONE);
		 
		 GridLayout layout = new GridLayout();
		 composite.setLayout(layout);
 		 
		 Button BtRemoveFilters = new Button(composite, SWT.PUSH);
		 BtRemoveFilters.addSelectionListener(new SelectionAdapter() {
			 @Override
			public void widgetSelected(SelectionEvent e) {
				 btShowAllRules.setSelection(true);
				 btShowAllRules.notifyListeners(SWT.Selection, null);
				 btShowAllMethods.setSelection(true);
				 btShowAllMethods.notifyListeners(SWT.Selection, null);
			}
		 });
		 BtRemoveFilters.setText("Remove  all  filters");
		
		 GridData gData = new GridData(SWT.RIGHT, SWT.FILL, true, true, 1, 1);
		 gData.grabExcessHorizontalSpace = true;
		 BtRemoveFilters.setLayoutData(gData);

		 Label label = new Label(composite, SWT.SEPARATOR | SWT.HORIZONTAL);
		 GridData labelGridData = new GridData(410, 12);
		 label.setLayoutData(labelGridData);
		 
		return super.createButtonBar(composite);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);

		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		container.setLayout(layout);

		if (exchangesView.getFilterManager().getFilterForClass(RulesFilter.class) != null) {
			rulesFilter = (RulesFilter) exchangesView.getFilterManager().getFilterForClass(RulesFilter.class);
		} else {
			rulesFilter = new RulesFilter();
		}

		if (exchangesView.getFilterManager().getFilterForClass(MethodFilter.class) != null) {
			methodFilter = (MethodFilter) exchangesView.getFilterManager().getFilterForClass(MethodFilter.class);
		} else {
			methodFilter = new MethodFilter();
		}

		tabFolder = new TabFolder(container, SWT.NONE);		
		
		GridData gdTabs = new GridData();
		gdTabs.grabExcessHorizontalSpace = true;
		gdTabs.widthHint = 400;
		tabFolder.setLayoutData(gdTabs);
		
		TabItem tabItemRule = new TabItem(tabFolder, SWT.NONE);
		tabItemRule.setText("Rule");
		tabItemRule.setControl(getRuleComposite());

		TabItem tabItemMethod = new TabItem(tabFolder, SWT.NONE);
		tabItemMethod.setText("Method");
		tabItemMethod.setControl(getMethodComposite());

		return container;
	}

	private Composite getRuleComposite() {
		Composite container = new Composite(tabFolder, SWT.NONE);

		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		container.setLayout(layout);

		Group rulesGroup = new Group(container, SWT.NONE);
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

		return container;
	}

	private Composite getMethodComposite() {
		Composite container = new Composite(tabFolder, SWT.NONE);

		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		container.setLayout(layout);

		Group rulesGroup = new Group(container, SWT.NONE);
		rulesGroup.setText("Show Methods");
		rulesGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4RuleGroup = new GridLayout();
		gridLayout4RuleGroup.marginTop = 10;
		gridLayout4RuleGroup.marginLeft = 10;
		gridLayout4RuleGroup.marginRight = 10;
		rulesGroup.setLayout(gridLayout4RuleGroup);

		btShowAllMethods = new Button(rulesGroup, SWT.RADIO);
		btShowAllMethods.setText("Display exchanges with any method");
		btShowAllMethods.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				if (btShowAllMethods.getSelection()) {
					btShowSelectedMethodsOnly.setSelection(false);
					btGet.setEnabled(false);
					btPut.setEnabled(false);
					btPost.setEnabled(false);
					btDelete.setEnabled(false);
					btHead.setEnabled(false);
					btTrace.setEnabled(false);
					methodFilter.setShowAllMethods(true);
				}

			}
		});

		btShowSelectedMethodsOnly = new Button(rulesGroup, SWT.RADIO);
		btShowSelectedMethodsOnly.setText("Display exchanges with selected methods only");
		btShowSelectedMethodsOnly.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (btShowSelectedMethodsOnly.getSelection()) {
					btGet.setEnabled(true);
					btPut.setEnabled(true);
					btPost.setEnabled(true);
					btHead.setEnabled(true);
					btDelete.setEnabled(true);
					btTrace.setEnabled(true);

					Set<String> toDisplay = methodFilter.getDisplayedMethods();
					if (toDisplay.contains(btGet.getData())) {
						btGet.setSelection(true);
					} else {
						btGet.setSelection(false);
					}

					if (toDisplay.contains(btPost.getData())) {
						btPost.setSelection(true);
					} else {
						btPost.setSelection(false);
					}

					if (toDisplay.contains(btPut.getData())) {
						btPut.setSelection(true);
					} else {
						btPut.setSelection(false);
					}

					if (toDisplay.contains(btDelete.getData())) {
						btDelete.setSelection(true);
					} else {
						btDelete.setSelection(false);
					}

					if (toDisplay.contains(btHead.getData())) {
						btHead.setSelection(true);
					} else {
						btHead.setSelection(false);
					}

					if (toDisplay.contains(btTrace.getData())) {
						btTrace.setSelection(true);
					} else {
						btTrace.setSelection(false);
					}

					methodFilter.setShowAllMethods(false);
				}
			}
		});

		Composite methodsComposite = new Composite(rulesGroup, SWT.BORDER);
		methodsComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		GridData rulesGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
		methodsComposite.setLayoutData(rulesGridData);

		GridLayout rulesLayout = new GridLayout();
		methodsComposite.setLayout(rulesLayout);

		btGet = new Button(methodsComposite, SWT.CHECK);
		btGet.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btGet.setText("GET");
		btGet.setData(Request.METHOD_GET);
		btGet.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btGet.getSelection()) {
					methodFilter.getDisplayedMethods().add((String) btGet.getData());
				} else {
					methodFilter.getDisplayedMethods().remove((String) btGet.getData());
				}
			}
		});

		btPost = new Button(methodsComposite, SWT.CHECK);
		btPost.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btPost.setText("POST");
		btPost.setData(Request.METHOD_POST);
		btPost.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btPost.getSelection()) {
					methodFilter.getDisplayedMethods().add((String) btPost.getData());
				} else {
					methodFilter.getDisplayedMethods().remove((String) btPost.getData());
				}
			}
		});

		btPut = new Button(methodsComposite, SWT.CHECK);
		btPut.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btPut.setText("PUT");
		btPut.setData(Request.METHOD_PUT);
		btPut.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btPut.getSelection()) {
					methodFilter.getDisplayedMethods().add((String) btPut.getData());
				} else {
					methodFilter.getDisplayedMethods().remove((String) btPut.getData());
				}
			}
		});

		btDelete = new Button(methodsComposite, SWT.CHECK);
		btDelete.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btDelete.setText("DELETE");
		btDelete.setData(Request.METHOD_DELETE);
		btDelete.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btDelete.getSelection()) {
					methodFilter.getDisplayedMethods().add((String) btDelete.getData());
				} else {
					methodFilter.getDisplayedMethods().remove((String) btDelete.getData());
				}
			}
		});

		btHead = new Button(methodsComposite, SWT.CHECK);
		btHead.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btHead.setText("HEAD");
		btHead.setData(Request.METHOD_HEAD);
		btHead.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btHead.getSelection()) {
					methodFilter.getDisplayedMethods().add((String) btHead.getData());
				} else {
					methodFilter.getDisplayedMethods().remove((String) btHead.getData());
				}
			}
		});

		btTrace = new Button(methodsComposite, SWT.CHECK);
		btTrace.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btTrace.setText("TRACE");
		btTrace.setData(Request.METHOD_TRACE);
		btTrace.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btTrace.getSelection()) {
					methodFilter.getDisplayedMethods().add((String) btTrace.getData());
				} else {
					methodFilter.getDisplayedMethods().remove((String) btTrace.getData());
				}
			}
		});

		if (methodFilter.isShowAllMethods()) {
			btShowAllMethods.setSelection(true);
			btShowAllMethods.notifyListeners(SWT.Selection, null);
		} else {
			btShowSelectedMethodsOnly.setSelection(true);
			btShowSelectedMethodsOnly.notifyListeners(SWT.Selection, null);
		}

		return container;
	}

	@Override
	protected void initializeBounds() {
		super.initializeBounds();
		Shell shell = this.getShell();
		Monitor primary = shell.getMonitor();
		Rectangle bounds = primary.getBounds();
		Rectangle rect = shell.getBounds();
		int x = bounds.x + (bounds.width - rect.width) / 2;
		int y = bounds.y + (bounds.height - rect.height) / 2;
		shell.setLocation(x, y);
	}

	@Override
	protected void okPressed() {
		exchangesView.getFilterManager().addFilter(rulesFilter);
		exchangesView.getFilterManager().addFilter(methodFilter);
		exchangesView.reloadAll();
		super.okPressed();
	}

}