package com.predic8.plugin.membrane.components.messagefolder;

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

	private ImageTabComposite imageTabComposite;

	private HeaderTabComposite headerTabComposite;

	private ErrorTabComposite errorTabComposite;

	private CSSTabComposite cssTabComposite;

	private JavaScriptTabComposite javaScriptTabComposite;

	private HTMLTabComposite htmlTabComposite;

	private SOAPTabComposite soapTabComposite;

	private JSONTabComposite jsonTabComposite;
	
	private BodyTabComposite currentBodyTabComposite;

	public MessageTabManager(final BaseComp baseComp) {
		this.baseComp = baseComp;
		folder = new TabFolder(baseComp, SWT.NONE);
		folder.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		errorTabComposite = new ErrorTabComposite(folder);
		rawTabComposite = new RawTabComposite(folder);
		headerTabComposite = new HeaderTabComposite(folder);
		imageTabComposite = new ImageTabComposite(folder);

		cssTabComposite = new CSSTabComposite(folder);
		javaScriptTabComposite = new JavaScriptTabComposite(folder);
		htmlTabComposite = new HTMLTabComposite(folder);
		soapTabComposite = new SOAPTabComposite(folder);
		jsonTabComposite = new JSONTabComposite(folder);
		
		currentBodyTabComposite = new NullBodyTabComposite(folder);
		
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
						currentBodyTabComposite.update(baseComp.getMsg());
						baseComp.setFormatEnabled(currentBodyTabComposite.isFormatSupported());
						baseComp.setSaveEnabled(currentBodyTabComposite.isSaveSupported());
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
	
	private TabItem getCurrentTabItem() {
		return currentBodyTabComposite.getTabItem();
	}

	public void setBodyModified(boolean b) {
		currentBodyTabComposite.setBodyModified(b);
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

		currentBodyTabComposite = new NullBodyTabComposite(folder);
		
		errorTabComposite.hide();

		rawTabComposite.show();
		
		headerTabComposite.show();
		headerTabComposite.update(msg);

		folder.setSelection(headerTabComposite.getTabItem());
		folder.notifyListeners(SWT.Selection, null);
		if (msg.getHeader().getContentType() == null) {
			hideAllBodyTabs();
			return;
		}

		hideAllBodyTabs();
	
		if (!msg.isBodyEmpty()) {
			if (msg.isImage()) {
				currentBodyTabComposite = imageTabComposite;
			} else if (msg.isXML()) {
				currentBodyTabComposite = soapTabComposite;
			} else if (msg.isHTML()) {
				currentBodyTabComposite = htmlTabComposite;
			} else if (msg.isCSS()) {
				currentBodyTabComposite = cssTabComposite;
			} else if (msg.isJavaScript()) {
				currentBodyTabComposite = javaScriptTabComposite;
			} else if (msg.isJSON()) {
				currentBodyTabComposite = jsonTabComposite;
			} 	
		} 
		
		currentBodyTabComposite.show();
		
		baseComp.setFormatEnabled(currentBodyTabComposite.isFormatSupported());
	}

	private void hideAllContentTabs() {
		rawTabComposite.hide();
		headerTabComposite.hide();
		
		hideAllBodyTabs();
		
		currentBodyTabComposite = new NullBodyTabComposite(folder);
	}

	private void hideAllBodyTabs() {
		cssTabComposite.hide();
		soapTabComposite.hide();
		jsonTabComposite.hide();
		javaScriptTabComposite.hide();
		htmlTabComposite.hide();
		imageTabComposite.hide();
	}

	public void setMessageEditable(boolean bool) {
		currentBodyTabComposite.setBodyTextEditable(bool);

		if (headerTabComposite != null && !headerTabComposite.isDisposed()) {
			headerTabComposite.setWidgetEditable(bool);
		}
	}

	public String getBodyText() {
		return currentBodyTabComposite.getBodyText();
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
		currentBodyTabComposite.beautify(msg.getBody().getContent());
	}

	public boolean isBodyModified() {
		return currentBodyTabComposite.isBodyModified();
	}
	
	
	public void setSelectionOnBodyTabItem() {
		if (currentBodyTabComposite != null && !currentBodyTabComposite.isDisposed() && currentBodyTabComposite.getTabItem() != null && !currentBodyTabComposite.getTabItem().isDisposed()) {
			folder.setSelection(currentBodyTabComposite.getTabItem());
			folder.notifyListeners(SWT.Selection, null);
		}
	}

}
