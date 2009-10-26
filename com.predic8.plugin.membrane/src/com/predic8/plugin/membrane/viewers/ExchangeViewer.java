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

package com.predic8.plugin.membrane.viewers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.components.RequestComp;
import com.predic8.plugin.membrane.components.ResponseComp;
import com.predic8.plugin.membrane.providers.ExchangeViewerContentProvider;
import com.predic8.plugin.membrane.resources.ImageKeys;

public class ExchangeViewer extends Composite {

	private SashForm vSashForm;

	private RequestComp requestComp;

	private ResponseComp responseComp;

	private Exchange exchange;

	private ExchangeViewerContentProvider contentProvider;

	private Composite requestPartComposite, responsePartComposite;

	private ToolBar toolBarRequest, toolBarResponse;

	private ToolItem itemContinueRequest, itemContinueResponse;

	private ToolItem itemResendRequest;

	private ToolItem itemFormatRequest, itemFormatResponse;

	
	private ToolItem itemSaveRequest, itemSaveResponse; 
	
	public ExchangeViewer(Composite parent, int style) {
		super(parent, style);
		// topViewer = this;
		setLayout(new FillLayout());

		vSashForm = new SashForm(this, SWT.VERTICAL);
		vSashForm.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));

		GridLayout requestLayout = new GridLayout();
		requestPartComposite = new Composite(vSashForm, SWT.NONE);
		requestPartComposite.setLayout(requestLayout);

		toolBarRequest = new ToolBar(requestPartComposite, SWT.NONE);
		toolBarRequest.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		itemContinueRequest = new ToolItem(toolBarRequest, SWT.PUSH);
		itemContinueRequest.setText("Continue");
		ImageDescriptor descriptorContinueRequest = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FLAG_GREEN);

		Image iconContinueRequest = descriptorContinueRequest.createImage();
		itemContinueRequest.setImage(iconContinueRequest);
		itemContinueRequest.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				requestComp.continuePressed();
			}
		});
		itemContinueRequest.setEnabled(false);

		itemResendRequest = new ToolItem(toolBarRequest, SWT.PUSH);
		itemResendRequest.setText("Resend");
		ImageDescriptor descriptorResend = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_ARROW_REDO);

		Image iconResend = descriptorResend.createImage();
		itemResendRequest.setImage(iconResend);
		itemResendRequest.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				requestComp.resendRequest();
			}
		});

		itemFormatRequest = new ToolItem(toolBarRequest, SWT.PUSH);
		itemFormatRequest.setText("Format");
		ImageDescriptor descriptor = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FORMAT);

		Image icon = descriptor.createImage();
		itemFormatRequest.setImage(icon);
		itemFormatRequest.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				requestComp.beautifyBody();
			}
		});
		itemFormatRequest.setEnabled(false);

		
		itemSaveRequest = new ToolItem(toolBarRequest, SWT.PUSH);
		itemSaveRequest.setText("Save");
		ImageDescriptor descriptorSaveRequest = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_SAVE_MESSAGE);
		
		Image iconSaveRequest = descriptorSaveRequest.createImage();
		itemSaveRequest.setImage(iconSaveRequest);
		itemSaveRequest.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					saveMessage(requestComp.getMsg());
				} catch (Exception e1) {
					MessageDialog.openError(ExchangeViewer.this.getShell(), "Save Error", e1.getMessage());
				}
			}
		});
		itemSaveRequest.setEnabled(false);
		
		requestComp = new RequestComp(requestPartComposite, SWT.NONE, this);
		requestComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		GridLayout responseLayout = new GridLayout();
		responsePartComposite = new Composite(vSashForm, SWT.NONE);
		responsePartComposite.setLayout(responseLayout);

		toolBarResponse = new ToolBar(responsePartComposite, SWT.NONE);

		itemContinueResponse = new ToolItem(toolBarResponse, SWT.PUSH);
		itemContinueResponse.setText("Continue");
		ImageDescriptor descriptorContinueResponse = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FLAG_GREEN);

		Image iconContinueResponse = descriptorContinueResponse.createImage();
		itemContinueResponse.setImage(iconContinueResponse);
		itemContinueResponse.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				responseComp.continuePressed();
			}
		});
		itemContinueResponse.setEnabled(false);

		itemFormatResponse = new ToolItem(toolBarResponse, SWT.PUSH);
		itemFormatResponse.setText("Format");
		ImageDescriptor descriptor2 = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FORMAT);

		Image icon2 = descriptor2.createImage();
		itemFormatResponse.setImage(icon2);
		itemFormatResponse.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				responseComp.beautifyBody();
			}
		});
		itemFormatResponse.setEnabled(false);

		
		itemSaveResponse = new ToolItem(toolBarResponse, SWT.PUSH);
		itemSaveResponse.setText("Save");
		ImageDescriptor descriptorSaveResponse = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_SAVE_MESSAGE);

		Image iconSaveResponse = descriptorSaveResponse.createImage();
		itemSaveResponse.setImage(iconSaveResponse);
		itemSaveResponse.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					saveMessage(responseComp.getMsg());
				} catch (Exception e1) {
					MessageDialog.openError(ExchangeViewer.this.getShell(), "Save Error", e1.getMessage());
				}
			}
		});
		itemSaveResponse.setEnabled(false);
		
		
		responseComp = new ResponseComp(responsePartComposite, SWT.NONE, this);
		responseComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		vSashForm.setWeights(new int[] { 1, 1 });
	}

	private void saveMessage(Message message) throws Exception {
		if (message == null) 
			return;
		
		String extension = "txt";
		boolean image = false;
		
		if (message.hasBody()) {
			if (message.getHeader().getContentType() != null) {
				if (message.isCSS()) {
					extension = "css";
				} else if (message.isHTML()) {
					extension = "html";
				} else if (message.isJavaScript()) {
					extension = "js";
				} else if (message.isXML()) {
					extension = "xml";
				} else if (message.isImage()) {
					image = true;
					String contentType = message.getHeader().getContentType();
					if (contentType.contains("jpeg")) {
						extension = "jpg";
					} else if (contentType.contains("gif")) {
						extension = "gif";
					} else if (contentType.contains("png")) {
						extension = "png";
					} else if (contentType.contains("bmp")) {
						extension = "bmp";
					} else {
						extension = "bmp";
					}
				}  
			}
		} 
		
		FileDialog fd = new FileDialog(getShell(), SWT.SAVE);
		fd.setText("Save Message");
		fd.setFilterPath("C:/");
		
		
        String[] filterExt = { "*." + extension};
        fd.setFilterExtensions(filterExt);
        String selected = fd.open();
        if (selected != null && !selected.equals("")) {
        	OutputStream writer = null;
        	try {
        		File file = new File(selected);
            	if (!file.exists()) {
            		file.createNewFile();
            	}
            	writer = new FileOutputStream(file);
            	if (message.getBody() != null && message.getBody() != null && message.getBody().getContent().length > 0) {
            		if (image) {
            			writer.write(message.getBody().getContent());
            			writer.flush();
            		} else {
            			PrintWriter printer = new PrintWriter(writer);
            			printer.write(new String(message.getBody().getContent()));
            			printer.flush();
            		}
            	} else {
            		PrintWriter printer = new PrintWriter(writer);
            		String headerText = new String(message.getHeader().toString());
            		printer.write(headerText);
            		printer.flush();
            	}
        	} finally {
        		if (writer != null) {
        			try {
						writer.close();
					} catch (IOException e) {
						throw e;
					}
        		}
        	}
        }
	}
	
	public Exchange getExchange() {
		return exchange;
	}

	public void setInput(Exchange newExchange) {
		if (contentProvider == null)
			return;

		contentProvider.inputChanged(this, exchange, newExchange);
		this.exchange = newExchange;
		setRequest(contentProvider.getRequest(exchange));
		setResponse(contentProvider.getResponse(exchange));
		refreshAll();
	}

	public void refreshAll() {
		requestComp.doUpdate();
		responseComp.doUpdate();
	}

	public void setRequest(Request request) {
		requestComp.setMsg(request);

	}

	public void setResponse(Response response) {
		responseComp.setMsg(response);
	}

	public void setContentProvider(ExchangeViewerContentProvider provider) {
		contentProvider = provider;
	}

	public void updateUIStatus() {
		if (exchange == null) {
			itemResendRequest.setEnabled(false);
		} else {
			if (exchange.getStatus() != ExchangeState.STARTED) {
				itemResendRequest.setEnabled(true);
			} else {
				itemResendRequest.setEnabled(false);
			}

			if (exchange.getRule().isBlockRequest()) {
				itemContinueRequest.setEnabled(requestComp.isContinueEnabled());
			} else {
				itemContinueRequest.setEnabled(false);
			}

			if (exchange.getRule().isBlockResponse() && exchange.getStatus() != ExchangeState.COMPLETED && exchange.getStatus() != ExchangeState.FAILED && exchange.getResponse() != null) {
				itemContinueResponse.setEnabled(responseComp.isContinueEnabled());
			} else {
				itemContinueResponse.setEnabled(false);
			}

		}
		requestComp.updateUIStatus(exchange);
		responseComp.updateUIStatus(exchange);
	}

	public boolean areBodiesChanged() {
		return requestComp.isBodyModified() || responseComp.isBodyModified();
	}

	public RequestComp getRequestComp() {
		return requestComp;
	}

	public ResponseComp getResponseComp() {
		return responseComp;
	}

	public void copyBodiesFromGUIToModel() {
		requestComp.copyBodyFromGUIToModel();
		responseComp.copyBodyFromGUIToModel();
	}

	public void setRequestFormatEnabled(boolean status) {
		itemFormatRequest.setEnabled(status);
	}

	public void setResponseFormatEnabled(boolean status) {
		itemFormatResponse.setEnabled(status);
	}

	
	public void setRequestSaveEnabled(boolean status) {
		itemSaveRequest.setEnabled(status);
	}

	public void setResponseSaveEnabled(boolean status) {
		itemSaveResponse.setEnabled(status);
	}
	
	
}