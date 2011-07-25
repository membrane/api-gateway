/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.plugin.membrane.views;

import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

import com.predic8.plugin.membrane.labelproviders.TableHeaderLabelProvider;

public abstract class TableViewPart extends ViewPart {

	protected TableViewer tableViewer;
	
	protected void createTableViewer(Composite composite) {
		tableViewer = new TableViewer(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER | SWT.VIRTUAL);
		GridData gData = new GridData(GridData.FILL_BOTH);
		gData.grabExcessVerticalSpace = true;
		gData.grabExcessHorizontalSpace = true;
		tableViewer.getTable().setLayoutData(gData);
		
		createColumns();
		tableViewer.setLabelProvider(createLabelProvider());
		tableViewer.setContentProvider(createContentProvider());
		
		setPropertiesForTableViewer();
		addListenersForTableViewer();
		addCellEditorsAndModifiersToViewer();
	}
	
	@Override
	public void setFocus() {
		tableViewer.getTable().setFocus();
	}

	protected void createColumns() {
		String[] titles = getTableColumnTitles();
		int[] bounds = getTableColumnBounds();
		
		for (int i = 0; i < titles.length; i++) {
			TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.NONE);
			column.getViewer().setLabelProvider(new TableHeaderLabelProvider());
			column.getColumn().setAlignment(getAlignemnt(i));
			column.getColumn().setText(titles[i]);
			column.getColumn().setWidth(bounds[i]);
			column.getColumn().setResizable(true);
			column.getColumn().setMoveable(true);
		}
		tableViewer.getTable().setHeaderVisible(true);
		tableViewer.getTable().setLinesVisible(true);
	}
	
	protected abstract String[] getTableColumnTitles();
	
	protected abstract int[] getTableColumnBounds();
	
	protected int getAlignemnt(int column) {
		return SWT.CENTER;
	}
	
	public TableViewer getTableViewer() {
		return tableViewer;
	}
	
	protected abstract IBaseLabelProvider createLabelProvider();
	
	protected abstract IContentProvider createContentProvider();
	
	protected void setPropertiesForTableViewer() {
		
	}
	
	protected void addListenersForTableViewer() {
		
	}
	
	protected void addCellEditorsAndModifiersToViewer() {
		
	}
	
}
