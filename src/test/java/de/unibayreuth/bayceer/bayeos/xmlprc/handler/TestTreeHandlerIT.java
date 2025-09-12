package de.unibayreuth.bayceer.bayeos.xmlprc.handler;


import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.NodeTest;
import de.unibayreuth.bayceer.bayeos.objekt.ArrayConverter;
import de.unibayreuth.bayceer.bayeos.objekt.ArraySerializationException;
import de.unibayreuth.bayceer.bayeos.objekt.Art;
import de.unibayreuth.bayceer.bayeos.objekt.Messung;
import de.unibayreuth.bayceer.bayeos.objekt.MessungOrdner;
import de.unibayreuth.bayceer.bayeos.objekt.Node;

public class TestTreeHandlerIT extends NodeTest {
	
	
	@Test
	public void testfindOrSaveNode() throws XmlRpcException {
		
		 Node n = cli.findOrCreateNode(testNode.getId(), "TestNode", Art.MESSUNG_ORDNER);
		 assertNotNull(n);
		 
		 Node n2 = cli.findOrCreateNode(testNode.getId(), "TestNode", Art.MESSUNG_ORDNER);
		 assertNotNull(n2);
		 
		 assertEquals(n.getId(), n2.getId());
		 
		 cli.deleteNode(n2);
		 
		 		 	 		 		 
		
	}
	
	@SuppressWarnings({ "static-access", "rawtypes" })
	@Test
	public void testGetObjektWithLocation() throws XmlRpcException, IOException, ArraySerializationException {
		
		
		MessungOrdner f = cli.getNodeObjekt(testNode, MessungOrdner.class);
		f.setLat(45.0d);
		f.setLon(19.0d);
		
		cli.updateNodeObjekt(f);
							
		Node n1 = cli.newNode(testNode.getId(),"SeriesLoc",Art.MESSUNG_MASSENDATEN);		
		Messung m = cli.getNodeObjekt(n1, Messung.class);		
		assertNotNull(m);		
		m.setLat(50.0d);
		m.setLon(30.0d);
		m.setAlt(535.0d);
		cli.updateNodeObjekt(m);
		
		Node n2 = cli.newNode(testNode.getId(),"SeriesWithoutLoc",Art.MESSUNG_MASSENDATEN);
											
		Vector a = (Vector) cli.getXmlRpcClient().execute("TreeHandler.getObjektTreeWithLocation",rootNode.getId(),null,null,null,null,null,null);
		assertNotNull(a);
		assertEquals(3, a.size());
		
		HashMap<String, ObjektWithLocation> res = new HashMap<String, ObjektWithLocation>(3);
		for (Object o : a) {
			ObjektWithLocation ow = ArrayConverter.toClass(ObjektWithLocation.class, ((Vector)o).toArray());
			res.put(ow.name, ow);
		}
		
		ObjektWithLocation r = res.get("Test Folder");	 	
		assertEquals(45.0,r.lat);
		assertEquals(19.0,r.lon);
		assertNull(r.alt);
		
		r = res.get("SeriesLoc"); 
		assertEquals(50.0,r.lat);
		assertEquals(30.0,r.lon);
		assertEquals(535.0,r.alt);
		
		r = res.get("SeriesWithoutLoc");
		assertEquals(45.0,r.lat);
		assertEquals(19.0,r.lon);
		assertNull(r.alt);
		
		
		cli.deleteNode(n1);
		cli.deleteNode(n2);
								
		
	}
	
	
	
}
