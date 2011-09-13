package com.predic8.membrane.balancer.beautifiers;

import org.junit.*;

import com.predic8.membrane.core.util.ByteUtil;


public class JSONBeautifierTest {

	private JSONBeautifier beautifier;
	
	private byte[] sample;
	
	@Before
	public void setUp() throws Exception {
		beautifier = new JSONBeautifier();
		sample = ByteUtil.getByteArrayData(getClass().getClassLoader().getResourceAsStream("singlelinejsonsample.json"));
	}
	
	
	@Test
	public void testBeautify() throws Exception {
		System.out.println(beautifier.beautify(sample));
	}
	
	@After
	public void tearDown() throws Exception {
		
	}
	
}
