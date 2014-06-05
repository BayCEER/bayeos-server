/*******************************************************************************
 * Copyright (c) 2011 University of Bayreuth - BayCEER.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     University of Bayreuth - BayCEER - initial API and implementation
 ******************************************************************************/
package de.unibayreuth.bayceer.bayeos.xmlrpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

import org.apache.log4j.Logger;

public class ByteUtils {
			
	private static final Logger logger = Logger.getLogger(ByteUtils.class);
    
    public static final int INTEGER = 0;
    public static final int FLOAT = 1;
    public static final int DATE = 2;            
    public static final int NULL = 0x7fc00000;
        
	
	public static byte[] getByte(ResultSet rs) throws SQLException, IOException {
		logger.debug("getByte");
        int rowTypes[] = getRowTypes(rs);                
        ByteBuffer bb = ByteBuffer.allocate(getByteSize(rowTypes));
        ByteArrayOutputStream bs = new ByteArrayOutputStream(3*1024*1024);
        
        try {
    	while(rs.next()){    		
			 for(int c=0;c<rowTypes.length;c++){
				 switch(rowTypes[c]){
				 case FLOAT:
					 Float f = rs.getFloat(c+1);			
					 bb.putFloat((rs.wasNull()?Float.NaN:f)); // Wert als 4 Byte					 
					 break;
				 case DATE:
					 Timestamp t = rs.getTimestamp(c+1);					 							 
					 bb.putInt((rs.wasNull()?NULL:(int)(t.getTime()/1000))); // Timestamp as int 4 Byte Max Date 2036 !
					 break;					 
				 case INTEGER:
					 Integer i = rs.getInt(c+1);					 							 
					 bb.putInt((rs.wasNull()?NULL:i)); 
					 break;
				 }
			 }            
			bs.write(bb.array());			
	        bb.clear();      					
		}			
    	
    	return bs.toByteArray();
    	
		       
        } finally {
        	try {
				bs.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
        }
        
	}
	
	
	private static int getByteSize(int[] rowTypes) {
		int r = 0;
		for (int i = 0; i < rowTypes.length; i++) {
			switch (rowTypes[i]) {
			case INTEGER:
			case FLOAT:
			case DATE:
				r = r + 4;
				break;								
			}
		}
		return r;
	}


	public static int[] getRowTypes(ResultSet rs) throws SQLException {
		ResultSetMetaData md = rs.getMetaData();
		int colCount = md.getColumnCount();
		int[] ret = new int[colCount];
		for (int i = 0; i < colCount; i++) {			
			ret[i] = getByteType(md.getColumnType(i + 1),md.getScale(i+1));
        }
		return ret;
        
	}


	public static int getByteType(int sqlType, int sqlScale) throws SQLException {
        switch (sqlType){
         case Types.CHAR:
         case Types.VARCHAR:
         case Types.LONGVARCHAR:
                 throw new SQLException("String format not supported.");
         case Types.DOUBLE:
         case Types.FLOAT:
         case Types.NUMERIC:
         case Types.REAL:
             // Sonderfall für Primärschlüssel und 
             // Numeric 
             if (sqlScale == 0) {
                 return INTEGER;
             } else {
                 return FLOAT;
             }
         case Types.TIMESTAMP:
         case Types.TIME:
         case Types.DATE:
                         return DATE;
         case Types.BIT: 
        	 throw new SQLException("Boolean format not supported.");
         case Types.INTEGER:
         case Types.SMALLINT:
         case Types.TINYINT:
         case Types.BIGINT:
                         return INTEGER;
         default:
             return 0;
    }
   }

}
