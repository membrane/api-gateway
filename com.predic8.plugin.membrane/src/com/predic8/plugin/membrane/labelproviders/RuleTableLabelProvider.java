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

import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ProxyRule;


public class RuleTableLabelProvider extends LabelProvider implements ITableLabelProvider {

	public Image getColumnImage(Object element, int columnIndex) {
		
		return null;
	}

	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof ServiceProxy) {
			ServiceProxy rule = (ServiceProxy)element;
			switch (columnIndex) {
			case 0:
				return rule.getKey().getHost();
			case 1:
				return rule.getKey().getPort() + "";
			case 2:
				return rule.getKey().getMethod();
			case 3:
				return rule.getKey().getPath();
			case 4:
				return rule.getTargetHost();
			case 5:
				return rule.getTargetPort() + "";
			default:
				throw new RuntimeException("Rule table viewer must have only 6 columns for ForwardingRule");
			}
		} else if (element instanceof ProxyRule) {
			ProxyRule rule = (ProxyRule)element;
			switch (columnIndex) {
			case 0:
				return rule.getKey().getHost();
			case 1:
				return rule.getKey().getPort() + "";
			case 2:
				return rule.getKey().getMethod();
			case 3:
				return rule.getKey().getPath();
			case 4:
				return "";
			case 5:
				return "";
			default:
				throw new RuntimeException("Rule table viewer must have only 6 columns for ProxyRule");
			}
		}
		
		throw new RuntimeException("Unknown object type: labels are provided only for ProxyRule or ForwardingRule.");
	}

	

}
