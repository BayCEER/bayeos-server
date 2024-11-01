package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Ignore;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.AbstractClientTest;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektArt;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektNode;

public class TestObjektHandlerIT extends AbstractClientTest{	
	 @Ignore @Test
	public void testUpdateDataFrame()  {
		// Get Root
		
		try {
			ObjektNode rootNode =  new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot", ObjektArt.MESSUNG_ORDNER.toString(),null,null,null));			
			assertNotNull(rootNode);
			
			// Create Frame 
			ObjektNode frameNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.newNode", ObjektArt.DATA_FRAME.toString(), "Frame", rootNode.getId()));
			assertNotNull(frameNode);
			
			/*                     0:plan_start
		     *                     1:plan_end
		     *                     2:rec_start
		     *                     3:rec_end
		     *                     4:bezeichnung
		     *                     5:beschreibung
		     *                     6:timezone_id
		     */
			Calendar c = GregorianCalendar.getInstance();
			c.set(GregorianCalendar.MILLISECOND, 0);			
			Date d = c.getTime();		    
		    
		
			cli.getXmlRpcClient().execute("ObjektHandler.updateObjekt",frameNode.getId(),ObjektArt.DATA_FRAME.toString(),new Object[]{d,d,d,d,"Bez","Besch",1});			
			Vector o = (Vector) cli.getXmlRpcClient().execute("ObjektHandler.getObjekt", frameNode.getId(), ObjektArt.DATA_FRAME.toString());
			
			assertEquals(d,o.get(15));
			assertEquals(d,o.get(16));
			assertEquals(d,o.get(17));
			assertEquals(d,o.get(18));
			assertEquals("Bez",(String)o.get(20));
			assertEquals("Besch",(String)o.get(21));
			assertEquals((int)1,(int)o.get(22));
			
			cli.getXmlRpcClient().execute("TreeHandler.deleteNode", frameNode.getId());
			
		} catch (XmlRpcException e) {
			fail(e.getMessage());
		}						

					
				
	}
	
	
	@Test
	public void testUpdateObjekt() {				
		try {
			Vector r = (Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot",ObjektArt.MESSUNG_ORDNER.toString(),false,null,null);			
			Integer rId  = (Integer) r.get(2);
			assertNotNull(rId);						
			Vector res = (Vector) cli.getXmlRpcClient().execute("TreeHandler.newNode",ObjektArt.MESSUNG_MASSENDATEN.toString(),"Test Node",rId);									
			Integer nId = (Integer) res.get(2);
			assertNotNull(nId);						
			cli.getXmlRpcClient().execute("ObjektHandler.updateObjekt",nId,ObjektArt.MESSUNG_MASSENDATEN.toString(),new Object[] {"Label","Description",120,null,null,1,2});			
			Vector o = (Vector) cli.getXmlRpcClient().execute("ObjektHandler.getObjekt", nId, ObjektArt.MESSUNG_MASSENDATEN.toString());												
			cli.getXmlRpcClient().execute("TreeHandler.deleteNode", nId);
			
		} catch (XmlRpcException e) {
			fail(e.getMessage());
		}
	}

}
