/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot.model;

import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.annot.MCMain;

/**
 * Keeps track of all information during one (or several incremental) compiler runs.
 * 
 * Collects all {@link MCMain}s found.
 */
public class Model {
	private List<MainInfo> mains = new ArrayList<MainInfo>();

	public List<MainInfo> getMains() {
		return mains;
	}

	public void setMains(List<MainInfo> mains) {
		this.mains = mains;
	}
}