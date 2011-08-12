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

package com.predic8.plugin.membrane.perspectives;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

import com.predic8.plugin.membrane.views.BrowserView;
import com.predic8.plugin.membrane.views.ExchangesView;
import com.predic8.plugin.membrane.views.RequestView;
import com.predic8.plugin.membrane.views.ResponseView;
import com.predic8.plugin.membrane.views.ProxyDetailsView;
import com.predic8.plugin.membrane.views.RuleStatisticsView;
import com.predic8.plugin.membrane.views.ProxiesView;

public class MembranePerspective implements IPerspectiveFactory {

	public static final String PERSPECTIVE_ID = "com.predic8.plugin.membrane.perspectives.MembranePerspective";
	
	public boolean showSingle;
	
	public void createInitialLayout(IPageLayout layout) {
		
		layout.setEditorAreaVisible(false);
		layout.setFixed(false);
		
		if (showSingle) {
			IFolderLayout centerLayoutFolder = layout.createFolder("center folder", IPageLayout.TOP, 1.0f, IPageLayout.ID_EDITOR_AREA);
			centerLayoutFolder.addPlaceholder(RequestView.VIEW_ID);
			centerLayoutFolder.addPlaceholder(ResponseView.VIEW_ID);
			centerLayoutFolder.addPlaceholder(BrowserView.VIEW_ID);
			centerLayoutFolder.addPlaceholder(RuleStatisticsView.VIEW_ID);
			centerLayoutFolder.addPlaceholder(ExchangesView.VIEW_ID);
		} else {
			IFolderLayout topLayoutFolder = layout.createFolder("top folder", IPageLayout.TOP, 0.52f, IPageLayout.ID_EDITOR_AREA);
			topLayoutFolder.addPlaceholder(ProxyDetailsView.VIEW_ID);
			topLayoutFolder.addView(ExchangesView.VIEW_ID);
			topLayoutFolder.addPlaceholder(BrowserView.VIEW_ID);
			topLayoutFolder.addPlaceholder(RuleStatisticsView.VIEW_ID);
			
			
			IFolderLayout topLeftLayoutFolder = layout.createFolder("top left folder", IPageLayout.LEFT, 0.31f, "top folder");
			topLeftLayoutFolder.addView(ProxiesView.VIEW_ID);
			
			IFolderLayout southLayoutFolder = layout.createFolder("south folder", IPageLayout.BOTTOM, 0.48f, IPageLayout.ID_EDITOR_AREA);
			southLayoutFolder.addView(RequestView.VIEW_ID);
			southLayoutFolder.addView(ResponseView.VIEW_ID);
		}
	}

	public boolean isShowSingle() {
		return showSingle;
	}

	public void setShowSingle(boolean showSingle) {
		this.showSingle = showSingle;
	}

	
	
}
