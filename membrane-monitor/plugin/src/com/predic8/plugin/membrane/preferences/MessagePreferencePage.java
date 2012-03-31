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
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.predic8.membrane.core.Proxies;
import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.MembraneUIPlugin;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class MessagePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	public static final String PAGE_ID = "com.predic8.plugin.membrane.preferences.MessagePreferencePage";
	
	private Button indentmsg;
	private Button adjhosthead;

	public MessagePreferencePage() {

	}

	public MessagePreferencePage(String title) {
		super(title);

	}

	public MessagePreferencePage(String title, ImageDescriptor image) {
		super(title, image);
		setDescription("Provides settings for Message options.");
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new RowLayout(SWT.VERTICAL));

		Proxies config = Router.getInstance().getConfigurationManager().getProxies();

		indentmsg = new Button(comp, SWT.CHECK);
		indentmsg.setText("Indent Message");
		indentmsg.setSelection(config.getIndentMessage());

		adjhosthead = new Button(comp, SWT.CHECK);
		adjhosthead.setText("Adjust Host Header Field");
		adjhosthead.setSelection(config.getAdjustHostHeader());

		return comp;
	}

	public void init(IWorkbench workbench) {
		setPreferenceStore(MembraneUIPlugin.getDefault().getPreferenceStore());
	}

	@Override
	protected void performApply() {
		setAndSaveConfig();
	}

	@Override
	public boolean performOk() {
		setAndSaveConfig();
		return true;
	}

	private void setAndSaveConfig() {
		Router.getInstance().getConfigurationManager().getProxies().setIndentMessage(indentmsg.getSelection());
		Router.getInstance().getConfigurationManager().getProxies().setAdjustHostHeader(adjhosthead.getSelection());
		
		try {
			Router.getInstance().getConfigurationManager().saveConfiguration(Router.getInstance().getConfigurationManager().getDefaultConfigurationFile());
		} catch (Exception e) {
			e.printStackTrace();
			MessageDialog.openError(Display.getCurrent().getActiveShell(), "Error", "Unable to save configuration: " + e.getMessage());
		}
	}
	
}