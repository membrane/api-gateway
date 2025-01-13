/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.servlet.embedded;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.transport.*;

@MCMain(
		outputPackage="com.predic8.membrane.servlet.config.spring",
		outputName="router-conf.xsd",
		targetNamespace="http://membrane-soa.org/war/1/")
@MCElement(name="servletTransport", configPackage="com.predic8.membrane.servlet.config.spring")
public class ServletTransport extends Transport {

	boolean removeContextRoot = true;

	public boolean isRemoveContextRoot() {
		return removeContextRoot;
	}

	@MCAttribute
	public void setRemoveContextRoot(boolean removeContextRoot) {
		this.removeContextRoot = removeContextRoot;
	}

	@Override
	public void closeAll() {
		// do nothing
	}

	@Override
	public boolean isOpeningPorts() {
		return false;
	}

}
