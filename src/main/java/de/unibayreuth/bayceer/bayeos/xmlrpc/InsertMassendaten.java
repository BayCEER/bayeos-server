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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

/*
 * InsertMassendaten.java
 *
 * Created on 8. Juli 2003, 16:04
 */

/**
 *
 * @author  oliver
 */
public class InsertMassendaten  {
    
    private int i=0;
    
    private Map insertedKeys = null;
    private PreparedStatement pst,ustmin,ustmax, ups;
    
    private Connection con = null;
    
    private static final Logger logger = Logger.getLogger(InsertMassendaten.class);
       
    
    /** Creates a new instance of InsertMassendaten */
    public InsertMassendaten(Connection con) {
        this.con = con;
        insertedKeys = new Hashtable();
    }
    
    public void upsert(Integer id, java.sql.Timestamp von, Float wert) throws SQLException, InvalidRightException {
    	if (logger.isDebugEnabled()){
    		logger.debug(id + ";" + von + ";" + wert);
    	}    	
    	if (ups == null) {
            ups = con.prepareStatement("with upsert as (update massendaten set wert=? where id=? and von=? returning *)" + 
            							"insert into massendaten (id,von,wert) select ?,?,? where not exists(select * from upsert);");                                           
        }    	    	
    	// With
    	ups.setFloat(1,wert.floatValue());                            
        ups.setInt(2,id.intValue());
        ups.setTimestamp(3,von);    
        
        // insert 
        ups.setInt(4,id.intValue());
        ups.setTimestamp(5,von);
        ups.setFloat(6,wert.floatValue());
        ups.execute();
        i++;
    	setMinMax(id,von);    	
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
        setMinMax(id,von);
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
        setMinMax(id,von);
    }
    
    
    
    private void setMinMax(Integer id, java.sql.Timestamp von) {
        ArrayList t = null;
        if (insertedKeys.containsKey(id)) {
            t = (ArrayList)insertedKeys.get(id);         
            java.sql.Timestamp minVon = (java.sql.Timestamp)t.get(0);
            if (von.before(minVon))   {
              t.set(0,von);
            }
            java.sql.Timestamp maxVon = (java.sql.Timestamp)t.get(1);
            if (von.after(maxVon))   {
              t.set(1,von);
            }            
        } else {
                t = new ArrayList(2);
                t.add(von); // min
                t.add(von); // max
                insertedKeys.put(id,t);
        }                 

    }
    
    public void updateMinMax() throws SQLException {
        if (ustmin == null) {
           ustmin = con.prepareStatement("update objekt set rec_start = ? where id = ? and (rec_start > ? or rec_start is null)");
        }
        if (ustmax == null) {
           ustmax = con.prepareStatement("update objekt set rec_end = ? where id = ? and (rec_end < ? or rec_end is null)");
        }
        
        Iterator it = insertedKeys.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry t = (Map.Entry)it.next();
            ArrayList a = (ArrayList)t.getValue();  
            
            // min
            ustmin.setTimestamp(1,(Timestamp)a.get(0));
            ustmin.setInt(2,((Integer)t.getKey()).intValue());
            ustmin.setTimestamp(3,(Timestamp)a.get(0));
            ustmin.executeUpdate();
            
            //max
            ustmax.setTimestamp(1,(Timestamp)a.get(1));
            ustmax.setInt(2,((Integer)t.getKey()).intValue());
            ustmax.setTimestamp(3,(Timestamp)a.get(1));
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
    
    public boolean isKeyInserted(Integer id) {
       return insertedKeys.containsKey(id);
    }
    
    public int keysUpdated() {
        return insertedKeys.size();
    }
    
    
}
