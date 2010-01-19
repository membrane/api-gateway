package com.predic8.plugin.membrane.dialogs.components;

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

import com.predic8.membrane.core.http.Request;
import com.predic8.plugin.membrane.filtering.MethodFilter;

public class MethodFilterComposite extends Composite {

	private Button btGet, btPost, btPut, btDelete, btHead, btTrace;

	private Button btShowAllMethods;

	private Button btShowSelectedMethodsOnly;

	private MethodFilter methodFilter;

	public MethodFilterComposite(Composite parent, MethodFilter filter) {
		super(parent, SWT.NONE);
		this.methodFilter = filter;

		setLayout(createTopLayout());

		Group rulesGroup = new Group(this, SWT.NONE);
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

				if (!btShowAllMethods.getSelection())
					return;

				btShowSelectedMethodsOnly.setSelection(false);
				setMethodButtonStatus(false);
				methodFilter.setShowAll(true);

			}
		});

		btShowSelectedMethodsOnly = new Button(rulesGroup, SWT.RADIO);
		btShowSelectedMethodsOnly.setText("Display exchanges with selected methods only");
		btShowSelectedMethodsOnly.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				
				if (!btShowSelectedMethodsOnly.getSelection())
					return;
				
				setMethodButtonStatus(true);

				Set<String> toDisplay = methodFilter.getDisplayedMethods();
				btGet.setSelection(toDisplay.contains(btGet.getData()) ? true : false);
				btPost.setSelection(toDisplay.contains(btPost.getData()) ? true : false);
				btPut.setSelection(toDisplay.contains(btPut.getData()) ? true : false);
				btDelete.setSelection(toDisplay.contains(btDelete.getData()) ? true : false);
				btHead.setSelection(toDisplay.contains(btHead.getData()) ? true : false);
				btTrace.setSelection(toDisplay.contains(btTrace.getData()) ? true : false);

				methodFilter.setShowAll(false);

			}
		});

		Composite methodsComposite = new Composite(rulesGroup, SWT.BORDER);
		methodsComposite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		methodsComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL));

		methodsComposite.setLayout(new GridLayout());

		btGet = createMethodButton(methodsComposite, Request.METHOD_GET);
		btPost = createMethodButton(methodsComposite, Request.METHOD_POST);
		btPut = createMethodButton(methodsComposite, Request.METHOD_PUT);
		btDelete = createMethodButton(methodsComposite, Request.METHOD_DELETE);
		btHead = createMethodButton(methodsComposite, Request.METHOD_HEAD);
		btTrace = createMethodButton(methodsComposite, Request.METHOD_TRACE);

		if (methodFilter.isShowAll()) {
			btShowAllMethods.setSelection(true);
			btShowAllMethods.notifyListeners(SWT.Selection, null);
		} else {
			btShowSelectedMethodsOnly.setSelection(true);
			btShowSelectedMethodsOnly.notifyListeners(SWT.Selection, null);
		}
	}

	private Button createMethodButton(Composite methodsComposite, String method) {
		Button bt = new Button(methodsComposite, SWT.CHECK);
		bt.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
		bt.setText(method);
		bt.setData(method);
		bt.addSelectionListener(new MethodSelectionAdapter(bt, methodFilter));
		return bt;
	}

	private GridLayout createTopLayout() {
		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		return layout;
	}

	public MethodFilter getMethodFilter() {
		return methodFilter;
	}

	public void showAllMethods() {
		btShowAllMethods.setSelection(true);
		btShowAllMethods.notifyListeners(SWT.Selection, null);
	}

	private void setMethodButtonStatus(boolean enabled) {
		btGet.setEnabled(enabled);
		btPut.setEnabled(enabled);
		btPost.setEnabled(enabled);
		btDelete.setEnabled(enabled);
		btHead.setEnabled(enabled);
		btTrace.setEnabled(enabled);
	}

}
