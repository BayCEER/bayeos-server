package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Vector;


import org.apache.xmlrpc.XmlRpcException;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.client.AbstractClientTest;
import de.unibayreuth.bayceer.bayeos.objekt.DataType;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektArt;
import de.unibayreuth.bayceer.bayeos.objekt.ObjektNode;
import de.unibayreuth.bayceer.bayeos.server.App;


public class TestDataFrameHandlerIT extends AbstractClientTest{
	
	private final static Logger log = LoggerFactory.getLogger(AbstractClientTest.class);
	
	@Ignore @Test(timeout=5000)
	public void testWritePerfomance() {	
		try {
			ObjektNode rootNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot", ObjektArt.MESSUNG_ORDNER.toString(),null,null,null));			
			assertNotNull(rootNode);
			
			
			// Create Frame 
			ObjektNode frameNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.newNode", ObjektArt.DATA_FRAME.toString(), "Huge Frame", rootNode.getId()));
			assertNotNull(frameNode);						
			
		
			String[] stringValues = new String[1000];
			for (int i = 0; i < stringValues.length; i++) {
				stringValues[i]="ABCDEFG";
			}
			
			long now = System.currentTimeMillis();
			addColumn(frameNode.getId(), 1, "COLUMN A", DataType.STRING,stringValues);
			log.debug(String.format("Added column with 1000 string values in %d millis.",System.currentTimeMillis() - now));
			
			
			Date[] dateValues = new Date[1000];
			Calendar c = new GregorianCalendar();			
			c.set(Calendar.MILLISECOND, 0); // XMLRPC uses dateTime.iso8601 with second precision
			for (int i = 0; i < dateValues.length; i++) {
				c.add(Calendar.MINUTE, -5);
				dateValues[i] = c.getTime();			
			}
			
			now = System.currentTimeMillis();
			addColumn(frameNode.getId(), 2, "COLUMN B", DataType.DATE,dateValues);
			log.debug(String.format("Added column with 1000 date values in %d millis.",System.currentTimeMillis() - now));
			
			
			Integer[] intValues = new Integer[1000];
			for (int i = 0; i < intValues.length; i++) {
				intValues[i] = i;
			}
			now = System.currentTimeMillis();
			addColumn(frameNode.getId(), 3, "COLUMN C", DataType.INTEGER,intValues);
			log.debug(String.format("Added column with 1000 int values in %d millis.",System.currentTimeMillis() - now));
			
			
			
			cli.getXmlRpcClient().execute("TreeHandler.deleteNode", frameNode.getId());
			
		} catch (XmlRpcException e) {
			fail(e.getMessage());
		}						
				
		
	}
	
		
	
