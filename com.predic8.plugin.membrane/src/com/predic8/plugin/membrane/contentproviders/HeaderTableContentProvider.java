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

package com.predic8.plugin.membrane.contentproviders;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;


public class HeaderTableContentProvider implements IStructuredContentProvider {

	public Object[] getElements(Object inputElement) {
		if (!(inputElement instanceof Message)) 
			return new Object[0];
		
		Message msg = (Message) inputElement;
		Header header = msg.getHeader();
		HeaderField[]headerFields = header.getAllHeaderFields();	 
		
		HeaderField[] copyHeaderFields = new HeaderField[headerFields.length];
		for(int i = 0; i < headerFields.length; i++){
			copyHeaderFields[i] = headerFields[i];
		}
		return copyHeaderFields;
	}

	public void dispose() {
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		
	}

}