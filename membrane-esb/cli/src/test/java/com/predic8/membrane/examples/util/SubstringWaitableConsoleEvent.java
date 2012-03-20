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

package com.predic8.membrane.examples.util;

import com.google.common.base.Predicate;
import com.predic8.membrane.examples.Process2;

/**
 * Watches the console until "substring" is found.
 */
public class SubstringWaitableConsoleEvent extends WaitableConsoleEvent {
	
	public SubstringWaitableConsoleEvent(Process2 launcher, final String substring) {
		super(launcher, new Predicate<String>() {
			@Override
			public boolean apply(String line) {
				return line.contains(substring);
			}
		});
	}

}
