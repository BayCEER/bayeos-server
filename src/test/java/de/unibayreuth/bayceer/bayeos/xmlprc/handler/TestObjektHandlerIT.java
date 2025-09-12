package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.io.IOException;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.NodeTest;
import de.unibayreuth.bayceer.bayeos.objekt.ArraySerializationException;
import de.unibayreuth.bayceer.bayeos.objekt.MessungOrdner;

public class TestObjektHandlerIT extends NodeTest {

	@Test
	public void testReadRootFolderAsObject() throws XmlRpcException, IOException, ArraySerializationException {
		
		
		MessungOrdner m = cli.getNodeObjekt(testNode, MessungOrdner.class);
		m.setLat(45.0d);
		m.setLon(19.0d);
		
		cli.updateNodeObjekt(m);
		
		MessungOrdner mp = cli.getNodeObjekt(testNode,MessungOrdner.class);
		
		assertEquals(m.getLat(), mp.getLat());
		assertEquals(m.getLon(), mp.getLon());
		
						
	}
	
	
		
	
	
	
	
	
	

}
