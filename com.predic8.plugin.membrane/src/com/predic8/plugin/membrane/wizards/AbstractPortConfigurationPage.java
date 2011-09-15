package com.predic8.plugin.membrane.wizards;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import com.predic8.plugin.membrane.listeners.PortVerifyListener;

public abstract class AbstractPortConfigurationPage extends AbstractProxyWizardPage {

	protected Text listenPortText;
	
	protected AbstractPortConfigurationPage(String pageName) {
		super(pageName);
	}

	protected Text createListenPortText(Composite parent) {
		Text text = new Text(parent, SWT.BORDER);
		text.addVerifyListener(new PortVerifyListener());
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		text.setText("" + getRuleManager().getDefaultListenPort());
		text.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent e) {
				Text source = (Text)e.getSource();
				
				if (source.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Listen port must be specified");
				} else if (source.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(source.getText()) > 65535) {
							setErrorMessage("Listen port number has an upper bound 65535.");
							setPageComplete(false);
						}
					} catch (NumberFormatException nfe) {
						setErrorMessage("Specified listen port must be in decimal number format.");
						setPageComplete(false);
					}
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
		});
		return text;
	}
	
	protected void createListenPortLabel(Composite composite) {
		Label label = new Label(composite, SWT.NONE);
		GridData gd = new GridData();
		gd.horizontalSpan = 1;
		label.setLayoutData(gd);
		label.setText("Listen Port:");
	}
	
	protected int getListenPort() {
		return Integer.parseInt(listenPortText.getText());
	}
}
