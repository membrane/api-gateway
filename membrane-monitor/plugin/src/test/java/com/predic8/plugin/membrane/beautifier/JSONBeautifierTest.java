package com.predic8.plugin.membrane.beautifier;

import org.junit.*;

import com.predic8.membrane.core.beautifier.JSONBeautifier;
import com.predic8.membrane.core.util.ByteUtil;

public class JSONBeautifierTest {

	private JSONBeautifier beautifier;

	private byte[] sample;

	@Before
	public void setUp() throws Exception {
		beautifier = new JSONBeautifier();
		sample = ByteUtil.getByteArrayData(getClass().getClassLoader()
				.getResourceAsStream("singlelinejsonsample.json"));
	}

	@Test
	public void testBeautify() throws Exception {
		System.out.println(beautifier.beautify(new String(sample, "UTF-8")));
	}

	@After
	public void tearDown() throws Exception {

	}

}
