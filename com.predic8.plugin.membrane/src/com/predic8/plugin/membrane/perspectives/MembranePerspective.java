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
import com.predic8.plugin.membrane.views.RulesView;

public class MembranePerspective implements IPerspectiveFactory {

	public static final String PERSPECTIVE_ID = "com.predic8.plugin.membrane.perspectives.MembranePerspective";
	
	public void createInitialLayout(IPageLayout layout) {
		layout.setEditorAreaVisible(false);
		
		IFolderLayout topLayoutFolder = layout.createFolder("top folder", IPageLayout.TOP, 0.45f, IPageLayout.ID_EDITOR_AREA);
		topLayoutFolder.addPlaceholder(RuleDetailsView.VIEW_ID);
		//topLayoutFolder.addView(RulesView.VIEW_ID);
		topLayoutFolder.addView(ExchangesView.VIEW_ID);
		topLayoutFolder.addPlaceholder(BrowserView.VIEW_ID);
		topLayoutFolder.addPlaceholder(RuleStatisticsView.VIEW_ID);
		
		
		IFolderLayout topLeftLayoutFolder = layout.createFolder("top left folder", IPageLayout.LEFT, 0.25f, "top folder");
		topLeftLayoutFolder.addView(RulesView.VIEW_ID);
		
		IFolderLayout southLayoutFolder = layout.createFolder("south folder", IPageLayout.BOTTOM, 0.55f, IPageLayout.ID_EDITOR_AREA);
		southLayoutFolder.addView(RequestView.VIEW_ID);
		southLayoutFolder.addView(ResponseView.VIEW_ID);
		
		layout.setFixed(true);
		
	}

}
