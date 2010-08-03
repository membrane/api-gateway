package com.predic8.plugin.membrane.components.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public abstract class ControlsComposite extends Composite {

	protected Button btAdd;
	
	protected Button btEdit;
	
	protected Button btRemove;
	
	protected Button btUp, btDown;
	
	public ControlsComposite(Composite parent, int style) {
		super(parent, style);
		setLayout(craateLayout());
		
		addPuttonsToComposite();
	}

	private void addPuttonsToComposite() {
		btAdd = createAddButton(this);
		if (isEditSupported())
			btEdit = createEditButton(this);
		btRemove = createRemoveButton(this);
		btUp = createUpButton(this);
		btDown = createDownButton(this);
		new Label(this, SWT.NONE).setText(" ");
		new Label(this, SWT.NONE).setText(" ");
		new Label(this, SWT.NONE).setText(" ");
		new Label(this, SWT.NONE).setText(" ");
	}

	private RowLayout craateLayout() {
		RowLayout rowLayout = new RowLayout();
		rowLayout.type = SWT.VERTICAL;
		rowLayout.spacing = 15;
		rowLayout.fill = true;
		return rowLayout;
	}

	public void enableDependentButtons(boolean status) {
		btEdit.setEnabled(status);
		btRemove.setEnabled(status);
		btUp.setEnabled(status);
		btDown.setEnabled(status);
	}

	private Button createAddButton(Composite composite) {
		Button bt = new Button(composite, SWT.PUSH);
		bt.setImage(MembraneUIPlugin.getDefault().getImageRegistry().get(ImageKeys.IMAGE_ADD_RULE));
		bt.setText("Add");
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				newButtonPressed();
			}

		});
		return bt;
	}
	
	private Button createEditButton(final Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Edit");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				editButtonPressed();
			}
		});
		return bt;
	}

	private Button createRemoveButton(Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Remove");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				removeButtonPressed();
			}
		});
		return bt;
	}

	private Button createUpButton(Composite controlsComposite) {
		Button bt = new Button(controlsComposite, SWT.PUSH);
		bt.setText("Up");
		bt.setEnabled(false);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				upButtonPressed();
			}
		});
		return bt;
	}

	private Button createDownButton(Composite controlsComposite) {
		Button btDown = new Button(controlsComposite, SWT.PUSH);
		btDown.setText("Down");
		btDown.setEnabled(false); 
		btDown.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				downButtonPressed();
			}
		});
		return btDown;
	}
	
	
	public abstract void newButtonPressed();
	
	public abstract void editButtonPressed();
	
	public abstract void removeButtonPressed();
	
	public abstract void upButtonPressed();
	
	public abstract void downButtonPressed();
	
	protected boolean isEditSupported() {
		return true;
	}
	
}
