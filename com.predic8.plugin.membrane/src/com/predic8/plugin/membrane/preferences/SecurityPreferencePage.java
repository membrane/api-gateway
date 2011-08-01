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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.KeyStore;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
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

import com.predic8.membrane.core.Proxies;
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
	
	private Button btShowKeyStoreContent;
	
	private Button btShowTrustStoreContent;
	
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
		textKeyLocation = createLocationTextWidget(groupKey, getSavedKeystoreLocation());
		textKeyLocation.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				btShowKeyStoreContent.setEnabled(!textKeyLocation.getText().equals(""));
			}
		});
		
		createFileBrowserButton(groupKey, textKeyLocation);
		
		new Label(groupKey, SWT.NONE).setText("Password:");
		textKeyPassword = createText(groupKey, SWT.PASSWORD, PASSWORD_WIDTH_HINT, 1);
		textKeyPassword.setText(getSavedKeystorePassword());
		
		addDummyLabels(groupKey, 7);
		
		btShowKeyStoreContent = createShowContentButton(groupKey, textKeyLocation, textKeyPassword);
	
		addDummyLabels(groupKey, 2);
		
		new Label(composite, SWT.NONE).setText(" ");
		
		
		Group groupTrust = createStoreGroup(composite, "Truststore");
		
		new Label(groupTrust, SWT.NONE).setText("Location:");
		
		textTrustLocation = createLocationTextWidget(groupTrust, getSavedTruststoreLocation());
		textTrustLocation.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				btShowTrustStoreContent.setEnabled(!textTrustLocation.getText().equals(""));
			}
		});
		
		createFileBrowserButton(groupTrust, textTrustLocation);
		
		new Label(groupTrust, SWT.NONE).setText("Password:");
		textTrustPassword = createText(groupTrust, SWT.PASSWORD, PASSWORD_WIDTH_HINT, 1);
		textTrustPassword.setText(getSavedTruststorePassword());
		
		addDummyLabels(groupTrust, 7);
		
		btShowTrustStoreContent = createShowContentButton(groupTrust, textTrustLocation, textTrustPassword);
		return composite;
	}

	private Text createLocationTextWidget(Composite parent, String initValue) {
		final Text text = createText(parent, SWT.NONE, LOCATION_WIDTH_HINT, 2);
		text.setText(initValue);
		return text;
	}
	
	private Button createShowContentButton(Composite parent, final Text location, final Text password) {
		Button bt = new Button(parent, SWT.PUSH);
		bt.setText("Show Content");
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showStoreContent(location.getText(), password.getText());
			}
		});
		bt.setEnabled(false);
		return bt;
	}
	
	private void showStoreContent(String location, String password) {
		try {
			KeyStore store = getStore(location, password);
			KeyStoreContentDialog dialog = new KeyStoreContentDialog(getShell(), store, password);
			dialog.open();
		} catch (Exception ex) {
			openError("Error", ex.getMessage());
		} 
	}
	
	private void addDummyLabels(Composite parent, int c) {
		for(int i = 0; i < c; i ++) {
			addDummyLabel(parent);
		}
	}
	
	private void addDummyLabel(Composite parent) {
		GridData lbGridData = new GridData(GridData.FILL_HORIZONTAL);
		lbGridData.grabExcessHorizontalSpace = true;
		
		Label lbKeyDummy8 = new Label(parent, SWT.NONE);
		lbKeyDummy8.setText(" ");
		lbKeyDummy8.setLayoutData(lbGridData);
	}
	
	private String getSavedKeystoreLocation() {
		return getConfiguration().getKeyStoreLocation() == null ? "" : getConfiguration().getKeyStoreLocation();  
	}

	private Proxies getConfiguration() {
		return Router.getInstance().getConfigurationManager().getProxies();
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
	
	
	private void createFileBrowserButton(Composite parent, final Text linkedText) {
		Button bt = new Button(parent, SWT.PUSH); 
		bt.setImage(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FOLDER).createImage());
		GridData g = new GridData();
		g.heightHint = 20;
		g.widthHint = 20;
		bt.setLayoutData(g);
		
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String selected = openFileDialog();
				if (selected != null)
					linkedText.setText(selected);
			}
		});
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
		return dialog.open();
	}
	
	private void setAndSaveSacurityInformations() {
		if (!checkStore(textKeyLocation.getText(), textKeyPassword.getText(), "Key Store"))
			return;
		
		if (!checkStore(textTrustLocation.getText(), textTrustPassword.getText(), "Trust Store"))
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

	private boolean checkStore(String location, String password, String storeName) {
		try {
			getStore(location, password);
			return true;
		} catch (FileNotFoundException fe) {
			openError( storeName + " validation failed!", "Unable to read" +  storeName  + " file. The path you have specified may be invalid.");
			return false;
		} catch (Exception e) {
			openError(storeName + " validation failed!", e.getMessage());
			return false;
		}
	}
	
	private void openError(String title, String message) {
		ErrorDialog.openError(this.getShell(), "Validation Error", title, new Status(IStatus.ERROR, MembraneUIPlugin.PLUGIN_ID, message));
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
	
	private KeyStore getStore(String file, String password) throws FileNotFoundException, Exception {
		if ("".equals(file.trim()))
			return null;
		
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
	    FileInputStream fis = null;
	    try {
	        fis = new java.io.FileInputStream(file);
	        ks.load(fis, password.toCharArray());
	    } finally {
	        if (fis != null) {
	            fis.close();
	        }
	    }
	    return ks;
	}
	
}
