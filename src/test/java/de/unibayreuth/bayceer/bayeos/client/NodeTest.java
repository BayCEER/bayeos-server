/**
 * 
 */
package de.unibayreuth.bayceer.bayeos.client;

import java.io.IOException;
import java.util.Date;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.objekt.ObjektArt;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektNode;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.StatusFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.TimeFilter;

/**
 * A basic test that provides a root node and a child node to work on
 * @author oliver
 *
 */
public abstract class NodeTest extends AbstractClientTest {
			
	public Vector statusFilter;
	public Vector timeFilter;
	public ObjektNode rootNode;
	public ObjektNode testNode;
	public Date now;


	@Override
	public void setUp() throws XmlRpcException, IOException {
		super.setUp();
		
		statusFilter = new StatusFilter(new int[] {0,1,2}).getVector();
		now = new Date();
		
		timeFilter = new TimeFilter(new Date(now.getTime() -1000),new Date(now.getTime() +1000)).getVector();
				
		rootNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot", ObjektArt.MESSUNG_ORDNER.toString(),null,null,null));			
		assertNotNull(rootNode);
		
		testNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.newNode", ObjektArt.MESSUNG_MASSENDATEN.toString(), "TestSeries", rootNode.getId()));
		assertNotNull(testNode);
		
	}
	
	
	@Override
	public void tearDown() throws XmlRpcException {				
		assertTrue((Boolean)cli.getXmlRpcClient().execute("TreeHandler.deleteNode", testNode.getId()));
		super.tearDown();
	}

}