	 @Ignore @Test
	public void testWriteGetFrame()  {
	
		try {
								
			// Get Root
			ObjektNode rootNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot", ObjektArt.MESSUNG_ORDNER.toString(),null,null,null));						
			assertNotNull(rootNode);
			
			// Create Frame 
			ObjektNode frameNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.newNode", ObjektArt.DATA_FRAME.toString(), "Frame", rootNode.getId()));
			assertNotNull(frameNode);						
						
			String[] stringValues = new String[]{"A","B","C","D"};			
			addColumn(frameNode.getId(), 1, "COLUMN A", DataType.STRING,stringValues);
			
									
			Integer[] intValues = new Integer[]{1,2,3,4};			
			addColumn(frameNode.getId(), 2, "COLUMN B", DataType.INTEGER,intValues);
			
			Double[] doubleValues = new Double[]{1.1,1.2,1.3,1.4};
			addColumn(frameNode.getId(), 3, "COLUMN C", DataType.DOUBLE,doubleValues);
									
			Boolean[] booleanValues = new Boolean[]{true,false,true,false};
			addColumn(frameNode.getId(), 4, "COLUMN D", DataType.BOOLEAN,booleanValues);
			
			
			Date[] dateValues = new Date[4];
			Calendar c = new GregorianCalendar();
			c.set(Calendar.MILLISECOND, 0); // XMLRPC uses dateTime.iso8601 with second precision   
			dateValues[0] = c.getTime();
			
			c.add(Calendar.MINUTE, 5);
			dateValues[1] = c.getTime();
			c.add(Calendar.MINUTE, 5);
			dateValues[2] = c.getTime();
			c.add(Calendar.MINUTE, 5);
			dateValues[3] = c.getTime();											
			addColumn(frameNode.getId(), 5, "COLUMN E", DataType.DATE,dateValues);
						
			// Get FrameRows 
		 	Vector frameValues = (Vector) cli.getXmlRpcClient().execute("DataFrameHandler.getFrameRows",frameNode.getId(),null);		 			 	

		 	// First Element Meta 		 	
		 	assertEquals(2, frameValues.size());		 			 	

		 	// Check Meta
		 	Vector cols = (Vector) frameValues.get(0);
		 	assertEquals(5, cols.size());  
		 	
		 	// Col A
		 	Vector col = (Vector)cols.get(0);
		 	assertTrue((Integer)col.get(0)>0);
		 	assertEquals(1, col.get(1));
		 	assertEquals("COLUMN A", col.get(2));
		 	assertEquals(DataType.STRING.name(), col.get(3));		 	
		 	// Col B
		 	col = (Vector)cols.get(1);		 
		 	assertTrue((Integer)col.get(0)>0);
		 	assertEquals(2, col.get(1));
		 	assertEquals("COLUMN B", col.get(2));
		 	assertEquals(DataType.INTEGER.name(), col.get(3));		 			 	
		 	// Col C
		 	col = (Vector)cols.get(2);		 
		 	assertTrue((Integer)col.get(0)>0);
		 	assertEquals(3, col.get(1));
		 	assertEquals("COLUMN C", col.get(2));
		 	assertEquals(DataType.DOUBLE.name(), col.get(3));		 	
		 	// Col D
		 	col = (Vector)cols.get(3);		 
		 	assertTrue((Integer)col.get(0)>0);
		 	assertEquals(4, col.get(1));
		 	assertEquals("COLUMN D", col.get(2));
		 	assertEquals(DataType.BOOLEAN.name(), col.get(3));		 	
		 	// Col E
		 	col = (Vector)cols.get(4);		 
		 	assertTrue((Integer)col.get(0)>0);
		 	assertEquals(5, col.get(1));
		 	assertEquals("COLUMN E", col.get(2));
		 	assertEquals(DataType.DATE.name(), col.get(3));
		 	
		 			 	
		 	// Second Element Data
		 	Vector rows = (Vector) frameValues.get(1);
		 	assertEquals(4, rows.size());
		 	
		 	for(int r=0;r<rows.size();r++){
		 		Vector row = (Vector) rows.get(r);
		 		assertEquals(6, row.size());
		 		assertEquals(r+1, row.get(0)); // RowNum		 		
		 		assertEquals(stringValues[r], row.get(1)); 
		 		assertEquals(intValues[r], row.get(2));
		 		assertEquals(doubleValues[r], row.get(3));
		 		assertEquals(booleanValues[r], row.get(4));
		 		assertEquals(dateValues[r], (Date)row.get(5));		 				 		
		 	}
		 	
		 	
		 	// Drop Values of Column 2
		 	Integer id = (Integer) ((Vector)cols.get(1)).get(0);
		 	log.info("Delete column values for Id:" + id);
		    
		    assertTrue((Boolean)(cli.getXmlRpcClient().execute("DataFrameHandler.deleteColValues",id)));
		    
		    frameValues = (Vector) cli.getXmlRpcClient().execute("DataFrameHandler.getFrameRows",frameNode.getId(),null);		 	

		 	// First Element Meta 		 	
		 	assertEquals(2, frameValues.size());		 			 	

		 	// Check Meta
		 	cols = (Vector) frameValues.get(0);
		 	assertEquals(5, cols.size());
		 	
		 	// Second Element Data
		 	rows = (Vector) frameValues.get(1);
		 	assertEquals(4, rows.size());
		 	
		 	for(int r=0;r<rows.size();r++){
		 		Vector row = (Vector) rows.get(r);
		 		assertEquals(6, row.size());
		 		assertEquals(r+1, row.get(0)); // RowNum		 		
		 		assertEquals(stringValues[r], row.get(1));
		 		// Delete Colums Values 
		 		assertEquals(null, row.get(2));
		 		assertEquals(doubleValues[r], row.get(3));
		 		assertEquals(booleanValues[r], row.get(4));
		 		assertEquals(dateValues[r], (Date)row.get(5));		 				 		
		 	}
		 	
		 	// Filter 
		 	Vector rowIndexes = new Vector();
		 	rowIndexes.add(2);		 	
		 	frameValues = (Vector) cli.getXmlRpcClient().execute("DataFrameHandler.getFrameRows",frameNode.getId(), rowIndexes);
		 	rows = (Vector) frameValues.get(1);
		 	assertEquals(1, rows.size());		 	
		 	Vector row = (Vector) rows.get(0);
		 	assertEquals("B", row.get(1));
		 	
		 	
		  
			// Drop Frame including columns and references
		 	cli.getXmlRpcClient().execute("TreeHandler.deleteNode", frameNode.getId());
		 						
												
		} catch (XmlRpcException e) {
				fail(e.getMessage());
		}		
								
	}
	
