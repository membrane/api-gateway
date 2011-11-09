package com.predic8.plugin.membrane.dialogs.rule.composites;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

import com.predic8.membrane.core.rules.Rule;

public abstract class AbstractProxyFeatureComposite extends Composite {

	public static final String SELECTION_INPUT_CHANGED = "SelectionInputChanged";
	
	protected Rule rule;
	
	protected boolean dataChanged;
	
	public AbstractProxyFeatureComposite(Composite parent) {
		super(parent, SWT.NONE);
	}

	public abstract String getTitle();

	public void setRule(Rule rule) {
		this.rule = rule;
	}

	public abstract void commit();

	public Rule getRule() {
		return rule;
	}
	

	public boolean isDataChanged() {
		return dataChanged;
	}
	
	protected Event createSelectionEvent(Object data) {
		Event event = new Event();
		event.type = SWT.Selection;
		event.data = data;
		return event;
	}
	
}
