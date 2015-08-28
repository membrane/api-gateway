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
package com.predic8.membrane.core.interceptor.authentication.session;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

class CleanupThread extends Thread {
	public interface Cleaner {
		public void cleanup();
	}

	private final ArrayList<WeakReference<Cleaner>> cleaners = new ArrayList<WeakReference<Cleaner>>();

	public CleanupThread(Cleaner... cleaner) {
		for (Cleaner c : cleaner)
			cleaners.add(new WeakReference<Cleaner>(c));
	}

	@Override
	public void run() {
		while (!interrupted()) {
			try {
				Thread.sleep(60 * 1000);
			} catch (InterruptedException e) {
				return;
			}
			ArrayList<WeakReference<Cleaner>> removeUs = new ArrayList<WeakReference<Cleaner>>();
			for (WeakReference<Cleaner> wr : cleaners) {
				Cleaner c = wr.get();
				if (c == null) {
					removeUs.add(wr);
					continue;
				}
				c.cleanup();
			}
			for (WeakReference<Cleaner> wr : removeUs)
				cleaners.remove(wr);
			if (cleaners.isEmpty())
				return;
		}
	}
}