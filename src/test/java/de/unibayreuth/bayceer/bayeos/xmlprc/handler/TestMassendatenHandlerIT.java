package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.nio.ByteBuffer;
import java.util.Vector;

import org.junit.Ignore;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.NodeTest;

public class TestMassendatenHandlerIT extends NodeTest {
	
	
	@Ignore @Test
	public void testAddUpsertSingle() throws Exception {
		Vector v = new Vector();
		v.add(testNode.getId());
		v.add(now);
		v.add(new Double(14.2));
		v.add(0);
		Boolean res = (Boolean)cli.getXmlRpcClient().execute("MassenTableHandler.addRow",v);		
		assertTrue(res);
		
		Vector ret = (Vector) cli.getXmlRpcClient().execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
		assertNotNull(ret);
		assertEquals(2, ret.size());				
		byte[] ba = (byte[]) ret.get(1);		
		ByteBuffer bb = ByteBuffer.wrap(ba);				
		assertEquals(now.getTime()/1000,bb.getInt());
		assertEquals(14.2F,bb.getFloat());
		assertEquals(0, bb.get());		
		bb.clear();
				
		
		v.set(2,new Double(14.2));
		res = (Boolean)cli.getXmlRpcClient().execute("MassenTableHandler.upsertRow",v);
		assertTrue(res);
		
		ret = (Vector) cli.getXmlRpcClient().execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
		assertNotNull(ret);
		assertEquals(2, ret.size());				
		byte[] ba2 = (byte[]) ret.get(1);		
		bb = ByteBuffer.wrap(ba2);				
		assertEquals(now.getTime()/1000,bb.getInt());
		assertEquals(14.2F,bb.getFloat());
		assertEquals(0, bb.get());		
		bb.clear();
		
		
	}
	
			
	
	@Ignore @Test
	public void testAddUpsertMany() throws Exception {						
		// Create Payload 
		ByteBuffer bb = ByteBuffer.allocate(16);
		bb.putInt(testNode.getId()); // 4		
		bb.putLong(now.getTime());   // 8
		bb.putFloat(12.0F);		  // 4

		// Call Method 
		Boolean res = (Boolean)cli.getXmlRpcClient().execute("MassenTableHandler.upsertByteRows",bb.array());		
		bb.clear();
		assertTrue(res);

		// Retrieve Value for t:now		
		Vector ret = (Vector) cli.getXmlRpcClient().execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
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
		bb.putFloat(12.0F);		 // 4
											
		res = (Boolean)cli.getXmlRpcClient().execute("MassenTableHandler.upsertByteRows",bb.array());		
		bb.clear();		
		assertTrue(res);		

		// Retrieve Value for t:now		
		ret = (Vector) cli.getXmlRpcClient().execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
		assertNotNull(ret);
		assertEquals(2, ret.size());		
		ba = (byte[]) ret.get(1);		
		bb = ByteBuffer.wrap(ba);				
		assertEquals(now.getTime()/1000,bb.getInt());
		assertEquals(12.0F,bb.getFloat());
		assertEquals(0, bb.get());
		bb.clear();								
	}
	
	@Ignore @Test
	public void testUpsert() throws Exception {
		
		
		// Create Payload 
		
				ByteBuffer bb = ByteBuffer.allocate(48);
				bb.putInt(testNode.getId()); // 4		
				bb.putLong(now.getTime());   // 8
				bb.putFloat(10.0F);		  // 4
		
				bb.putInt(testNode.getId()); // 4		
				bb.putLong(now.getTime());   // 8
				bb.putFloat(12.0F);		  // 4
				
				bb.putInt(testNode.getId()); // 4		
				bb.putLong(now.getTime());   // 8
				bb.putFloat(14.0F);		  // 4


				// Call Method 
				Boolean res = (Boolean)cli.getXmlRpcClient().execute("MassenTableHandler.upsertByteRows",bb.array());		
				bb.clear();
				assertTrue(res);
				
				// Retrieve Value for t:now		
				Vector ret = (Vector) cli.getXmlRpcClient().execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
				assertNotNull(ret);
				assertEquals(2, ret.size());		
				byte[] ba = (byte[]) ret.get(1);
				
				assertEquals(9, ba.length);				
				bb = ByteBuffer.wrap(ba);				
				assertEquals(now.getTime()/1000,bb.getInt());
				assertEquals(14.0F,bb.getFloat());
				assertEquals(0, bb.get());
				bb.clear();

	}
	
	@Ignore @Test
	public void testInsert() throws Exception {
		ByteBuffer bb = ByteBuffer.allocate(48);
		bb.putInt(testNode.getId()); // 4		
		bb.putLong(now.getTime());   // 8
		bb.putFloat(10.0F);		  // 4

		bb.putInt(testNode.getId()); // 4		
		bb.putLong(now.getTime());   // 8
		bb.putFloat(12.0F);		  // 4
		
		bb.putInt(testNode.getId()); // 4		
		bb.putLong(now.getTime());   // 8
		bb.putFloat(14.0F);		  // 4
		
		
		// Call Method 
		Boolean res = (Boolean)cli.getXmlRpcClient().execute("MassenTableHandler.addByteRows",bb.array());		
		bb.clear();
		assertTrue(res);
		
		// Retrieve Value for t:now		
		Vector ret = (Vector) cli.getXmlRpcClient().execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
		assertNotNull(ret);
		assertEquals(2, ret.size());		
		byte[] ba = (byte[]) ret.get(1);
		
		assertEquals(9, ba.length);				
		bb = ByteBuffer.wrap(ba);				
		assertEquals(now.getTime()/1000,bb.getInt());
		assertEquals(10.0F,bb.getFloat());
		assertEquals(0, bb.get());
		bb.clear();

	}
	 
	 
	@Ignore @Test
	public void testAddByteRows() throws Exception {
		
		
		ByteBuffer bb = ByteBuffer.allocate(16).putInt(testNode.getId()).putLong(now.getTime()).putFloat(12.0F);			

		// Call Method 
		Boolean res = (Boolean)cli.getXmlRpcClient().execute("MassenTableHandler.addByteRows",bb.array());		
		assertTrue(res);
		
		// Retrieve Value for t:now		
		Vector ret = (Vector) cli.getXmlRpcClient().execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
		assertNotNull(ret);
		assertEquals(9, ((byte[])ret.get(1)).length);

		
		// Call Method 
		bb = ByteBuffer.allocate(16).putInt(testNode.getId()).putLong(now.getTime()).putFloat(16.0F);
		res = (Boolean)cli.getXmlRpcClient().execute("MassenTableHandler.upsertByteRows",bb.array());		
		assertTrue(res);
		
				
		// Retrieve Value for t:now		
		ret = (Vector) cli.getXmlRpcClient().execute("MassenTableHandler.getRows",testNode.getId(),timeFilter,statusFilter);		
		assertNotNull(ret);
		assertEquals(9, ((byte[])ret.get(1)).length);
		
		byte[] ba = (byte[]) ret.get(1);		
		bb = ByteBuffer.wrap(ba);				
		assertEquals(now.getTime()/1000,bb.getInt());
		assertEquals(16.0F,bb.getFloat());
		assertEquals(0,bb.get());

		
	}
	

}