	/*
	*					0:plan_start 
    * 					1:plan_end
    * 					2:rec_start 
    * 					3:rec_end 
    * 					4:bezeichnung 
    * 					5:beschreibung 
    * 					6:col_index
    * 					7:data_type
    * */
	private ObjektNode addColumn(Integer frameId, Integer index, String name, DataType type, Object[] values) throws XmlRpcException {				
			log.debug("Create " + type.name() + " Column");
			// Create Column
			ObjektNode colNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.newNode", ObjektArt.DATA_COLUMN.toString(),name,frameId));
			// Update Column 
			cli.getXmlRpcClient().execute("ObjektHandler.updateObjekt", colNode.getId(), ObjektArt.DATA_COLUMN.toString(), new Object[]{null,null,null,null,name,name,index,type.name()});						
			// Add Values to Column  
			cli.getXmlRpcClient().execute("DataFrameHandler.writeColValues",colNode.getId(), values);
			return colNode;
		
	}
	
	
	 @Ignore @Test 
	public void testGetColumRows(){
		try {
		ObjektNode rootNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.getRoot", ObjektArt.MESSUNG_ORDNER.toString(),null,null,null));			
		assertNotNull(rootNode);		
		
		// Create Frame A
		ObjektNode frameNodeA = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.newNode", ObjektArt.DATA_FRAME.toString(), "Test Frame", rootNode.getId()));
		assertNotNull(frameNodeA);			
		String[] stringValues = new String[]{"A","B","C","D"};			
		ObjektNode c1 = addColumn(frameNodeA.getId(), 1, "COLUMN A", DataType.STRING,stringValues);
				
		// Create Frame B
		ObjektNode frameNodeB = new ObjektNode((Vector) cli.getXmlRpcClient().execute("TreeHandler.newNode", ObjektArt.DATA_FRAME.toString(), "Test Frame", rootNode.getId()));
		assertNotNull(frameNodeB);
		Integer[] intValues = new Integer[]{1,2,3,4};			
		ObjektNode c2 = addColumn(frameNodeB.getId(), 1, "COLUMN A", DataType.INTEGER,intValues);
		
		
		// Get Both Columns without filtering						
		Vector colIds = new Vector(2);
		colIds.add(c1.getId());
		colIds.add(c2.getId());
		Vector parm = new Vector(2);
		parm.add(colIds);
		parm.add(null);		
		Vector frameValues = (Vector) cli.getXmlRpcClient().execute("DataFrameHandler.getColumnRows",parm);
		
		Vector meta = ((Vector)frameValues.get(0));
		Vector cols = ((Vector)frameValues.get(1));		 
		assertEquals(2, meta.size());
		assertEquals(4, cols.size());
		
		Vector m1 = (Vector) meta.get(0);
		assertEquals(DataType.STRING.name(), m1.get(3));		
		Vector m2 = (Vector) meta.get(1);
		assertEquals(DataType.INTEGER.name(), m2.get(3));
				
				
		// Changed order by id 
		colIds = new Vector(2);
		colIds.add(c2.getId());
		colIds.add(c1.getId());
		parm = new Vector(2);
		parm.add(colIds);
		parm.add(null);		
		
		frameValues = (Vector) cli.getXmlRpcClient().execute("DataFrameHandler.getColumnRows",parm);
		meta = ((Vector)frameValues.get(0));
		cols = ((Vector)frameValues.get(1));		
		assertEquals(2, meta.size());
		assertEquals(4, cols.size());
		
		m1 = (Vector) meta.get(0);
		assertEquals(DataType.INTEGER.name(), m1.get(3));		
		m2 = (Vector) meta.get(1);
		assertEquals(DataType.STRING.name(), m2.get(3));
				
		// Get Values by filter
		colIds = new Vector(2);
		colIds.add(c1.getId());
		colIds.add(c2.getId());
		parm = new Vector(2);
		parm.add(colIds);
		Vector rowIndexes = new Vector(2);
		rowIndexes.add(2);
		rowIndexes.add(4);
		parm.add(rowIndexes);		
		frameValues = (Vector) cli.getXmlRpcClient().execute("DataFrameHandler.getColumnRows",parm);
		
		meta = ((Vector)frameValues.get(0));
		cols = ((Vector)frameValues.get(1));		
		
		assertEquals(2, meta.size());
		assertEquals(2, cols.size());
		
		m1 = (Vector) cols.get(0);
		assertEquals("B",m1.get(1).toString());		
		assertEquals(2,(int)m1.get(2));
				
					
		
		// Drop Frames including columns and references
	 	cli.getXmlRpcClient().execute("TreeHandler.deleteNode", frameNodeA.getId());
	 	cli.getXmlRpcClient().execute("TreeHandler.deleteNode", frameNodeB.getId());
		
		
		} catch (Exception e){
			fail(e.getMessage());
		}
		
	}
	
	
	 @Ignore @Test
	public void testGetMaxRowIndex(){
		
		try {
		// Get Root
			ObjektNode rootNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute(
					"TreeHandler.getRoot", ObjektArt.MESSUNG_ORDNER.toString(),
					null, null, null));
			assertNotNull(rootNode);

			// Create Frame
			ObjektNode frameNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute(
					"TreeHandler.newNode", ObjektArt.DATA_FRAME.toString(),
					"Frame", rootNode.getId()));
			assertNotNull(frameNode);

