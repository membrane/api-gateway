package com.predic8.plugin.membrane.perspectives;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

import com.predic8.plugin.membrane.views.BrowserView;
import com.predic8.plugin.membrane.views.ExchangesView;
import com.predic8.plugin.membrane.views.RequestView;
import com.predic8.plugin.membrane.views.ResponseView;
import com.predic8.plugin.membrane.views.RuleDetailsView;
import com.predic8.plugin.membrane.views.RuleStatisticsView;
import com.predic8.plugin.membrane.views.RuleTreeView;

public class MembranePerspective implements IPerspectiveFactory {

	public static final String PERSPECTIVE_ID = "com.predic8.plugin.membrane.perspectives.MembranePerspective";
	
	public void createInitialLayout(IPageLayout layout) {
		layout.setEditorAreaVisible(false);
		layout.addView(RuleTreeView.VIEW_ID, IPageLayout.LEFT, 0.25f, IPageLayout.ID_EDITOR_AREA);
		
		IFolderLayout folderLayout = layout.createFolder("supplementary view folder", IPageLayout.TOP, 0.45f, IPageLayout.ID_EDITOR_AREA);
		folderLayout.addPlaceholder(RuleDetailsView.VIEW_ID);
		folderLayout.addView(ExchangesView.VIEW_ID);
		folderLayout.addPlaceholder(BrowserView.VIEW_ID);
		folderLayout.addPlaceholder(RuleStatisticsView.VIEW_ID);
		
		
		IFolderLayout southFolderLayout = layout.createFolder("south folder", IPageLayout.BOTTOM, 0.55f, IPageLayout.ID_EDITOR_AREA);
		southFolderLayout.addView(RequestView.VIEW_ID);
		southFolderLayout.addView(ResponseView.VIEW_ID);
		
		layout.setFixed(true);
		
	}

}
