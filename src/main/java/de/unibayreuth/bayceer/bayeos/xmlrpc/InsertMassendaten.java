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
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.Map;
import java.util.TimeZone;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * InsertMassendaten.java
 *
 *  
 */

/**
 *
 * @author  oliver
 */
public class InsertMassendaten  {
    
    private int i=0;
    
    private Map<Integer,Interval> intervalIndex; 
 
    private PreparedStatement pst,ustmin,ustmax, ups;
    
    private Connection con = null;

	private Integer userId;
    
    private static final Logger logger = LoggerFactory.getLogger(InsertMassendaten.class);
    
    class Interval {
    	public java.sql.Timestamp min;
    	public java.sql.Timestamp max;
    	
    	public Interval(java.sql.Timestamp min, java.sql.Timestamp max) {
    		this.min = min; this.max = max;    		
		}
    	
    	public Interval(java.sql.Timestamp value){
    		this.min = value; this.max = value;
    	}    	    	    
    	
    	void expand(java.sql.Timestamp value){
    		if (min == null || value.before(min)) min = value;
    		if (max == null || value.after(max)) max = value;    		
    	}
    }
       
    
    /** Creates a new instance of InsertMassendaten */
    public InsertMassendaten(Connection con, Integer userId) {
        this.con = con;
        this.userId = userId;
        intervalIndex = new Hashtable<>(10);
    }
    
    
    private boolean isWriteable(Integer id) throws SQLException {    	
    		PreparedStatement pst = con.prepareStatement("select check_write(?,?)");
    		pst.setInt(1, id.intValue());
        	pst.setInt(2, this.userId.intValue());
        	ResultSet rs = pst.executeQuery();        	
        	if (rs.next()){    		
        		return rs.getBoolean(1);
        	} else {
        		return false;
        	}         	        	     		
    }
    
    
    
    public void insertByteArray(byte[] payload, boolean overwrite) throws SQLException, IOException, InvalidRightException {
    	    			
		SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS+00");
		dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC")); 	
		
		Statement st = con.createStatement();		
		st.executeUpdate("create temp table tmp_massendaten (n serial primary key, id int, von timestamp with time zone,wert real)");
		
		CopyManager cm = ((PGConnection)con.unwrap(PGConnection.class)).getCopyAPI();
		CopyIn cin = cm.copyIn("COPY tmp_massendaten (id,von,wert) FROM STDIN WITH CSV");
		
		
		try (DataInputStream di = new DataInputStream(new ByteArrayInputStream(payload))){
			while (di.available() > 0) {
				int id = di.readInt();
				Timestamp von = new Timestamp(di.readLong());
				Float wert = di.readFloat();								
				if (!wert.isNaN()){
					StringBuffer sb = new StringBuffer();					
					sb.append(id).append(",").append(dateFormatter.format(von)).append(",").append(wert).append("\n");
					byte[] b = sb.toString().getBytes("UTF-8");
					cin.writeToCopy(b,0,b.length);
					setInterval(id, von);
				}
			}
		}
		long r = cin.endCopy();
		logger.debug(r + " records received.");
		
		
		// Check Rights		
		for(Integer id:intervalIndex.keySet()){
			if (!isWriteable(id)){
				st.executeUpdate("drop table if exists tmp_massendaten");		
				throw new InvalidRightException(1, "Missing rights to insert data");
			};	
		}
				
		// Uses on conflict clause ! Requires Postgresql >= 9.5.  
		if (overwrite){			
			 // on conflict do update
			// All inserts are ordered desc by ingestion to keep the latest record 
			r = st.executeUpdate("insert into massendaten (id,von,wert) select distinct on (id,von) id,von,wert from tmp_massendaten ORDER BY id,von,n desc ON CONFLICT (id,von) DO UPDATE SET id = EXCLUDED.ID, von = EXCLUDED.VON, wert = EXCLUDED.WERT");																					
		} else {			
			// on conflict do nothing
			r = st.executeUpdate("insert into massendaten (id,von,wert) select id,von,wert from tmp_massendaten order by n asc ON CONFLICT (id,von) DO NOTHING");		
		}
				
		logger.info(r + " records imported.");
												
		st.executeUpdate("drop table if exists tmp_massendaten");		
    	
    }
    
    
    