			String[] stringValues = new String[] { "A", "B", "C", "D" };
			ObjektNode a = addColumn(frameNode.getId(), 1, "COLUMN A",
					DataType.STRING, stringValues);

			Integer[] intValues = new Integer[] { 1, 2, 3 };
			ObjektNode b = addColumn(frameNode.getId(), 2, "COLUMN B",
					DataType.INTEGER, intValues);
								 
			Vector v = new Vector();
			Vector r = new Vector();
			r.add(a.getId());
			r.add(b.getId());
			v.add(r);
			
			Integer va =  (Integer) cli.getXmlRpcClient().execute("DataFrameHandler.getMaxRowIndex", v);			
			assertEquals(4, (int)va);
					
		} catch (Exception e){
			fail(e.getMessage());
		}
	}
	
	 @Ignore @Test
	public void testUpdateColValues() {
		try{
			ObjektNode rootNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute(
					"TreeHandler.getRoot", ObjektArt.MESSUNG_ORDNER.toString(),
					null, null, null));
			assertNotNull(rootNode);

			// Create Frame
			ObjektNode frameNode = new ObjektNode((Vector) cli.getXmlRpcClient().execute(
					"TreeHandler.newNode", ObjektArt.DATA_FRAME.toString(),
					"Frame", rootNode.getId()));
			assertNotNull(frameNode);

			String[] stringValues = new String[] { "A", "B", "C", "D" };
			ObjektNode a = addColumn(frameNode.getId(), 1, "UPDATE COLUMN", DataType.STRING, stringValues);
			
			Vector rowIndex = new Vector();
			rowIndex.add(2);
			rowIndex.add(5);
			Vector values = new Vector();
			values.add("X");
			values.add("Z");			
			Boolean bol = (Boolean) cli.getXmlRpcClient().execute("DataFrameHandler.updateColValues", a.getId(),rowIndex,values);						
			assertTrue(bol);
			
			Vector ids = new Vector();
			ids.add(a.getId());
				
			Vector rec = (Vector) cli.getXmlRpcClient().execute("DataFrameHandler.getColumnRows",ids,rowIndex);
			Vector val = (Vector) rec.get(1);
			Vector row = (Vector) val.get(0);
			assertEquals("X", row.get(1));
			row = (Vector) val.get(1);
			assertEquals("Z", row.get(1));
			
			
			// Update with null value			
			rowIndex = new Vector();
			rowIndex.add(5);
			values = new Vector();
			values.add(null);
			assertTrue((Boolean) cli.getXmlRpcClient().execute("DataFrameHandler.updateColValues", a.getId(),rowIndex,values));
			
			
			Vector v = new Vector();
			Vector r = new Vector();
			r.add(a.getId());
			v.add(r);
			
			Integer va =  (Integer) cli.getXmlRpcClient().execute("DataFrameHandler.getMaxRowIndex", v);				
		    assertEquals(4, va.intValue());
		     
			
			
		} catch (Exception e){
			fail(e.getMessage());
		}
	}
	
		

}
