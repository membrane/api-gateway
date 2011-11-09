package com.predic8.plugin.membrane.dialogs.rule;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.plugin.membrane.dialogs.rule.composites.*;
import com.predic8.plugin.membrane.util.SWTUtil;

public abstract class AbstractProxyEditDialog extends Dialog {

	protected Rule originalRule;
	
	protected Rule workingCopy;
	
	protected TabFolder tabFolder;
	
	protected AbstractProxyFeatureComposite currentSelectedComposite;
	
	public AbstractProxyEditDialog(Shell parentShell) {
		super(parentShell);
	}
	
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(getTitle());
		shell.setSize(520, 500);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(SWTUtil.createGridLayout(1, 5));
		tabFolder = createTabFolder(container);
		
		addTabTabItem(new ProxyGeneralInfoTabComposite(tabFolder));
		addTabTabItem(createProxyKeyTabItem());
		addCustomTabItems();
		addTabTabItem(new ProxyActionsTabComposite(tabFolder));
		addTabTabItem(createProxyXMLConfTabItem());

		return container;
	}
	
	public abstract String getTitle();

	private TabFolder createTabFolder(Composite container) {
		TabFolder tabs = new TabFolder(container, SWT.NONE);
		GridData g = new GridData();
		g.widthHint = 500;
		g.heightHint = 490;
		g.grabExcessHorizontalSpace = true;
		g.grabExcessVerticalSpace = true;
		tabs.setLayoutData(g);
		
		tabs.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				refreshTabContent();
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				refreshTabContent();
			}
		});
		
		return tabs;
	}
	
	protected abstract AbstractProxyFeatureComposite createProxyKeyTabItem();
	
	protected abstract AbstractProxyFeatureComposite createProxyXMLConfTabItem();
	
	protected void addTabTabItem(AbstractProxyFeatureComposite composite) {
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText(composite.getTitle());
		tabItem.setControl(composite);
	}
	
	public void setInput(Rule rule) {
		if (rule == null)
			return;
		
		this.originalRule = rule;
		try {
			workingCopy = originalRule.getDeepCopy();
		} catch (Exception e) {
			e.printStackTrace();
		}
			
		refreshTabContent();
	}
	
	protected abstract void addCustomTabItems();

	private void refreshTabContent() {
		if (workingCopy == null)
			return;
		
		if (tabFolder.getSelection() == null)
			return;
		
		TabItem selected = tabFolder.getItem(tabFolder.getSelectionIndex());
		
		if (selected == null || selected.getControl() == null)
			return;
		
		commitCurrent();
		
		currentSelectedComposite = (AbstractProxyFeatureComposite)selected.getControl();
		currentSelectedComposite.setRule(workingCopy);
	}

	private void commitCurrent() {
		if (currentSelectedComposite != null) {
			currentSelectedComposite.commit();
			workingCopy = currentSelectedComposite.getRule();
		}
	}
	
	@Override
	protected void okPressed() {
		try {
			commitCurrent();
			if (isRuleChanged())
				replaceRule();
			
			close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void replaceRule() throws Exception {
		
		System.err.println("rule replaced . . . ");
		
		getRuleManager().removeRule(originalRule);
		getRuleManager().addRuleIfNew(workingCopy);
	}
	
	protected RuleManager getRuleManager() {
		return Router.getInstance().getRuleManager();
	}
	
	private boolean isRuleChanged() {
		Control[] tabList = tabFolder.getTabList();
		for (Control control : tabList) {
			AbstractProxyFeatureComposite composite = (AbstractProxyFeatureComposite)control;
			if (composite.isDataChanged())
				return true;
			
		}
		return false;
	}
	
}
