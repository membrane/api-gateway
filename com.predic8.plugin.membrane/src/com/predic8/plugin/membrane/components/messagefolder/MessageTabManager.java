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

package com.predic8.plugin.membrane.components.messagefolder;

import java.io.IOException;
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
import com.predic8.membrane.core.http.Response;
import com.predic8.plugin.membrane.components.composites.tabmanager.BodyTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.CSSTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.ErrorTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.HTMLTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.HeaderTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.ImageTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.JSONTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.JavaScriptTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.NullBodyTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.RawTabComposite;
import com.predic8.plugin.membrane.components.composites.tabmanager.SOAPTabComposite;
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

	private NullBodyTabComposite nullBodyTabComposite;

	public MessageTabManager(final BaseComp baseComp) {
		this.baseComp = baseComp;
		folder = createTabFolder(baseComp);

		errorTabComposite = new ErrorTabComposite(folder);
		rawTabComposite = new RawTabComposite(folder);
		headerTabComposite = new HeaderTabComposite(folder);
		nullBodyTabComposite = new NullBodyTabComposite(folder);

		createBodyTabs();

		currentBodyTab = new NullBodyTabComposite(folder);

		addSelectionListenerToFolder(baseComp);

		doUpdate(null);

	}

	private void addSelectionListenerToFolder(final BaseComp baseComp) {
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
	}

	private TabFolder createTabFolder(final BaseComp baseComp) {
		final TabFolder folder = new TabFolder(baseComp, SWT.NONE);
		folder.setLayoutData(new GridData(GridData.FILL_BOTH));
		return folder;
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

		currentBodyTab = nullBodyTabComposite;

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

		currentBodyTab = getCurrentBodyTab(msg);

		currentBodyTab.show();

		baseComp.setFormatEnabled(currentBodyTab.isFormatSupported());
	}

	private BodyTabComposite getCurrentBodyTab(Message msg) {
		if (msg instanceof Response) {
			if (((Response) msg).isRedirect() || ((Response) msg).hasNoContent())
				return nullBodyTabComposite;
		}

		try {
			if (msg.isBodyEmpty())
				return bodyTabs.get(2);
		} catch (IOException e) {
			e.printStackTrace();
			return bodyTabs.get(2);
		}

		if (msg.isCSS()) {
			return bodyTabs.get(0);
		} else if (msg.isJavaScript()) {
			return bodyTabs.get(1);
		} else if (msg.isHTML()) {
			return bodyTabs.get(2);
		} else if (msg.isXML()) {
			return bodyTabs.get(3);
		} else if (msg.isJSON()) {
			return bodyTabs.get(4);
		} else if (msg.isImage()) {
			return bodyTabs.get(5);
		}
		return bodyTabs.get(2);
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
			// TODO header view must be refreshed
			log.debug("Body copied from GUI to model");
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	boolean openConfirmDialog(String msg) {
		return MessageDialog.openQuestion(baseComp.getShell(), "Question", msg);
	}

	public void beautify(Message msg) throws IOException {
		currentBodyTab.beautify(msg.getBody().getContent());
	}

	public boolean isBodyModified() {
		return currentBodyTab.isBodyModified();
	}

	public void setSelectionOnBodyTabItem() {
		if (!isCurrentBodyTabAvailable())
			return;
		folder.setSelection(currentBodyTab.getTabItem());
		folder.notifyListeners(SWT.Selection, null);
	}

	private boolean isCurrentBodyTabAvailable() {
		return currentBodyTab != null && !currentBodyTab.isDisposed() && currentBodyTab.getTabItem() != null && !currentBodyTab.getTabItem().isDisposed();
	}

}
