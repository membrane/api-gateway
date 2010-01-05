package com.predic8.plugin.membrane.dialogs.rule.composites;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.dialogs.rule.AddInterceptorDialog;
import com.predic8.plugin.membrane.dialogs.rule.EditInterceptorDialog;
import com.predic8.plugin.membrane.dialogs.rule.providers.InterceptorTableViewerContentProvider;
import com.predic8.plugin.membrane.dialogs.rule.providers.InterceptorTableViewerLabelProvider;

public class RuleInterceptorTabComposite extends Composite {

	private TableViewer tableViewer;

	private List<Interceptor> interceptorList = new ArrayList<Interceptor>();
	
	private Button btEdit;
	
	private Button btRemove;
	
	private Button btUp, btDown;
	
	private Interceptor selectedInterceptor; 
	
	public RuleInterceptorTabComposite(final Composite parent) {
		super(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 5;
		gridLayout.marginBottom = 10;
		gridLayout.marginRight = 5;
		setLayout(gridLayout);

		Composite listComposite = new Composite(this, SWT.NONE);
		listComposite.setLayout(new GridLayout());

		tableViewer = new TableViewer(listComposite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		createColumns(tableViewer);
		tableViewer.setContentProvider(new InterceptorTableViewerContentProvider());
		tableViewer.setLabelProvider(new InterceptorTableViewerLabelProvider());
		
		tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
				if (selection == null || selection.isEmpty()) {
					enableButtonsOnSelection(false);
					return;
				}
				enableButtonsOnSelection(true);
				selectedInterceptor = (Interceptor)selection.getFirstElement();
			}

		});

		
		GridData gridData4List = new GridData();
		gridData4List.widthHint = 330;
		gridData4List.heightHint = 270;
		tableViewer.getTable().setLayoutData(gridData4List);

		Composite controlsComposite = new Composite(this, SWT.NONE);
		RowLayout rowLayout = new RowLayout();
		rowLayout.type = SWT.VERTICAL;
		rowLayout.spacing = 15;
		rowLayout.fill = true;
		controlsComposite.setLayout(rowLayout);

		Button btNew = new Button(controlsComposite, SWT.PUSH);
		btNew.setText("Add");
		btNew.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				AddInterceptorDialog dialog = new AddInterceptorDialog(parent.getShell(), RuleInterceptorTabComposite.this);
				if (dialog.getShell() == null) {
					dialog.create();
				}
				dialog.open();
			}
		});

		btEdit = new Button(controlsComposite, SWT.PUSH);
		btEdit.setText("Edit");
		btEdit.setEnabled(false);
		btEdit.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				EditInterceptorDialog dialog = new EditInterceptorDialog(parent.getShell(), RuleInterceptorTabComposite.this);
				if (dialog.getShell() == null) {
					dialog.create();
				}
				dialog.setInput(selectedInterceptor);
				dialog.open();
			}
		});
		
		btRemove = new Button(controlsComposite, SWT.PUSH);
		btRemove.setText("Remove");
		btRemove.setEnabled(false);
		btRemove.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				interceptorList.remove(selectedInterceptor);
				selectedInterceptor = null;
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						tableViewer.setInput(interceptorList);
					}
				});
			}
		});
		
		btUp = new Button(controlsComposite, SWT.PUSH);
		btUp.setText("Up");
		btUp.setEnabled(false);
		btUp.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = interceptorList.indexOf(selectedInterceptor);
				if (index == 0)
					return;
				interceptorList.remove(index);
				interceptorList.add(index - 1, selectedInterceptor);
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						tableViewer.setInput(interceptorList);
					}
				});
			}
		});
		
		btDown = new Button(controlsComposite, SWT.PUSH);
		btDown.setText("Down");
		btDown.setEnabled(false); 
		btDown.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = interceptorList.indexOf(selectedInterceptor);
				if (index == interceptorList.size() -1)
					return;
				interceptorList.remove(index);
				interceptorList.add(index + 1, selectedInterceptor);
				Display.getCurrent().asyncExec(new Runnable() {
					public void run() {
						tableViewer.setInput(interceptorList);
					}
				});
			}
		});
		
		
		Label lbDummy1 = new Label(controlsComposite, SWT.NONE);
		lbDummy1.setText(" ");

		Label lbDummy2 = new Label(controlsComposite, SWT.NONE);
		lbDummy2.setText(" ");

		Label lbDummy3 = new Label(controlsComposite, SWT.NONE);
		lbDummy3.setText(" ");
	}

	private void enableButtonsOnSelection(boolean status) {
		btEdit.setEnabled(status);
		btRemove.setEnabled(status);
		btUp.setEnabled(status);
		btDown.setEnabled(status);
	}
	
	private void createColumns(TableViewer viewer) {
		String[] titles = { "Interceptor Name", "Class Name" };
		int[] bounds = { 100, 240 };

		for (int i = 0; i < titles.length; i++) {
			final TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
			column.getColumn().setAlignment(SWT.CENTER);
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);
		}

		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
	}

	public void setInput(Rule rule ) {
		interceptorList.addAll(rule.getInterceptors());
		tableViewer.setInput(interceptorList);
	}

	public void addNewInterceptor(Interceptor interceptor) {
		interceptorList.add(interceptor);
		Display.getCurrent().asyncExec(new Runnable() {
			public void run() {
				tableViewer.setInput(interceptorList);
			}
		});
	}

	public List<Interceptor> getInterceptorList() {
		return interceptorList;
	}

}
