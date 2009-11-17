package com.predic8.plugin.membrane.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class MembraneMonitorPreferencePage extends PreferencePage implements
		IWorkbenchPreferencePage {

	public static final String PAGE_ID = "com.predic8.plugin.membrane.preferences.MembraneMonitorPreferencePage";
	
	//private Text textPath;
	
	public MembraneMonitorPreferencePage() {
		
	}

	public MembraneMonitorPreferencePage(String title) {
		super(title);
		setDescription("Provides global settings for membrane SOAP monitor.");
	}

	public MembraneMonitorPreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.marginTop = 25;
		gridLayout.marginLeft = 15;
		gridLayout.marginBottom = 15;
		gridLayout.marginRight = 15;
		gridLayout.verticalSpacing = 10;
		composite.setLayout(gridLayout);
		
//		Label labelPath = new Label(composite, SWT.NONE);
//		labelPath.setText("Select a default directory for configuration store.");
//		GridData lbGridData = new GridData();
//		lbGridData.horizontalAlignment = GridData.HORIZONTAL_ALIGN_BEGINNING;
//		lbGridData.horizontalSpan = 2;
//		lbGridData.grabExcessHorizontalSpace = false;
//		labelPath.setLayoutData(lbGridData);
//		
//		
//		Button btDirectoryChooser = new Button(composite, SWT.PUSH);
//		btDirectoryChooser.setText("select");
//		GridData btGridData = new GridData();
//		btGridData.horizontalAlignment = GridData.HORIZONTAL_ALIGN_BEGINNING;
//		btGridData.widthHint = 40;
//		btGridData.horizontalSpan = 1;
//		btGridData.grabExcessHorizontalSpace = false;
//		btDirectoryChooser.setLayoutData(btGridData);
//		btDirectoryChooser.addSelectionListener(new SelectionListener() {
//			
//			public void widgetSelected(SelectionEvent e) {
//				DirectoryDialog fd = new DirectoryDialog(getShell(), SWT.SAVE);
//				fd.setText("Default Directory for Configuration Store");
//				fd.setFilterPath("C:/");
//		        String selected = fd.open();
//		        if (selected != null && !selected.equals("")) {
//		        	textPath.setText(selected);
//		        }
//			}
//			
//			public void widgetDefaultSelected(SelectionEvent e) {
//				
//				
//			}
//		});
//		
//		textPath = new Text(composite, SWT.BORDER);
//		GridData textPathGridData = new GridData();
//		textPathGridData.horizontalAlignment = GridData.FILL;
//		textPathGridData.horizontalSpan = 1;
//		textPathGridData.grabExcessHorizontalSpace = true;
//		textPath.setLayoutData(textPathGridData);
		
		
		return composite;
	}

	public void init(IWorkbench workbench) {
		
	}

}
