package com.predic8.plugin.membrane.components.messagefolder;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import com.predic8.membrane.core.http.Message;
import com.predic8.plugin.membrane.components.BodyTabComposite;
import com.predic8.plugin.membrane.components.CSSTabComposite;
import com.predic8.plugin.membrane.components.ErrorTabComposite;
import com.predic8.plugin.membrane.components.HTMLTabComposite;
import com.predic8.plugin.membrane.components.HeaderTabComposite;
import com.predic8.plugin.membrane.components.ImageTabComposite;
import com.predic8.plugin.membrane.components.JSONTabComposite;
import com.predic8.plugin.membrane.components.JavaScriptTabComposite;
import com.predic8.plugin.membrane.components.NullBodyTabComposite;
import com.predic8.plugin.membrane.components.RawTabComposite;
import com.predic8.plugin.membrane.components.SOAPTabComposite;
import com.predic8.plugin.membrane.viewcomponents.BaseComp;

public class MessageTabManager {

	private Log log = LogFactory.getLog(MessageTabManager.class.getName());

	private BaseComp baseComp;

	private TabFolder folder;
	
	private RawTabComposite rawTabComposite;

	private HeaderTabComposite headerTabComposite;

	private ErrorTabComposite errorTabComposite;
	
	private BodyTabComposite currentBodyTab;

	private List<BodyTabComposite> bodyTabs = new ArrayList<BodyTabComposite>();
	
	public MessageTabManager(final BaseComp baseComp) {
		this.baseComp = baseComp;
		folder = new TabFolder(baseComp, SWT.NONE);
		folder.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		errorTabComposite = new ErrorTabComposite(folder);
		rawTabComposite = new RawTabComposite(folder);
		headerTabComposite = new HeaderTabComposite(folder);
		
		createBodyTabs();
		
		currentBodyTab = new NullBodyTabComposite(folder);
		
		folder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				for (TabItem tabItem : folder.getSelection()) {
					if (tabItem.equals(rawTabComposite.getTabItem())) {
						baseComp.setFormatEnabled(false);
						baseComp.setSaveEnabled(true);
						rawTabComposite.update(baseComp.getMsg());
						break;
					} else if (tabItem.equals(headerTabComposite.getTabItem())) {
						baseComp.setFormatEnabled(false);
						baseComp.setSaveEnabled(false);
						headerTabComposite.update(baseComp.getMsg());
						break;
					} else if (tabItem.equals(getCurrentTabItem())) {
						currentBodyTab.update(baseComp.getMsg());
						baseComp.setFormatEnabled(currentBodyTab.isFormatSupported());
						baseComp.setSaveEnabled(currentBodyTab.isSaveSupported());
					}
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				baseComp.setFormatEnabled(false);
				baseComp.setSaveEnabled(false);
			}
			
		});
		
		doUpdate(null);

	}

	private void createBodyTabs() {
		bodyTabs.add(new CSSTabComposite(folder));
		bodyTabs.add(new JavaScriptTabComposite(folder));
		bodyTabs.add(new HTMLTabComposite(folder));
		bodyTabs.add(new SOAPTabComposite(folder));
		bodyTabs.add(new JSONTabComposite(folder));
		bodyTabs.add(new ImageTabComposite(folder));
	}
	
	private TabItem getCurrentTabItem() {
		return currentBodyTab.getTabItem();
	}

	public void setBodyModified(boolean b) {
		currentBodyTab.setBodyModified(b);
	}

	public void doUpdate(Message msg) {
		if (msg == null) {
			hideAllContentTabs();
			errorTabComposite.hide();
			return;
		}

		if (msg.getErrorMessage() != null && !msg.getErrorMessage().equals("")) {
			hideAllContentTabs();
			errorTabComposite.show();
			errorTabComposite.update(msg);
			return;
		}

		if (msg.getHeader() == null) {
			hideAllBodyTabs();
			headerTabComposite.hide();
			errorTabComposite.hide();
			return;
		}

		currentBodyTab = new NullBodyTabComposite(folder);
		
		errorTabComposite.hide();

		rawTabComposite.show();
		
		headerTabComposite.show();
		headerTabComposite.update(msg);

		folder.setSelection(headerTabComposite.getTabItem());
		folder.notifyListeners(SWT.Selection, null);
		hideAllBodyTabs();
		if (msg.getHeader().getContentType() == null) {	
			return;
		}
	
		if (!msg.isBodyEmpty()) {
			if (msg.isCSS()) {
				currentBodyTab = bodyTabs.get(0);
			} else if (msg.isJavaScript()) {
				currentBodyTab = bodyTabs.get(1);
			} else if (msg.isHTML()) {
				currentBodyTab = bodyTabs.get(2);
			} else if (msg.isXML()) {
				currentBodyTab = bodyTabs.get(3);
			} else if (msg.isJSON()) {
				currentBodyTab = bodyTabs.get(4);
			} else if (msg.isImage()) {
				currentBodyTab = bodyTabs.get(5);
			} 	
		} 
		
		currentBodyTab.show();
		
		baseComp.setFormatEnabled(currentBodyTab.isFormatSupported());
	}

	private void hideAllContentTabs() {
		rawTabComposite.hide();
		headerTabComposite.hide();
		
		hideAllBodyTabs();
		
		currentBodyTab = new NullBodyTabComposite(folder);
	}

	private void hideAllBodyTabs() {
		for (BodyTabComposite bodyTab : bodyTabs) {
			bodyTab.hide();
		}
	}

	public void setMessageEditable(boolean bool) {
		currentBodyTab.setBodyTextEditable(bool);

		if (headerTabComposite != null && !headerTabComposite.isDisposed()) {
			headerTabComposite.setWidgetEditable(bool);
		}
	}

	public String getBodyText() {
		return currentBodyTab.getBodyText();
	}

	public void copyBodyFromGUIToModel() {
		try {
			baseComp.getMsg().setBodyContent(getBodyText().getBytes());
			//TODO header view must be refreshed
			log.debug("Body copied from GUI to model");
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	boolean openConfirmDialog(String msg) {
		return MessageDialog.openQuestion(baseComp.getShell(), "Question", msg);
	}

	public void beautify(Message msg) {
		currentBodyTab.beautify(msg.getBody().getContent());
	}

	public boolean isBodyModified() {
		return currentBodyTab.isBodyModified();
	}
	
	
	public void setSelectionOnBodyTabItem() {
		if (currentBodyTab != null && !currentBodyTab.isDisposed() && currentBodyTab.getTabItem() != null && !currentBodyTab.getTabItem().isDisposed()) {
			folder.setSelection(currentBodyTab.getTabItem());
			folder.notifyListeners(SWT.Selection, null);
		}
	}

}
