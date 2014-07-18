package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.NodeTest;

public class TestMessungenHandler  extends NodeTest {

	
	@Test
	public void testRefreshRecInterval() throws XmlRpcException {
		
		Integer id = testNode.getId();
		
		// Add sample data with sql 
		
		assertTrue((boolean) cli.execute("MessungHandler.refreshRecInterval",id));
		
		// Get Node rec_start and rec_end
		
		
	}

}