    @SuppressWarnings("unused")
	private void printTable(String table) {
    	logger.debug("Table:" + table);
    	Statement st;
		try {
			st = con.createStatement();
			ResultSet rs = st.executeQuery("select * from " + table);
			ResultSetMetaData meta = rs.getMetaData();						
			while(rs.next()){
				StringBuffer n = new StringBuffer();
				for(int i=1;i<=meta.getColumnCount();i++){
					n.append(" " + rs.getObject(i).toString());					
				}
				logger.debug("Row:" + n.toString());
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
    	
    	
    	
		
	}


	public void upsert(Integer id, java.sql.Timestamp von, Short status, Float wert) throws SQLException, InvalidRightException {
    	if (logger.isDebugEnabled()){
    		logger.debug(id + ";" + von + ";" + wert + ";" + status);
    	}    	
    	if (ups == null) {
            ups = con.prepareStatement("with upsert as (update massendaten set wert=?,status=? where id=? and von=? returning *)" + 
            							"insert into massendaten (id,von,wert,status) select ?,?,?,? where not exists(select * from upsert);");                                           
        }    	    	
    	// With
    	ups.setFloat(1,wert.floatValue());
    	ups.setShort(2, status);
        ups.setInt(3,id.intValue());
        ups.setTimestamp(4,von);    
        
        // insert 
        ups.setInt(5,id.intValue());
        ups.setTimestamp(6,von);
        ups.setFloat(7,wert.floatValue());
        ups.setShort(8, status);
        ups.execute();
        i++;
    	setInterval(id,von);    	
    }
    
           
    public void insert(Integer id, java.sql.Timestamp von, Short status,  Float wert) throws SQLException, InvalidRightException {
    	if (logger.isDebugEnabled()){
    		logger.debug(id + ";" + von + ";" + status + ";" + wert);
    	}
        if (pst == null) {
         pst = con.prepareStatement("insert into massendaten (id,von,status,wert) values (?,?,?,?)");
        }        
        pst.setInt(1,id.intValue());
        pst.setTimestamp(2,von);  
        
        if (status == null) {
            pst.setNull(3,java.sql.Types.SMALLINT);
        } else {
            pst.setShort(3,status.shortValue());
        }
        pst.setFloat(4,wert.floatValue());
        pst.executeUpdate();
        i++;
        setInterval(id,von);
    }
    
    public void insert(Integer id, java.sql.Timestamp von, Float wert) throws SQLException, InvalidRightException {
        if (pst == null) {
         pst = con.prepareStatement("insert into massendaten (id,von,wert) values (?,?,?)");
        }        
        pst.setInt(1,id.intValue());
        pst.setTimestamp(2,von);  
        pst.setFloat(3,wert.floatValue());
        pst.executeUpdate();
        i++;
        setInterval(id,von);
    }
    
        
    private void setInterval(Integer id, java.sql.Timestamp von) { 
        if (intervalIndex.containsKey(id)) {
            intervalIndex.get(id).expand(von);                        
        } else {
            intervalIndex.put(id,new Interval(von));
        }                 
    }
    
    public void updateObjektInterval() throws SQLException {
        if (ustmin == null) {
           ustmin = con.prepareStatement("update objekt set rec_start = ? where id = ? and (rec_start > ? or rec_start is null)");
        }
        if (ustmax == null) {
           ustmax = con.prepareStatement("update objekt set rec_end = ? where id = ? and (rec_end < ? or rec_end is null)");
        }
                
        for(Map.Entry<Integer, Interval> entry:intervalIndex.entrySet()){        	                    
            // min
            ustmin.setTimestamp(1,entry.getValue().min);
            ustmin.setInt(2,entry.getKey());
            ustmin.setTimestamp(3,entry.getValue().min);
            ustmin.executeUpdate();
            
            //max
            ustmax.setTimestamp(1,entry.getValue().max);
            ustmax.setInt(2,entry.getKey());
            ustmax.setTimestamp(3,entry.getValue().max);
            ustmax.executeUpdate();
        }
        ustmin.close();  ustmax.close();
    }
    
    public int getRowsInserted() {
        return i;
    }
    
    public void setRowsInserted(int i){
        this.i = i;
    }
    
   
    
    
}
