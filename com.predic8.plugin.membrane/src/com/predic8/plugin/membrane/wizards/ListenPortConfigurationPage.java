package com.predic8.plugin.membrane.wizards;

import java.io.IOException;
import java.net.ServerSocket;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Core;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.plugin.membrane.components.PortVerifyListener;

public class ListenPortConfigurationPage extends WizardPage {

	public static final String PAGE_NAME = "Listen Port Configuration";
	
	private Text ruleOptionsListenPortTextField;
	
	protected ListenPortConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Simple Rule");
		setDescription("Specify Listen Port");
	}

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 2;
		gridLayout.marginBottom = 10;
		gridLayout.marginRight = 10;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);
		
		
	
		Label labelFullDescription = new Label(composite, SWT.WRAP);
		labelFullDescription.setText("A rule is listenening on a TCP port for incomming connections.\n" + "The port number can be any integer between 1 and 65535.");
		labelFullDescription.setBounds(120, 10, 100, 100);
		
		GridData gridData4ListenDescrLabel = new GridData();
		gridData4ListenDescrLabel.horizontalSpan = 2;
		gridData4ListenDescrLabel.verticalSpan = 2;
		labelFullDescription.setLayoutData(gridData4ListenDescrLabel);
		
		Label listenPortLabel = new Label(composite, SWT.NONE);
		GridData gridData4ListenPortLabel = new GridData();
		gridData4ListenPortLabel.horizontalSpan = 1;
		listenPortLabel.setLayoutData(gridData4ListenPortLabel);
		listenPortLabel.setText("Listen Port:");

		
		ruleOptionsListenPortTextField = new Text(composite,SWT.BORDER);
		ruleOptionsListenPortTextField.addVerifyListener(new PortVerifyListener());
		ruleOptionsListenPortTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		ruleOptionsListenPortTextField.setText(Core.getRuleManager().getDefaultListenPort());
		ruleOptionsListenPortTextField.addModifyListener(new ModifyListener() {
			
			public void modifyText(ModifyEvent e) {
				if (ruleOptionsListenPortTextField.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Listen port must be specified");
				} else if (ruleOptionsListenPortTextField.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(ruleOptionsListenPortTextField.getText()) > 65535) {
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
		
		setControl(composite);
	}

	@Override
	public boolean canFlipToNextPage() {
		if (!isPageComplete())
			return false;
		try {
			if (((HttpTransport) Core.getTransport()).isAnyThreadListeningAt(Integer.parseInt(ruleOptionsListenPortTextField.getText()))) {
				return true;
			}
			new ServerSocket(Integer.parseInt(ruleOptionsListenPortTextField.getText())).close();
			return true;
		} catch (IOException ex) {
			setErrorMessage("Port is already in use. Please choose a different port!");
			return false;
		} 
	}
	
	@Override
	public IWizardPage getNextPage() {
		return getWizard().getPage(TargetHostConfigurationPage.PAGE_NAME);
	}
	
	public String getListenPort() {
		return ruleOptionsListenPortTextField.getText();
	}
	
}
