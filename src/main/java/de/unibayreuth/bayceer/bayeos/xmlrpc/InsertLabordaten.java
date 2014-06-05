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
import java.sql.Types;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;


/*
 * InsertMassendaten.java
 *
 * Created on 8. Juli 2003, 16:04
 */

/**
 *
 * @author  oliver
 */
public class InsertLabordaten  {
    
    private int i=0;
    
    private Map insertedKeys = null;
    private PreparedStatement pst,ust;
    private Connection con = null;   
    
    
    /** Creates a new instance of InsertMassendaten */
    public InsertLabordaten(Connection con) {
        this.con = con;
        insertedKeys = new Hashtable();
    }
    
    public void insert(Integer id, Short status, Timestamp von, Timestamp bis, Float wert, 
    String labnr, Float genau, Float nachw, String bem) throws SQLException, InvalidRightException {
       
        if (pst == null) {
         pst = con.prepareStatement("insert into labordaten (id,status,von,bis,wert,labornummer," +
         "genauigkeit, bestimmungsgrenze, bemerkung) values (?,?,?,?,?,?,?,?,?)");
        }                
        
        pst.setInt(1,id.intValue()); // not null
        if (status == null) { 
            pst.setNull(2,Types.SMALLINT);
        } else {
            pst.setShort(2, status.shortValue());
        }        
        if (von == null) { 
            pst.setNull(3,Types.TIMESTAMP);
        } else {
            pst.setTimestamp(3,von);
        }        
        pst.setTimestamp(4,bis); // not null
        
        if (wert == null) { 
            pst.setNull(5,Types.FLOAT);
        } else {
            pst.setFloat(5,wert.floatValue());
        }        
        
        if (labnr == null) { 
            pst.setNull(6,Types.VARCHAR);
        } else {
            pst.setString(6,labnr);
        }
        if (genau == null) { 
            pst.setNull(7,Types.FLOAT);
        } else {
            pst.setFloat(7,genau.floatValue());
        }
        if (nachw == null) { 
            pst.setNull(8,Types.FLOAT);
        } else {
            pst.setFloat(8,nachw.floatValue());
        }        
        if (bem == null) { 
            pst.setNull(9,Types.VARCHAR);
        } else {
            pst.setString(9,bem);
        }
        pst.executeUpdate();
        i++;
        setMinMax(id,von,bis);
    }

    public void insert (Integer id, Short status, Timestamp bis, Float wert) throws SQLException, InvalidRightException{
        insert(id,status,null,bis,wert,null,null,null,null);        
    }
    
    public void insert (Integer id, Timestamp bis, Float wert) throws SQLException, InvalidRightException{
        if (pst == null) {
         pst = con.prepareStatement("insert into labordaten (id,bis,wert) values (?,?,?)");
        }                
        pst.setInt(1,id.intValue()); // not null
        pst.setTimestamp(2,bis); // not null
        pst.setFloat(3, wert.floatValue()); // not null        
        pst.executeUpdate();
        i++;
        setMinMax(id,bis,bis);
    }


    
    private void setMinMax(Integer id, java.sql.Timestamp von, java.sql.Timestamp bis) {
        ArrayList t = null;
        if (insertedKeys.containsKey(id)) {
            t = (ArrayList)insertedKeys.get(id);         
            Timestamp tmin = (Timestamp)t.get(0);
            Timestamp tmax = (Timestamp)t.get(1);
            Timestamp tx = min(von,bis);            
            if (tmin == null || tx.before(tmin))   {
              t.set(0,tx);
            }
            tx = max(von,bis);
            if ((tmax == null) || tx.after(tmax))   {
              t.set(1,tx);
            }            
        } else {
                t = new ArrayList(2);
                t.add(von); // min
                t.add(bis); // max
                insertedKeys.put(id,t);
        }                 

    }
    
    public void updateMinMax() throws SQLException {
        if (ust == null) {
           ust = con.prepareStatement("update objekt set rec_start = ?, rec_end = ? where id = ?");
        }
        Iterator it = insertedKeys.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry t = (Map.Entry)it.next();
            ArrayList a = (ArrayList)t.getValue();
            // von nullable !
            if (a.get(0) == null) {
              ust.setNull(1,Types.TIMESTAMP);    
            } else {
              ust.setTimestamp(1,(Timestamp)a.get(0));    
            }
            // bis not null
            ust.setTimestamp(2,(Timestamp)a.get(1));
            ust.setInt(3,((Integer)t.getKey()).intValue());
            ust.executeUpdate();
        }
        ust.close();  
    }
    
    public int rowsInserted() {
        return i;
    }
    
    public boolean isKeyInserted(Integer id) {
       return insertedKeys.containsKey(id);
    }
    
    public int keysUpdated() {
        return insertedKeys.size();
    }   
    
    private Timestamp min(Timestamp a, Timestamp b) {        
        return ( a.before(b) ? a:b);
        
    }
    private Timestamp max(Timestamp a, Timestamp b) {        
        return (a.after(b) ? a:b);
    }
    
    
}
