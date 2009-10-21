package com.predic8.plugin.membrane.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Core;
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

		Configuration config = Core.getConfigurationManager().getConfiguration();

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

	protected void performApply() {
		Core.getConfigurationManager().getConfiguration().setIndentMessage(indentmsg.getSelection());
		Core.getConfigurationManager().getConfiguration().setAdjustHostHeader(adjhosthead.getSelection());
		
		Core.getConfigurationManager().saveConfiguration(Core.getConfigurationManager().getDefaultConfigurationFile());
	}
	
	@Override
	public boolean performOk() {
		Core.getConfigurationManager().getConfiguration().setIndentMessage(indentmsg.getSelection());
		Core.getConfigurationManager().getConfiguration().setAdjustHostHeader(adjhosthead.getSelection());
		
		Core.getConfigurationManager().saveConfiguration(Core.getConfigurationManager().getDefaultConfigurationFile());
		return true;
	}
	
}