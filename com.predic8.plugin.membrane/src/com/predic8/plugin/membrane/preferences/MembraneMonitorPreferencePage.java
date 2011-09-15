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

package com.predic8.plugin.membrane.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;

import com.predic8.plugin.membrane.util.SWTUtil;

public class MembraneMonitorPreferencePage extends PreferencePage implements
		IWorkbenchPreferencePage {

	public static final String PAGE_ID = "com.predic8.plugin.membrane.preferences.MembraneMonitorPreferencePage";
	
	public MembraneMonitorPreferencePage() {
		
	}

	public MembraneMonitorPreferencePage(String title) {
		super(title);
		setDescription("Provides global settings for membrane SOAP monitor.");
	}

	public MembraneMonitorPreferencePage(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = SWTUtil.createGridLayout(2, 25, 15, 15, 15);
		gridLayout.verticalSpacing = 10;
		composite.setLayout(gridLayout);
		
		return composite;
	}

	public void init(IWorkbench workbench) {
		
	}

}
