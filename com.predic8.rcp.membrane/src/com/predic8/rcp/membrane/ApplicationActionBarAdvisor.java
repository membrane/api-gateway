package com.predic8.rcp.membrane;

import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;

public class ApplicationActionBarAdvisor extends ActionBarAdvisor {

	private IWorkbenchAction exitAction;

	//private IContributionItem viewList;

	private IAction preferencesAction;

	private IAction aboutAction;

	//private IAction helpAction;

	//private IAction searchHelpAction;
	
	//private IAction webPredic8Action;

	public ApplicationActionBarAdvisor(IActionBarConfigurer configurer) {
		super(configurer);
	}

	protected void makeActions(IWorkbenchWindow window) {
		exitAction = ActionFactory.QUIT.create(window);
		register(exitAction);

		preferencesAction = ActionFactory.PREFERENCES.create(window);
		register(preferencesAction);

//		helpAction = ActionFactory.HELP_CONTENTS.create(window);
//		register(helpAction);
//
//		searchHelpAction = ActionFactory.HELP_SEARCH.create(window); // NEW
//		register(searchHelpAction); // NEW

		aboutAction = ActionFactory.ABOUT.create(window);
		register(aboutAction);

//		webPredic8Action = new WebPredic8Action(window);
//		register(webPredic8Action);

		//viewList = ContributionItemFactory.VIEWS_SHORTLIST.create(window);
	}

	protected void fillMenuBar(IMenuManager menuBar) {

		MenuManager fileMenu = new MenuManager("&File",
				IWorkbenchActionConstants.M_FILE);
		MenuManager windowMenu = new MenuManager("&Window",
				IWorkbenchActionConstants.M_WINDOW);
		MenuManager helpMenu = new MenuManager("&Help",
				IWorkbenchActionConstants.M_HELP);

		menuBar.add(fileMenu);
		menuBar.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
		menuBar.add(windowMenu);
		menuBar.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
		menuBar.add(helpMenu);

		fileMenu.add(new Separator());
		fileMenu.add(exitAction);

//		MenuManager showViewMenu = new MenuManager("Show View");
//		showViewMenu.add(viewList);
//		windowMenu.add(showViewMenu);
		windowMenu.add(preferencesAction);

		helpMenu.add(aboutAction);
		helpMenu.add(new Separator());

//		helpMenu.add(helpAction);
//		helpMenu.add(new Separator());
//
//		helpMenu.add(searchHelpAction);
//		helpMenu.add(new Separator());
		
//		helpMenu.add(new Separator());
//		helpMenu.add(webPredic8Action);
	}

}
