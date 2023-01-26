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

package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static java.io.File.createTempFile;
import static java.lang.Thread.sleep;
import static java.util.Calendar.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Calendar;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.exchangestore.FileExchangeStore;

public class FileExchangeStoreTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "file-exchangestore";
	}

	@Test
	public void test() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			getAndAssert200("http://localhost:2000/");

			sleep(300);

			if (!containsRecursively(new File(baseDir, "exchanges"), this::filterDotMsg))
				throw new AssertionError("Did not find *.msg in exchanges dir.");
		}
	}

	private boolean filterDotMsg(File dir, String name) {
		return name.endsWith(".msg");
	}

	@Test
	public void testDeleteOldFolders() throws Exception {

		File t = createTempFile("fes", null);

		//noinspection ResultOfMethodCallIgnored
		t.delete();

		File base = new File(t, "FileExchangeStoreTest");

		//noinspection ResultOfMethodCallIgnored
		base.mkdirs();

		renameMe(base);

		// before
		for (int m = 1; m<=3; m++)
			for (int d = 1; d<=31; d++)
				if (!(m == 2 && d > 28))
					assertTrue(new File(base, "2015/"+m+"/"+d).exists());

		getFileExchangeStore(base).deleteOldFolders(getCalendar());

		// after
		for (int d = 1; d<=31; d++)
			assertFalse(new File(base, "2015/1/"+d).exists());
		for (int d = 1; d<=13; d++)
			assertFalse(new File(base, "2015/2/"+d).exists());
		for (int d = 17; d<=28; d++)
			assertTrue(new File(base, "2015/2/"+d).exists());
		for (int d = 1; d<=31; d++)
			assertTrue(new File(base, "2015/3/"+d).exists());

		// cleanup
		recursiveDelete(base);
	}

	private void renameMe(File base) {
		for (int m = 1; m<=3; m++)
			for (int d = 1; d<=31; d++)
				if (!(m == 2 && d > 28))
					//noinspection ResultOfMethodCallIgnored
					new File(base, "2015/"+m+"/"+d).mkdirs();
	}

	private Calendar getCalendar() {
		Calendar c = Calendar.getInstance();
		c.set(YEAR, 2015);
		c.set(MONTH, 2); // 2 = March
		c.set(DAY_OF_MONTH, 15);
		return c;
	}

	private FileExchangeStore getFileExchangeStore(File base) {
		FileExchangeStore fes = new FileExchangeStore();
		fes.setDir(base.getAbsolutePath());
		fes.setMaxDays(30);
		return fes;
	}

	private void recursiveDelete(File file) {
		if (file.isDirectory())

			//noinspection ConstantConditions
			for (File child : file.listFiles())
				recursiveDelete(child);
		if (!file.delete())
			throw new RuntimeException("could not delete " + file.getAbsolutePath());
	}

	private boolean containsRecursively(File base, FilenameFilter filter) {
		//noinspection ConstantConditions
		for (File f : base.listFiles()) {
			if (f.isDirectory())
				if (containsRecursively(f, filter))
					return true;
			if (f.isFile() && filter.accept(base, f.getName()))
				return true;
		}
		return false;
	}
}
