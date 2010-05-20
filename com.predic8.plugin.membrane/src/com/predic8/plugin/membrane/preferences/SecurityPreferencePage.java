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
package com.predic8.plugin.membrane.preferences;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class SecurityPreferencePage extends PreferencePage implements
		IWorkbenchPreferencePage {

	public static final String PAGE_ID = "com.predic8.plugin.membrane.preferences.SecurityPreferencePage";
	
	private static final int PASSWORD_WIDTH_HINT = 100;
	
	private static final int LOCATION_WIDTH_HINT = 270;
	
	private Text textKeyLocation;
	
	private Text textKeyPassword;
	
	private Text textTrustLocation;
	
	private Text textTrustPassword;
	
	public SecurityPreferencePage() {
		
	}

	public SecurityPreferencePage(String title) {
		super(title);
		setDescription("Provides settings for security options.");
	}

	public SecurityPreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new RowLayout(SWT.VERTICAL));
		
		new Label(composite, SWT.NONE).setText(" ");
		

		GridData lbGridData = new GridData(GridData.FILL_HORIZONTAL);
		lbGridData.grabExcessHorizontalSpace = true;
		
		Group groupKey = createStoreGroup(composite, "Keystore");
		
		new Label(groupKey, SWT.NONE).setText("Location:");
		textKeyLocation = createText(groupKey, SWT.NONE, LOCATION_WIDTH_HINT, 2);
		textKeyLocation.setText(getSavedKeystoreLocation());
		
		
		Button bt1 = createFileBrowserButton(groupKey);
		bt1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String selected = openFileDialog();
				if (selected != null)
					textKeyLocation.setText(selected);
			}
		});
		
		new Label(groupKey, SWT.NONE).setText("Password:");
		textKeyPassword = createText(groupKey, SWT.PASSWORD, PASSWORD_WIDTH_HINT, 1);
		textKeyPassword.setText(getSavedKeystorePassword());
		
		Label lbKeyDummy = new Label(groupKey, SWT.NONE);
		lbKeyDummy.setText(" ");
		lbKeyDummy.setLayoutData(lbGridData);
		
		Label lbKeyDummy2 = new Label(groupKey, SWT.NONE);
		lbKeyDummy2.setText(" ");
		lbKeyDummy2.setLayoutData(lbGridData);
		
		new Label(composite, SWT.NONE).setText(" ");
		

		Group groupTrust = createStoreGroup(composite, "Truststore");
		
		new Label(groupTrust, SWT.NONE).setText("Location:");
		
		textTrustLocation = createText(groupTrust, SWT.NONE, LOCATION_WIDTH_HINT, 2);
		textTrustLocation.setText(getSavedTruststoreLocation());
		
		Button bt2 = createFileBrowserButton(groupTrust);
		bt2.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String selected = openFileDialog();
				if (selected != null)
					textTrustLocation.setText(selected);
			}
		});
		
		new Label(groupTrust, SWT.NONE).setText("Password:");
		textTrustPassword = createText(groupTrust, SWT.PASSWORD, PASSWORD_WIDTH_HINT, 1);
		textTrustPassword.setText(getSavedTruststorePassword());
		
		Label lbTrusDummy = new Label(groupTrust, SWT.NONE);
		lbTrusDummy.setText(" ");
		lbTrusDummy.setLayoutData(lbGridData);
		
		Label lbTrusDummy2 = new Label(groupTrust, SWT.NONE);
		lbTrusDummy2.setText(" ");
		lbTrusDummy2.setLayoutData(lbGridData);
		
		return composite;
	}

	private String getSavedKeystoreLocation() {
		return getConfiguration().getKeyStoreLocation() == null ? "" : getConfiguration().getKeyStoreLocation();  
	}

	private Configuration getConfiguration() {
		return Router.getInstance().getConfigurationManager().getConfiguration();
	}
	
	private String getSavedKeystorePassword() {
		return getConfiguration().getKeyStorePassword() == null ? "" : getConfiguration().getKeyStorePassword();  
	}
	
	private String getSavedTruststoreLocation() {
		return getConfiguration().getTrustStoreLocation() == null ? "" : getConfiguration().getTrustStoreLocation();  
	}
	
	private String getSavedTruststorePassword() {
		return getConfiguration().getTrustStorePassword() == null ? "" : getConfiguration().getTrustStorePassword();  
	}
	
	
	private Button createFileBrowserButton(Composite parent) {
		Button bt = new Button(parent, SWT.PUSH); 
		bt.setImage(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FOLDER).createImage());
		GridData g = new GridData();
		g.heightHint = 20;
		g.widthHint = 20;
		
		bt.setLayoutData(g);
		return bt;
	}
	
	private Group createStoreGroup(Composite composite, String text) {
		Group group = new Group(composite, SWT.NONE);
		group.setText(text);
		GridLayout layout = new GridLayout();
		layout.numColumns = 4;
		group.setLayout(layout);
		return group;
	}

	private Text createText(Composite parent, int type, int width, int span) {
		Text text = new Text(parent, type | SWT.BORDER);
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.widthHint = width;
		gData.horizontalSpan = span;
		text.setLayoutData(gData);
		
		return text;
	}
	
	public void init(IWorkbench workbench) {
		setPreferenceStore(MembraneUIPlugin.getDefault().getPreferenceStore());
	}

	private String openFileDialog() {
		FileDialog dialog = new FileDialog(Display.getCurrent().getActiveShell(), SWT.OPEN);
		dialog.setText("Open");
		dialog.setFilterExtensions(new String[] { "*.*", "*.txt", "*.doc", ".rtf", "*.jks*" });
		String selected = dialog.open();
		return selected;
	}
	
	private void setAndSaveSacurityInformations() {
		if (isEmptyInput())
			return;
		
		getConfiguration().setKeyStoreLocation(textKeyLocation.getText());
		getConfiguration().setKeyStorePassword(textKeyPassword.getText());
		getConfiguration().setTrustStoreLocation(textTrustLocation.getText());
		getConfiguration().setTrustStorePassword(textTrustPassword.getText());
	
		Router.getInstance().getConfigurationManager().setSecuritySystemProperties();
		
		try {
			Router.getInstance().getConfigurationManager().saveConfiguration(Router.getInstance().getConfigurationManager().getDefaultConfigurationFile());
		} catch (Exception e) {
			e.printStackTrace();
			MessageDialog.openError(Display.getCurrent().getActiveShell(), "Error", "Unable to save configuration: " + e.getMessage());
		}
			
	}
	
	@Override
	protected void performApply() {
		setAndSaveSacurityInformations();
	}

	@Override
	public boolean performOk() {
		setAndSaveSacurityInformations();
		return true;
	}

	private boolean isEmptyInput() {
		return textKeyLocation.getText().trim().equals("") &&
				textTrustLocation.getText().trim().equals("");
	}
	
}
