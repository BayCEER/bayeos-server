package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.nio.ByteBuffer;
import java.util.Vector;

import de.unibayreuth.bayceer.bayeos.client.NodeTest;

public class TestMassendatenHandler extends NodeTest {
	
	//TODO: Replace getRows with getByteRows and use exact time values to query result !
	public void testUpsert() throws Exception {						
		// Create Payload 
		ByteBuffer bb = ByteBuffer.allocate(16);
		bb.putInt(testNode.getId()); // 4		
		bb.putLong(now.getTime());   // 8
		bb.putFloat(12.0F);		  // 4

		// Call Method 
		Boolean res = (Boolean)cli.execute("MassenTableHandler.upsertByteRows",bb.array());		
		bb.clear();
		assertTrue(res);

		// Retrieve Value for t:now		
		Vector ret = (Vector) cli.execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
		assertNotNull(ret);
		assertEquals(2, ret.size());		
		byte[] ba = (byte[]) ret.get(1);		
		bb = ByteBuffer.wrap(ba);				
		assertEquals(now.getTime()/1000,bb.getInt());
		assertEquals(12.0F,bb.getFloat());
		assertEquals(0, bb.get());		
		bb.clear();
		
		// Upsert with new value		
		bb = ByteBuffer.allocate(16);
		bb.putInt(testNode.getId()); // 4		
		bb.putLong(now.getTime());   // 8
		bb.putFloat(111111.0F);		  // 4
							
		res = (Boolean)cli.execute("MassenTableHandler.upsertByteRows",bb.array());		
		bb.clear();		
		assertTrue(res);		

		// Retrieve Value for t:now		
		ret = (Vector) cli.execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
		assertNotNull(ret);
		assertEquals(2, ret.size());		
		ba = (byte[]) ret.get(1);		
		bb = ByteBuffer.wrap(ba);				
		assertEquals(now.getTime()/1000,bb.getInt());
		assertEquals(111111.0F,bb.getFloat());
		assertEquals(0, bb.get());
		bb.clear();								
	}

	

}
