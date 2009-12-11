/* Copyright 2009 predic8 Gmb
H, www.predic8.com

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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;

import com.predic8.plugin.membrane.views.ExchangesView;

public class ExchangesTableSorterDialog extends Dialog {

	private ExchangesView exchangesView;
	
	private Combo comboSorters;
	
	private Button btDisable;
	
	private Button btEnable;
	
	Composite childComp;
	
	public ExchangesTableSorterDialog(Shell parentShell, ExchangesView parent) {
		super(parentShell);
		this.exchangesView = parent;

	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Exchange Sorters");
		shell.setSize(440, 560);
	}

	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);

		GridLayout layout = new GridLayout();
		layout.marginTop = 25;
		layout.marginLeft = 25;
		layout.marginBottom = 25;
		layout.marginRight = 25;
		layout.numColumns = 1;
		container.setLayout(layout);

		
		btDisable = new Button(container, SWT.RADIO);
		btDisable.setText("Disable exchange sorting");
		btDisable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btDisable.getSelection()) {
					comboSorters.setEnabled(false);
				} 
			}
		});
		
		btEnable = new Button(container, SWT.RADIO);
		btEnable.setText("Enable exchange sorting");
		btEnable.addSelectionListener(new SelectionAdapter() {
		
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (btEnable.getSelection()) {
					comboSorters.setEnabled(true);
				}
			}
		
		});
		
		
		Label lbDummy1 = new Label(container, SWT.NONE);
		lbDummy1.setText("");
		
		childComp = new Composite(container, SWT.BORDER);
		childComp.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		GridData gdChildComp = new GridData();
		gdChildComp.grabExcessHorizontalSpace = true;
		gdChildComp.grabExcessVerticalSpace = true;
		gdChildComp.widthHint = 370;
		gdChildComp.heightHint = 300;
		childComp.setLayoutData(gdChildComp);
		
		GridLayout layoutChild = new GridLayout();
		layoutChild.marginTop = 15;
		layoutChild.marginLeft = 15;
		layoutChild.marginBottom = 15;
		layoutChild.marginRight = 15;
		layoutChild.numColumns = 2;
		childComp.setLayout(layoutChild);
		
		
		Label lbComboSorters = new Label(childComp, SWT.NONE);
		lbComboSorters.setText("Sort By "); 
		lbComboSorters.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		
		comboSorters = new Combo(childComp, SWT.DROP_DOWN);
		comboSorters.add("Status-Code");
		comboSorters.add("Time");
		comboSorters.add("Rule");
		comboSorters.add("Method");
		comboSorters.add("Path");
		comboSorters.add("Client");
		comboSorters.add("Server");
		comboSorters.add("Request Content-Type");
		comboSorters.add("Request Content-Length");
		comboSorters.add("Response Content-Length");
		comboSorters.add("Duration");
		
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
		
		super.okPressed();
	}

}