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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.membrane.core.Router;
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
	
	public ExchangesTableFilterDialog(Shell parentShell, ExchangesView parent) {
		super(parentShell);
		this.exchangesView = parent;
		
	}
	
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Exchange Filters");
		shell.setSize(440, 500);
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
			rulesFilter = (RulesFilter)exchangesView.getFilterManager().getFilterForClass(RulesFilter.class);
		} else {
			rulesFilter = new RulesFilter();
		}
		
		
		if (exchangesView.getFilterManager().getFilterForClass(MethodFilter.class) != null) {
			methodFilter = (MethodFilter)exchangesView.getFilterManager().getFilterForClass(MethodFilter.class);
		} else {
			methodFilter = new MethodFilter();
		}
		
		tabFolder = new TabFolder(container, SWT.NONE);
		
		
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
		rulesGroup.setLayoutData(new GridData( GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		
		
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
					for (Button button : buttons) {
						button.setEnabled(false);
						//filter.getDisplayedRules().clear();
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
						rulesFilter.getDisplayedRules().add((RuleKey)bt.getData());
					} else {
						rulesFilter.getDisplayedRules().remove((RuleKey)bt.getData());
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
		rulesGroup.setLayoutData(new GridData( GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		
		
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
					
				} 
			}
		});
		
		
		btShowSelectedMethodsOnly = new Button(rulesGroup, SWT.RADIO);
		btShowSelectedMethodsOnly.setText("Display exchanges with selected methods only");
		btShowSelectedMethodsOnly.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (btShowSelectedMethodsOnly.getSelection()) {
					
				} 
			}
		});
		
		Composite methodsComposite = new Composite(rulesGroup, SWT.BORDER);
		methodsComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		GridData rulesGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
		methodsComposite.setLayoutData(rulesGridData);

		GridLayout rulesLayout = new GridLayout();
		methodsComposite.setLayout(rulesLayout);
		
		

		Button btGet = new Button(methodsComposite, SWT.CHECK);
		btGet.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btGet.setText("GET");
		
		Button btPost = new Button(methodsComposite, SWT.CHECK);
		btPost.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btPost.setText("POST");
		

		Button btPut = new Button(methodsComposite, SWT.CHECK);  
		btPut.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btPut.setText("PUT");
		
		
		Button btDelete = new Button(methodsComposite, SWT.CHECK);  
		btDelete.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btDelete.setText("DELETE");
		
		Button btHead = new Button(methodsComposite, SWT.CHECK);  
		btHead.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btHead.setText("HEAD");
		
		Button btTrace = new Button(methodsComposite, SWT.CHECK);  
		btTrace.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		btTrace.setText("TRACE");
		
		
		
		return container;
	}
	
	
	
	@Override
	protected void okPressed() {
		exchangesView.getFilterManager().addFilter(rulesFilter);
		exchangesView.reloadAll();
		super.okPressed();
	}
	
	
}