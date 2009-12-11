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
		GridLayout layout = new GridLayout();
		layout.marginTop = 20;
		layout.marginLeft = 20;
		layout.marginBottom = 20;
		layout.marginRight = 20;
		setLayout(layout);

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
	}

	public MethodFilter getMethodFilter() {
		return methodFilter;
	}

	
	public void showAllMethods() {
		 btShowAllMethods.setSelection(true);
		 btShowAllMethods.notifyListeners(SWT.Selection, null);
	}
	
	
}
