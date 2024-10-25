package de.unibayreuth.bayceer.bayeos.xmlprc.handler;


import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Ignore;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.NodeTest;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektNode;

public class TestTreeHandlerIT extends NodeTest {
	
	 @Ignore @Test
	public void testGetRoot() throws XmlRpcException {
		Vector r = (Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot", "messung_%",false,"week",null);		
		assertNull(r);
		
		r = (Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot", "messung_ordner",false,"week",null);		
		assertNotNull(r);
				
		r = (Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot", "web_ordner",false,"week",null);		
		assertNotNull(r);
	}
	
	 @Ignore @Test
	public void testfindOrSaveNode() throws XmlRpcException {								
		 ObjektNode n = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.findOrSaveNode", "messung_ordner","TestNode", rootNode.getId()));
		 assertTrue(n.getId()!=rootNode.getId());
		 Integer id = n.getId();		 		 
		 n = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.findOrSaveNode", "messung_ordner","TestNode", rootNode.getId()));		
		 assertEquals(id, n.getId());		 
		 assertTrue((Boolean)cli.getXmlRpcClient().execute("TreeHandler.deleteNode", n.getId()));
		
	}
}
