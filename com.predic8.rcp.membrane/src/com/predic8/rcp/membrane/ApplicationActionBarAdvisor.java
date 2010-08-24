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
//		exitAction = ActionFactory.QUIT.create(window);
//		register(exitAction);

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

		//fileMenu.add(new Separator());
		//fileMenu.add(exitAction);

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
