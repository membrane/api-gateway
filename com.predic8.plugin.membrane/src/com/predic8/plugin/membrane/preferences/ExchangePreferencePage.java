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

import com.predic8.membrane.core.Router;
import com.predic8.plugin.membrane.MembraneUIPlugin;

public class ExchangePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	public static final String PAGE_ID = "com.predic8.plugin.membrane.preferences.ExchangePreferencePage";

	private Button autotrack;

	public ExchangePreferencePage() {

	}

	public ExchangePreferencePage(String title) {
		super(title);
		setDescription("Provides settings for Exchange options.");
	}

	public ExchangePreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	protected Control createContents(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new RowLayout(SWT.VERTICAL));
		autotrack = new Button(comp, SWT.CHECK);
		autotrack.setText("Autotrack New Exchanges");
		autotrack.setSelection(Router.getInstance().getConfigurationManager().getConfiguration().getTrackExchange());

		return comp;
	}

	public void init(IWorkbench workbench) {
		setPreferenceStore(MembraneUIPlugin.getDefault().getPreferenceStore());
	}

	@Override
	protected void performApply() {
		Router.getInstance().getConfigurationManager().getConfiguration().setTrackExchange(autotrack.getSelection());
		Router.getInstance().getConfigurationManager().saveConfiguration(Router.getInstance().getConfigurationManager().getDefaultConfigurationFile());
	}

	@Override
	public boolean performOk() {
		Router.getInstance().getConfigurationManager().getConfiguration().setTrackExchange(autotrack.getSelection());
		Router.getInstance().getConfigurationManager().saveConfiguration(Router.getInstance().getConfigurationManager().getDefaultConfigurationFile());
		return true;
	}

}
