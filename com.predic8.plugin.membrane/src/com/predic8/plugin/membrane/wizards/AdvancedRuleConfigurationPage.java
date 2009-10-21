package com.predic8.plugin.membrane.wizards;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.predic8.membrane.core.Core;
import com.predic8.plugin.membrane.components.PortVerifyListener;

public class AdvancedRuleConfigurationPage extends WizardPage {

	public static final String PAGE_NAME = "Advanced Rule Configuration";
	
	private Text listenPortTextField;

	private Combo methodCombo;

	private Text pathTextField;

	private Text listenHostTextField;
	
	private Text targetPortTextField;

	private Text targetHostTextField;
	
	protected AdvancedRuleConfigurationPage() {
		super(PAGE_NAME);
		setTitle("Advanced Rule");
		setDescription("Specify all rule configuration parameters");
	}

	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginTop = 10;
		gridLayout.marginLeft = 2;
		gridLayout.marginBottom = 10;
		gridLayout.marginRight = 10;
		gridLayout.verticalSpacing = 20;
		composite.setLayout(gridLayout);
		
		
		Group ruleKeyGroup = new Group(composite, SWT.NONE);
		ruleKeyGroup.setText("Rule Key");
		ruleKeyGroup.setLayoutData(new GridData( GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

		GridLayout gridLayout4RuleKeyGroup = new GridLayout();
		gridLayout4RuleKeyGroup.numColumns = 5;
		ruleKeyGroup.setLayout(gridLayout4RuleKeyGroup);

		
		Label listenHostLabel = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4ListenHostLabel = new GridData();
		gridData4ListenHostLabel.horizontalSpan = 2;
		listenHostLabel.setLayoutData(gridData4ListenHostLabel);
		listenHostLabel.setText("Host:");

		listenHostTextField = new Text(ruleKeyGroup, SWT.BORDER);
		GridData gridData4HostTextField = new GridData(GridData.FILL_HORIZONTAL);
		listenHostTextField.setLayoutData(gridData4HostTextField);
		listenHostTextField.setText(Core.getRuleManager().getDefaultHost());
		
		
		Label labelDummy0 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4LabelDummy0 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy0.setLayoutData(gridData4LabelDummy0);
		labelDummy0.setText(" ");
	
		Label labelDummy1 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4LabelDummy1 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy1.setLayoutData(gridData4LabelDummy1);
		labelDummy1.setText(" ");
		
		Label ruleOptionsListenPortLabel = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4ListenPortLabel = new GridData();
		gridData4ListenPortLabel.horizontalSpan = 2;
		ruleOptionsListenPortLabel.setLayoutData(gridData4ListenPortLabel);
		ruleOptionsListenPortLabel.setText("Listen Port:");

		
		listenPortTextField = new Text(ruleKeyGroup,SWT.BORDER);
		listenPortTextField.addVerifyListener(new PortVerifyListener());
		listenPortTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		listenPortTextField.setText(Core.getRuleManager().getDefaultListenPort());
		listenPortTextField.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if (listenPortTextField.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Listen port must be specified");
				} else if (listenPortTextField.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(listenPortTextField.getText()) > 65535) {
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
		
		Label labelDummy2 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4LabelDummy2 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy2.setLayoutData(gridData4LabelDummy2);
		labelDummy2.setText(" ");
		
		Label labelDummy3 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4LabelDummy3 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy3.setLayoutData(gridData4LabelDummy3);
		labelDummy3.setText(" ");
		
		Label ruleOptionsMethodLabel = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4MethodLabel = new GridData();
		gridData4MethodLabel.horizontalSpan = 2;
		ruleOptionsMethodLabel.setLayoutData(gridData4MethodLabel);
		ruleOptionsMethodLabel.setText("Method:");

		methodCombo = new Combo(ruleKeyGroup, SWT.READ_ONLY);
		methodCombo.setItems(new String[] { "POST", "GET", "DELETE", "PUT", " * " });
		methodCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		methodCombo.select(Core.getRuleManager().getDefaultMethod());
		
		Label labelDummy4 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4LabelDummy4 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy4.setLayoutData(gridData4LabelDummy4);
		labelDummy4.setText(" ");
	
		Label labelDummy5 = new Label(ruleKeyGroup, SWT.NONE);
		GridData gridData4LabelDummy5 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy5.setLayoutData(gridData4LabelDummy5);
		labelDummy5.setText(" ");
		
		Label ruleOptionsPathLabel = new Label(ruleKeyGroup, SWT.NONE);
		ruleOptionsPathLabel.setText("Path:");

		pathTextField = new Text(ruleKeyGroup, SWT.BORDER);
		GridData gridData4PathTextField = new GridData(GridData.FILL_HORIZONTAL);
		gridData4PathTextField.horizontalSpan = 4;
		pathTextField.setLayoutData(gridData4PathTextField);
		pathTextField.setText(Core.getRuleManager().getDefaultPath());
		
		Group ruleOptionsTargetGroup = new Group(composite, SWT.NONE);
		ruleOptionsTargetGroup.setText("Target");
		ruleOptionsTargetGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));
		GridLayout gridLayout4TargetGroup = new GridLayout();
		gridLayout4TargetGroup.numColumns = 4;
		ruleOptionsTargetGroup.setLayout(gridLayout4TargetGroup);

		Label ruleOptionsTargetHostLabel = new Label(ruleOptionsTargetGroup, SWT.NONE);
		ruleOptionsTargetHostLabel.setText("Host");

		targetHostTextField = new Text(ruleOptionsTargetGroup, SWT.BORDER);
		targetHostTextField.setText(Core.getRuleManager().getDefaultTargetHost());
		targetHostTextField.addModifyListener(new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				if (targetHostTextField.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("Target host must be specified");
				} else {
					setPageComplete(true);
					setErrorMessage(null);
				}
			}});
		targetHostTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label labelDummy6 = new Label(ruleOptionsTargetGroup, SWT.NONE);
		GridData gridData4LabelDummy6 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy6.setLayoutData(gridData4LabelDummy6);
		labelDummy6.setText(" ");
		
		Label labelDummy7 = new Label(ruleOptionsTargetGroup, SWT.NONE);
		GridData griddata4LabelDummy7 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy7.setLayoutData(griddata4LabelDummy7);
		labelDummy7.setText(" ");
		
		Label ruleOptionsTargetPortTextLabel = new Label(ruleOptionsTargetGroup, SWT.NONE);
		ruleOptionsTargetPortTextLabel.setText("Port");

		targetPortTextField = new Text(ruleOptionsTargetGroup,SWT.BORDER);
		targetPortTextField.setText(Core.getRuleManager().getDefaultTargetPort());
		targetPortTextField.addVerifyListener(new PortVerifyListener());
		targetPortTextField.addModifyListener(new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				if (targetPortTextField.getText().trim().equals("")) {
					setPageComplete(false);
					setErrorMessage("target host port must be specified");
				} else if (targetPortTextField.getText().trim().length() >= 5) {
					try {
						if (Integer.parseInt(targetPortTextField.getText()) > 65535) {
							setErrorMessage("Target host port number has an upper bound 65535.");
							setPageComplete(false);
						}
					} catch (NumberFormatException nfe) {
						setErrorMessage("Specified target host port must be in decimal number format.");
						setPageComplete(false);
					}
				} else {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}});
		targetPortTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Label labelDummy8 = new Label(ruleOptionsTargetGroup, SWT.NONE);
		GridData gridData4LabelDummy8 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy8.setLayoutData(gridData4LabelDummy8);
		labelDummy8.setText(" ");
	
		Label labelDummy9 = new Label(ruleOptionsTargetGroup, SWT.NONE);
		GridData griddata4LabelDummy9 = new GridData(GridData.FILL_HORIZONTAL);
		labelDummy9.setLayoutData(griddata4LabelDummy9);
		labelDummy9.setText(" ");
		
		setControl(composite);
	}

	@Override
	public IWizardPage getNextPage() {
		return null;
	}
	
	public String getListenPort() {
		return listenPortTextField.getText();
	}
	
	public String getPath() {
		return pathTextField.getText();
	}
	
	public String getListenHost() {
		return listenHostTextField.getText();
	}
	
	public String getMethod() {
		int index = methodCombo.getSelectionIndex();
		String method;
		if (index > -1)
			method = methodCombo.getItem(index);
		else
			method = "";
		return method;
	}
	
	public String getTargetHostPort() {
		return targetPortTextField.getText();
	}
	
	public String getTargetHost() {
		return targetHostTextField.getText();
	}
	
}
