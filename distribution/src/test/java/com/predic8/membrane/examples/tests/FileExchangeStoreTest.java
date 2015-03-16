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
import static org.junit.Assert.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Calendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.core.classloader.annotations.PrepareForTest;

import com.predic8.membrane.core.exchangestore.FileExchangeStore;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Calendar.class)
public class FileExchangeStoreTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("file-exchangestore");
		Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();
		try {
			getAndAssert200("http://localhost:2000/");
			
			Thread.sleep(1000);
			
			File exchangesDir = new File(baseDir, "exchanges");
			if (!containsRecursively(exchangesDir, new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".msg");
				}
			}))
				throw new AssertionError("Did not find *.msg in exchanges dir.");
		} finally {
			sl.killScript();
		}
	}
	
	@Test
	public void testDeleteOldFolders() throws IOException {

		PowerMockito.mockStatic(Calendar.class);
		Calendar calendar = Mockito.mock(Calendar.class);
		Mockito.when(calendar.get(Mockito.eq(Calendar.YEAR))).thenReturn(2015);
		Mockito.when(calendar.get(Mockito.eq(Calendar.MONTH))).thenReturn(2); // 2 = March
		Mockito.when(calendar.get(Mockito.eq(Calendar.DAY_OF_MONTH))).thenReturn(15);
		Mockito.when(Calendar.getInstance()).thenReturn(calendar);

		new File("/tmp/FileExchangeStoreTest").mkdirs();

		for (int m = 1; m<=3; m++)
			for (int d = 1; d<=31; d++)
				if (!(m == 2 && d > 28))
					new File("/tmp/FileExchangeStoreTest/2015/"+m+"/"+d).mkdirs();

		FileExchangeStore fes = new FileExchangeStore();
		fes.setDir("/tmp");
		fes.setMaxDays(30);

		// before
		for (int m = 1; m<=3; m++)
			for (int d = 1; d<=31; d++)
				if (!(m == 2 && d > 28))
					assertTrue(new File("/tmp/FileExchangeStoreTest/2015/"+m+"/"+d).exists());

		fes.deleteOldFolders();

		// after
		for (int d = 1; d<=31; d++)
			assertFalse(new File("/tmp/FileExchangeStoreTest/2015/1/"+d).exists());
		for (int d = 1; d<=13; d++)
			assertFalse(new File("/tmp/FileExchangeStoreTest/2015/2/"+d).exists());
		for (int d = 17; d<=28; d++)
			assertTrue(new File("/tmp/FileExchangeStoreTest/2015/2/"+d).exists());
		for (int d = 1; d<=31; d++)
			assertTrue(new File("/tmp/FileExchangeStoreTest/2015/3/"+d).exists());

		// cleanup
		new File("/tmp/FileExchangeStoreTest").delete();
	}

	private boolean containsRecursively(File base, FilenameFilter filter) {
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
