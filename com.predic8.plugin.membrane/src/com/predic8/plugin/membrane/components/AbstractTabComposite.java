package com.predic8.plugin.membrane.components;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

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
		
	}
	
	public void hide() {
		tabItem.dispose();
	}
	
	public void show() {
		if (tabItem == null) {
			return;
		}
		if (tabItem.isDisposed()) {
			tabItem = new TabItem((TabFolder)getParent(), SWT.NONE);
			tabItem.setText(tabTitle);
			tabItem.setControl(this);
		} 
	}

	public String getTabTitle() {
		return tabTitle;
	}

	public void setTabTitle(String tabTitle) {
		this.tabTitle = tabTitle;
	}
	
}
