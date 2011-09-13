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

package com.predic8.plugin.membrane.components.composites.tabmanager;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.http.Message;

public class AbstractTabComposite extends Composite {

	protected TabItem tabItem;

	protected String tabTitle;
	
	public AbstractTabComposite(TabFolder parent) {
		super(parent, SWT.NONE);
	}
	
	public AbstractTabComposite(TabFolder parent, String tabTitle) {
		super(parent, SWT.NONE);
		this.tabTitle = tabTitle;
		setLayout(new FillLayout());
		tabItem = new TabItem(parent, SWT.NONE);
		tabItem.setText(tabTitle);
		tabItem.setControl(this);
	}

	public TabItem getTabItem() {
		return tabItem;
	}
	
	@Override
	public void dispose() {
		tabItem.dispose();
		super.dispose();
	}

	public void update(Message msg) {
		if (msg == null)
			return;
		updateInternal(msg);
	}
	
	protected void updateInternal(Message msg) {
		if (msg == null)
			return;
	}

	public void hide() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				tabItem.dispose();
			}
		});
		
	}
	
	public void show() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (tabItem == null) {
					return;
				}
				if (tabItem.isDisposed()) {
					tabItem = new TabItem((TabFolder)getParent(), SWT.NONE);
					tabItem.setText(tabTitle);
					tabItem.setControl(AbstractTabComposite.this);
				} 
			}
		});
	}

	public String getTabTitle() {
		return tabTitle;
	}

	public void setTabTitle(String tabTitle) {
		this.tabTitle = tabTitle;
	}
	
}
