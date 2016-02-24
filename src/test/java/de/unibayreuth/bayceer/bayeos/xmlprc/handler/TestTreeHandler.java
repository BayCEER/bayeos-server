package de.unibayreuth.bayceer.bayeos.xmlprc.handler;


import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.NodeTest;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektNode;

public class TestTreeHandler extends NodeTest {
	
	@Test
	public void testGetRoot() throws XmlRpcException {
		Vector r = (Vector) cli.execute("TreeHandler.getRoot", "messung_%",false,"week",null);		
		assertNull(r);
		
		r = (Vector) cli.execute("TreeHandler.getRoot", "messung_ordner",false,"week",null);		
		assertNotNull(r);
				
		r = (Vector) cli.execute("TreeHandler.getRoot", "web_ordner",false,"week",null);		
		assertNotNull(r);
	}
	
	@Test
	public void testfindOrSaveNode() throws XmlRpcException {								
		 ObjektNode n = new ObjektNode((Vector) cli.execute("TreeHandler.findOrSaveNode", "messung_ordner","TestNode", rootNode.getId()));
		 assertTrue(n.getId()!=rootNode.getId());
		 Integer id = n.getId();		 		 
		 n = new ObjektNode((Vector) cli.execute("TreeHandler.findOrSaveNode", "messung_ordner","TestNode", rootNode.getId()));		
		 assertEquals(id, n.getId());		 
		 assertTrue((Boolean)cli.execute("TreeHandler.deleteNode", n.getId()));
		
	}

	
	

}
