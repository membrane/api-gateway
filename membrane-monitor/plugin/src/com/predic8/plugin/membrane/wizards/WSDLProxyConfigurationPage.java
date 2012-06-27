package com.predic8.plugin.membrane.wizards;

import java.io.IOException;
import java.net.URL;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor;
import com.predic8.membrane.core.rules.*;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.contentproviders.WSDLPortTableContentProvider;
import com.predic8.plugin.membrane.labelproviders.*;
import com.predic8.plugin.membrane.listeners.PortVerifyListener;
import com.predic8.plugin.membrane.util.SWTUtil;
import com.predic8.wsdl.*;


public class WSDLProxyConfigurationPage extends AbstractProxyWizardPage {

	public static final String PAGE_NAME = "WSDL Proxy Configuration";

	private Text textWSDL;

	private Button btAnalyze;

	private Button btRewriteEndpoint;

	private Button btValidateWSDL;

	protected CheckboxTableViewer tableViewer;

	private boolean canFinish;
	
	private String wsdl;
	
	private Combo comboRewriteWSDLProtocol;
	
	private Text textRewriteWSDLHost;
	
	private Text textRewriteWSDLPort;
	
	protected WSDLProxyConfigurationPage() {
		super(PAGE_NAME);
		setTitle("SOAP Proxy");
	}
	@Override
	public void createControl(Composite parent) {
		Composite composite = createComposite(parent, 3);
		createFullDescriptionLabel(composite, "Service Proxy from WSDL");
		textWSDL = createTextWSDL(composite);
		btAnalyze = createButtonAnalyze(composite);

		tableViewer = createTableViewer(composite);

		btValidateWSDL = createButtonCheck(composite, "Validate incomming SOAP messages against WSDL and Schema");
		
		btRewriteEndpoint = createButtonCheck(composite, "Rewrite endpoint and schema location in WSDL");
		btRewriteEndpoint.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Button bt = (Button)e.widget;
				comboRewriteWSDLProtocol.setEnabled(bt.getSelection());
				textRewriteWSDLHost.setEnabled(bt.getSelection());
				textRewriteWSDLPort.setEnabled(bt.getSelection());
			}
		});

		createRewriteWSDLDetailsComposite(composite);

		btRewriteEndpoint.setSelection(true);
		btRewriteEndpoint.notifyListeners(SWT.Selection, SWTUtil.createSelectionEvent(null, btRewriteEndpoint));
		
		setControl(composite);
	}

	private Text createTextWSDL(Composite parent) {
		Text text = createText(parent, 2);
		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				Text t = (Text)e.getSource();
				btAnalyze.setEnabled(t.getText().length() > 0);
			}
		});
		return text;
	}
	
	private Text createText(Composite parent, int span) {
		Text text = new Text(parent, SWT.BORDER);
		GridData gd = SWTUtil.getGreedyHorizontalGridData();
		gd.horizontalSpan = span;
		gd.heightHint = 16;
		text.setLayoutData(gd);
		return text;
	}

	private Button createButtonAnalyze(Composite parent) {
		Button bt = new Button(parent, SWT.NONE);
		bt.setEnabled(false);
		bt.setText("Analyze");
		GridData gd = new GridData();
		gd.horizontalSpan = 1;
		bt.setLayoutData(gd);
		bt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String text = textWSDL.getText();
				if (Constants.EMPTY_STRING.equals(text))
					return;

				WSDLParser parser = new WSDLParser();
				try {
					tableViewer.setInput(parser.parse(text));
					wsdl = text;
				} catch (Exception e1) {
					reset(); 
					ErrorDialog.openError(Display.getCurrent().getActiveShell(),  "WSDL Parse Error", "Parsing of specified WSDL failed!", new Status(IStatus.ERROR, MembraneUIPlugin.PLUGIN_ID, e1.getMessage()));
				}

			}
		});

		return bt;
	}

	private Button createButtonCheck(Composite parent, String text) {
		Button bt = new Button(parent, SWT.CHECK);
		bt.setText(text);
		GridData gd = new GridData();
		gd.horizontalSpan = 3;
		bt.setLayoutData(gd);
		return bt;
	}

	@Override
	public IWizardPage getNextPage() {
		return null;
	}

	private CheckboxTableViewer createTableViewer(Composite parent) {
		CheckboxTableViewer viewer = CheckboxTableViewer.newCheckList(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL); //new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER | SWT.VIRTUAL);
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.horizontalSpan = 3;
		gData.grabExcessVerticalSpace = true;
		gData.grabExcessHorizontalSpace = true;
		viewer.getTable().setLayoutData(gData);
		createColumns(viewer);

		viewer.setContentProvider(new WSDLPortTableContentProvider());
		viewer.setLabelProvider(new WSDLPortTableLabelProvider());

		viewer.addCheckStateListener(new ICheckStateListener() {
			@Override
			public void checkStateChanged(CheckStateChangedEvent event) {
				CheckboxTableViewer source = (CheckboxTableViewer)event.getSource();
				
				// here is a single selection behavior implemented
				if (event.getChecked()) {
					source.setCheckedElements(new Object[] {event.getElement()});
				} else {
					source.setAllChecked(false);
				}
				
				canFinish = event.getChecked();
				getWizard().getContainer().updateButtons();
				
			}
		});
		
		return viewer;
	}

	protected void createColumns(TableViewer table) {
		String[] titles = new String[] { " ", "Portname", "Protocol", "Address" };
		int[] bounds = new int[] { 30, 150, 60, 170 };

		for (int i = 0; i < titles.length; i++) {
			TableViewerColumn column = new TableViewerColumn(table, SWT.NONE);
			column.getViewer().setLabelProvider(new TableHeaderLabelProvider());
			column.getColumn().setAlignment(i == 0 ? SWT.CENTER : SWT.LEFT );
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);
		}
		table.getTable().setHeaderVisible(true);
		table.getTable().setLinesVisible(true);
	}
	
	@Override
	boolean canFinish() {
		return canFinish;
	}
	
	private void reset() {
		wsdl = null;
		tableViewer.setInput(null);
		canFinish = false;
	}
	
	@Override
	boolean performFinish(AddProxyWizard wizard) throws IOException {
		Port p = (Port)tableViewer.getCheckedElements()[0];
		getRuleManager().addProxyAndOpenPortIfNew(createServiceProxy(p));
		return true;
	}
	
	private ServiceProxy createServiceProxy(Port p) throws IOException {
		ServiceProxy proxy = new ServiceProxy();
		ServiceProxyKey key = new ServiceProxyKey(80);
		key.setMethod("*");
		proxy.setKey(key);
		
		URL url = new URL(p.getAddress().getLocation());
		key.setPath(url.getPath());
		
		proxy.setTargetHost(url.getHost());
		proxy.setTargetPort(url.getPort());
		
		if (btRewriteEndpoint.getSelection()) {
			proxy.getInterceptors().add(createWSDLInterceptor());
		}
		
		if (btValidateWSDL.getSelection()) {
			proxy.getInterceptors().add(createValidatorInterceptor());
		} 
		
		return proxy;
	}
	
	private Interceptor createWSDLInterceptor() {
		WSDLInterceptor interceptor = new WSDLInterceptor();
		
		if (comboRewriteWSDLProtocol.getSelectionIndex() > 0)
			interceptor.setProtocol(comboRewriteWSDLProtocol.getItem(comboRewriteWSDLProtocol.getSelectionIndex()));
		
		if (!Constants.EMPTY_STRING.equals(textRewriteWSDLHost.getText().trim()))
			interceptor.setHost(textRewriteWSDLHost.getText().trim());
			
		if (!Constants.EMPTY_STRING.equals(textRewriteWSDLPort.getText().trim()))
			interceptor.setPort(textRewriteWSDLPort.getText().trim());
		
		try {
			interceptor.init(Router.getInstance());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return interceptor;
	}
	
	private Interceptor createValidatorInterceptor() {
		ValidatorInterceptor interceptor = new ValidatorInterceptor();
		interceptor.setWsdl(wsdl);
		try {
			interceptor.init(Router.getInstance());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return interceptor;
	}
	
	private Composite createRewriteWSDLDetailsComposite(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		GridLayout layout = SWTUtil.createGridLayout(3, 10);
		layout.marginTop = 5;
		comp.setLayout(layout);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 3;
		comp.setLayoutData(gd);
		
		new Label(comp, SWT.NONE).setText("Protocol:");
		
		comboRewriteWSDLProtocol = createRewriteWSDLComboProtocol(comp); 
		
		new Label(comp, SWT.NONE).setText("");
		
		new Label(comp, SWT.NONE).setText("Host:");
		
		textRewriteWSDLHost = createText(comp, 2);
		
		new Label(comp, SWT.NONE).setText("Port:");
		
		textRewriteWSDLPort = createTextRewriteWSDLPort(comp);
		
		Label lb = new Label(comp, SWT.NONE);
		lb.setText("");
		lb.setLayoutData(SWTUtil.getGreedyHorizontalGridData());
		
		return comp;
	}
	
	
	private Combo createRewriteWSDLComboProtocol(Composite parent) {
		Combo combo = new Combo(parent, SWT.NONE);
		combo.setItems(new String[] {"From Request", "HTTP", "HTTPS" });
		combo.select(0);
		return combo;
	}

	private Text createTextRewriteWSDLPort(Composite parent) {
		Text text = new Text(parent, SWT.BORDER);
		text.addVerifyListener(new PortVerifyListener());
		GridData gd = new GridData();
		gd.horizontalSpan = 1;
		gd.heightHint = 16;
		gd.widthHint = 88;
		text.setLayoutData(gd);
		return text;
	}
	
}



