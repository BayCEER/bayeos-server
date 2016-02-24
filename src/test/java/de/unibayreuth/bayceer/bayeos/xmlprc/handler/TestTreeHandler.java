package de.unibayreuth.bayceer.bayeos.xmlprc.handler;


import java.io.IOException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.AbstractClientTest;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektArt;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektNode;

public class TestTreeHandler extends AbstractClientTest {

	@Before
	public void setUp() throws XmlRpcException, IOException {
		super.setUp();
	}

	@After
	public void tearDown() throws XmlRpcException {
		super.tearDown();
	}

	@Test
	public void testGetRoot() throws XmlRpcException {
		Vector r = (Vector) cli.execute("TreeHandler.getRoot", "messung_%",false,"week",null);		
		assertNull(r);
		
		r = (Vector) cli.execute("TreeHandler.getRoot", "messung_ordner",false,"week",null);		
		assertNotNull(r);
				
		r = (Vector) cli.execute("TreeHandler.getRoot", "web_ordner",false,"week",null);		
		assertNotNull(r);
	}

	

}
