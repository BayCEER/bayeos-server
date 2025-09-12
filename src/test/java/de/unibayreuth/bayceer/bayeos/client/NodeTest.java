/**
 * 
 */
package de.unibayreuth.bayceer.bayeos.client;

import java.io.IOException;
import java.util.Date;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.objekt.Art;
import de.unibayreuth.bayceer.bayeos.objekt.Node;
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
	public Node rootNode;
	public Node testNode;
	public Date now;


	@Override
	public void setUp() throws XmlRpcException, IOException {
		super.setUp();
		
		statusFilter = new StatusFilter(new int[] {0,1,2}).getVector();
		
		now = new Date();		
		timeFilter = new TimeFilter(new Date(now.getTime() -1000),new Date(now.getTime() +1000)).getVector();
		
		rootNode = cli.getRoot(Art.MESSUNG_ORDNER);
		assertNotNull(rootNode);
				
		testNode = cli.newNode(rootNode.getId(),"Test Folder",Art.MESSUNG_ORDNER);
		assertNotNull(testNode);		
	}
	
	
	@Override
	public void tearDown() throws XmlRpcException {
		cli.deleteNode(testNode);		
		super.tearDown();
	}

}
