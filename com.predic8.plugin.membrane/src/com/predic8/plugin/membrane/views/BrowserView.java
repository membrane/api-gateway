package com.predic8.plugin.membrane.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.part.ViewPart;

import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class BrowserView extends ViewPart {

	public static final String VIEW_ID = "com.predic8.plugin.membrane.views.BrowserView";
	
	private Browser browser;
	
	private Text addressTextField;
	
	public BrowserView() {
		
	}

	@Override
	public void createPartControl(Composite parent) {
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 3;
		parent.setLayout(gridLayout);
		ToolBar toolbar = new ToolBar(parent, SWT.NONE);
		
		ToolItem itemNavigateBackword = new ToolItem(toolbar, SWT.PUSH);
		itemNavigateBackword.setImage( MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_ARROW_ROTATE_CLOCKWISE).createImage());
		itemNavigateBackword.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browser.back();
			}
		});
		
		ToolItem itemNavigateForward = new ToolItem(toolbar, SWT.PUSH);
		itemNavigateForward.setImage( MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_ARROW_ROTATE_COUNTER_CLOCKWISE).createImage());
		itemNavigateForward.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browser.forward();
			}
		});
		
		ToolItem itemStopOperation = new ToolItem(toolbar, SWT.PUSH);
		itemStopOperation.setImage(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_STOP_OPERATION).createImage());
		itemStopOperation.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browser.stop();
			}
		});
		
		ToolItem itemRefreshSite = new ToolItem(toolbar, SWT.PUSH);
		itemRefreshSite.setImage(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_ARROW_REFRESH).createImage());
		itemRefreshSite.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browser.refresh();
			}
		});
		
		ToolItem itemLaunch = new ToolItem(toolbar, SWT.PUSH);
		itemLaunch.setImage(MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_DOOR_IN).createImage());
		itemLaunch.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				browser.refresh();
			}
		});
		
		GridData data = new GridData();
		data.horizontalSpan = 3;
		toolbar.setLayoutData(data);

		Label labelAddress = new Label(parent, SWT.NONE);
		labelAddress.setText("Address");
		
		addressTextField = new Text(parent, SWT.BORDER);
		data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		data.horizontalSpan = 2;
		data.grabExcessHorizontalSpace = true;
		addressTextField.setLayoutData(data);

		try {
			browser = new Browser(parent, SWT.NONE);
		} catch (SWTError e) {
			e.printStackTrace();
			return;
		}
		data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.FILL;
		data.horizontalSpan = 3;
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		browser.setLayoutData(data);

		final Label status = new Label(parent, SWT.NONE);
		data = new GridData(GridData.FILL_HORIZONTAL);
		data.horizontalSpan = 2;
		status.setLayoutData(data);

		final ProgressBar progressBar = new ProgressBar(parent, SWT.NONE);
		data = new GridData();
		data.horizontalAlignment = GridData.END;
		progressBar.setLayoutData(data);

		browser.addProgressListener(new ProgressListener() {
			public void changed(ProgressEvent event) {
					if (event.total == 0) return;                            
					int ratio = event.current * 100 / event.total;
					progressBar.setSelection(ratio);
			}
			public void completed(ProgressEvent event) {
				progressBar.setSelection(0);
			}
		});
		browser.addStatusTextListener(new StatusTextListener() {
			public void changed(StatusTextEvent event) {
				status.setText(event.text);	
			}
		});
		browser.addLocationListener(new LocationListener() {
			public void changed(LocationEvent event) {
				if (event.top) addressTextField.setText(event.location);
			}
			public void changing(LocationEvent event) {
			}
		});
		
		addressTextField.addListener(SWT.DefaultSelection, new Listener() {
			public void handleEvent(Event e) {
				browser.setUrl(addressTextField.getText());
			}
		});

		browser.setUrl("http://www.predic8.com");

	}

	@Override
	public void setFocus() {
		addressTextField.setFocus();
	}

}
