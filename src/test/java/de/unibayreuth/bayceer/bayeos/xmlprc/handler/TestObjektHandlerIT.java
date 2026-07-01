package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.NodeTest;
import de.unibayreuth.bayceer.bayeos.objekt.ArrayConverter;
import de.unibayreuth.bayceer.bayeos.objekt.ArraySerializationException;
import de.unibayreuth.bayceer.bayeos.objekt.Art;
import de.unibayreuth.bayceer.bayeos.objekt.MessungOrdner;
import de.unibayreuth.bayceer.bayeos.objekt.Objekt;

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
	
	
	@SuppressWarnings({ "static-access", "unchecked" })
	@Test
	public void testGetObjekte() throws XmlRpcException {
		Vector<Integer> ids = new Vector<Integer>();
		ids.add(rootNode.getId());
		ids.add(999);
		Vector<Vector<Object>> vec = (Vector<Vector<Object>>) cli.getXmlRpcClient().execute("ObjektHandler.getObjekte", ids, Art.MESSUNG_ORDNER);
		assertNotNull(vec);
		
		for (Iterator<Vector<Object>> it = vec.iterator(); it.hasNext();) {
			try {
				Objekt o =  (Objekt) ArrayConverter.toObject(Objekt.class, it.next().toArray());
				assertEquals(rootNode.getId(), o.getId());
			} catch (ArraySerializationException e) {
				throw new XmlRpcException(0, e.getMessage());
			} 			 
			
		}
		
		
		
	}
	
	
	
		
	
	
	
	
	
	

}
