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

package com.predic8.plugin.membrane.labelproviders;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import com.predic8.plugin.membrane.preferences.KeyData;

public class KeyStoreViewLabelProvider extends LabelProvider implements ITableLabelProvider {

	public String getColumnText(Object element, int columnIndex) {

		KeyData keyData = (KeyData) element;

		switch (columnIndex) {

		case 0:
			return keyData.getKind()== null ? "" : keyData.getKind() ;

		case 1:
			return keyData.getAlias();

		case 2:
			return keyData.getSubject()== null ? "" : keyData.getSubject() ;

		case 3:
			return keyData.getIssuer()== null ? "" : keyData.getIssuer() ;

		case 4:
			return keyData.getAlgorithm()== null ? "" : keyData.getSerialNumber() ;
			
		case 5:
			return keyData.getAlgorithm()== null ? "" : keyData.getAlgorithm() ;
		}
		
		return "";
	}

	public Image getColumnImage(Object element, int columnIndex) {
		return null;
	}
	
}
