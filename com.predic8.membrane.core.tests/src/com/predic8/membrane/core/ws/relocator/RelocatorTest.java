package com.predic8.membrane.core.ws.relocator;

import java.io.ByteArrayInputStream;

import junit.framework.TestCase;

import org.junit.Test;

import com.predic8.membrane.core.util.ByteUtil;

public class RelocatorTest extends TestCase {

	private Relocator relocator;

	@Override
	protected void setUp() throws Exception {
		relocator = new Relocator(System.out, "http", "localhost", 3000);
		super.setUp();
	}

	public void testWSDLRelocate() throws Exception {
		byte[] contentWSDL = ByteUtil.getByteArrayData(this.getClass().getResourceAsStream("/blz-service.wsdl"));
		relocator.relocate(new ByteArrayInputStream(contentWSDL));
		assertTrue(relocator.isWsdlFound());
	}

	@Test
	public void testXMLRelocate() throws Exception {
		byte[] contentXML = ByteUtil.getByteArrayData(this.getClass().getResourceAsStream("/acl.xml"));
		relocator.relocate(new ByteArrayInputStream(contentXML));
		assertFalse(relocator.isWsdlFound());
	}
}
