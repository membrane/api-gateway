package com.predic8.plugin.membrane.preferences;

import java.security.KeyStore;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;

import com.predic8.plugin.membrane.contentproviders.KeyStoreViewContentProvider;
import com.predic8.plugin.membrane.labelproviders.KeyStoreViewLabelProvider;

public class KeyStoreContentDialog extends Dialog {

	private TableViewer tableViewer;

	private static final String[] TITLES = { "Kind", "Alias", "Subject", "Issuer", "Serial Number", "Algorithm" };
	private static final int[] BOUNDS = { 100, 100, 100, 100, 120, 120 };

	private KeyStore store;
	private String storePassword;

	protected KeyStoreContentDialog(Shell parentShell, KeyStore store, String password) {
		super(parentShell);
		this.store = store;
		this.storePassword = password;
	}

	@Override
	protected void initializeBounds() {
		super.initializeBounds();
		Shell shell = this.getShell();
		Monitor primary = shell.getMonitor();
		Rectangle bounds = primary.getBounds();
		Rectangle rect = shell.getBounds();
		shell.setLocation(bounds.x + (bounds.width - rect.width) / 2, bounds.y + (bounds.height - rect.height) / 2);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Certificate Content");
		shell.setSize(600, 300);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite comp = (Composite) super.createDialogArea(parent);
		comp.setLayout(new GridLayout());

		tableViewer = createTableViewer(comp);
		tableViewer.setInput(store);

		return comp;
	}

	private TableViewer createTableViewer(Composite comp) {
		final TableViewer viewer = new TableViewer(comp, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER | SWT.VIRTUAL);
		createColumns(viewer);

		viewer.setContentProvider(new KeyStoreViewContentProvider(storePassword));
		viewer.setLabelProvider(new KeyStoreViewLabelProvider());

		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.grabExcessVerticalSpace = true;
		gData.grabExcessHorizontalSpace = true;
		viewer.getTable().setLayoutData(gData);

		return viewer;
	}

	private void createColumns(TableViewer viewer) {
		for (int i = 0; i < TITLES.length; i++) {
			TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
			col.getColumn().setAlignment(SWT.LEFT);
			col.getColumn().setText(TITLES[i]);
			col.getColumn().setWidth(BOUNDS[i]);
			col.getColumn().setResizable(true);
			col.getColumn().setMoveable(true);
		}

		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
	
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		Button cancelButton = createButton(parent, CANCEL, "Close", false);
		cancelButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				close();
			};
		});
	}
	
}
