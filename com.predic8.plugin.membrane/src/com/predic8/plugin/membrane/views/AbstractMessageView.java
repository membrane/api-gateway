package com.predic8.plugin.membrane.views;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.part.ViewPart;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.plugin.membrane.MembraneUIPlugin;
import com.predic8.plugin.membrane.providers.MessageViewContentProvider;
import com.predic8.plugin.membrane.resources.ImageKeys;
import com.predic8.plugin.membrane.viewcomponents.BaseComp;
import com.predic8.plugin.membrane.viewcomponents.IBaseCompositeHost;

public abstract class AbstractMessageView extends ViewPart implements IBaseCompositeHost {

	protected Exchange exchange;
	
	private String savePath;
	
	protected Composite partComposite;
	
	protected ToolBar toolBar;
	
	protected ToolItem itemContinue, itemFormat, itemSave;
	
	protected BaseComp baseComp;
	
	protected MessageViewContentProvider contentProvider;
	
	@Override
	public void createPartControl(Composite parent) {
		GridLayout responseLayout = new GridLayout();
		partComposite = new Composite(parent, SWT.NONE);
		partComposite.setLayout(responseLayout);

		toolBar = new ToolBar(partComposite, SWT.NONE);
		
		itemContinue = new ToolItem(toolBar, SWT.PUSH);
		itemContinue.setText("Continue");
		ImageDescriptor descriptorContinueResponse = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FLAG_GREEN);

		Image iconContinueResponse = descriptorContinueResponse.createImage();
		itemContinue.setImage(iconContinueResponse);
		itemContinue.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				baseComp.continuePressed();
			}
		});
		itemContinue.setEnabled(false);
		
		
		itemFormat = new ToolItem(toolBar, SWT.PUSH);
		itemFormat.setText("Format");
		ImageDescriptor descriptor2 = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_FORMAT);

		Image icon2 = descriptor2.createImage();
		itemFormat.setImage(icon2);
		itemFormat.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				baseComp.beautifyBody();
			}
		});
		itemFormat.setEnabled(false);

		
		itemSave = new ToolItem(toolBar, SWT.PUSH);
		itemSave.setText("Save");
		ImageDescriptor descriptorSaveResponse = MembraneUIPlugin.getDefault().getImageRegistry().getDescriptor(ImageKeys.IMAGE_SAVE_MESSAGE);

		Image iconSaveResponse = descriptorSaveResponse.createImage();
		itemSave.setImage(iconSaveResponse);
		itemSave.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				try {
					saveMessage(baseComp.getMsg());
				} catch (Exception e1) {
					MessageDialog.openError(partComposite.getShell(), "Save Error", e1.getMessage());
				}
			}
		});
		itemSave.setEnabled(false);
		
	}
	
	protected void saveMessage(Message message) throws Exception {
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
		
		FileDialog fd = new FileDialog(partComposite.getShell(), SWT.SAVE);
		fd.setText("Save Message");
		if (savePath != null && !savePath.equals("")) {
			fd.setFilterPath(savePath);
		} else {
			fd.setFilterPath("C:/");
		}
		
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
            	
            	savePath = file.getParent();
            	
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
	
	public void setInput(Exchange newExchange) {
		if (contentProvider == null)
			return;

		contentProvider.inputChanged(exchange, newExchange);
		this.exchange = newExchange;
		baseComp.setMsg(contentProvider.getMessage(exchange));
	}
	
	public Exchange getExchange() {
		return exchange;
	}
	
	@Override
	public void setFocus() {
		
	}
	
	public void setMessage(Message message) {
		baseComp.setMsg(message);
	}
	
	public abstract void updateUIStatus(boolean canShowBody);  
	
}
