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
package de.unibayreuth.bayceer.bayeos.xmlrpc.handler;
/*
 * LaborTableHandler.java
 *
 * Created on 28. August 2002, 13:47
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.InsertLabordaten;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRightException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRowStringException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.StatusFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.TimeFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.formats.DateFormat;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ILaborTableHandler;

/**
 *
 * @author  oliver
 */
public class LaborTableHandler extends AccessHandler implements ILaborTableHandler {
    
    final static Logger logger = LoggerFactory.getLogger(LaborTableHandler.class); 
    
       
    
   public Boolean updateRow(Integer id,java.util.Date oldbis, Vector values) throws XmlRpcException  {
        PreparedStatement pst = null;
        Connection con = null;
        if (!checkWrite(id)) {
            throw new XmlRpcException(1,"Missing rights on Objekt " + id + ".");
          }
        
        try {
        // create prepared statement 
        if (logger.isDebugEnabled()) { 
         logger.debug("updateRow(" + id + "," + oldbis +  ")" );
        }
        con = getPooledConnection();
        String sql = "update labordaten set status = ? , von = ?, bis = ?, wert = ?, labornummer = ?, genauigkeit = ?, " +
        "bestimmungsgrenze = ?, bemerkung = ? where bis = ? and id = ?";
        pst = con.prepareStatement(sql);
        
        Integer status = (Integer)values.elementAt(0);
        java.util.Date von = (java.util.Date)values.elementAt(1);
        java.util.Date bis = (java.util.Date)values.elementAt(2);
        Double wert = (Double)values.elementAt(3);
        String labnr = (String)values.elementAt(4);
        Double genau = (Double)values.elementAt(5);
        Double nachw = (Double)values.elementAt(6);
        String bem = (String)values.elementAt(7);
        
        
        pst.setShort(1,status.shortValue());
        
        if (von == null){
            pst.setNull(2,java.sql.Types.TIMESTAMP);
        } else {
            pst.setTimestamp(2,new java.sql.Timestamp(von.getTime()));
        }
        
        pst.setTimestamp(3,new java.sql.Timestamp(bis.getTime()));
        
        if (wert == null){
            pst.setNull(4,java.sql.Types.FLOAT);
        } else {        
            pst.setFloat(4,wert.floatValue());
        }
        
        pst.setString(5,labnr);
        
        if (genau == null){
            pst.setNull(6,java.sql.Types.FLOAT);
        } else {        
            pst.setFloat(6,genau.floatValue());
        }
        
        if (nachw == null){
            pst.setNull(7,java.sql.Types.FLOAT);
        } else {        
            pst.setFloat(7,nachw.floatValue());
        }
        pst.setString(8,bem);
        pst.setTimestamp(9,new java.sql.Timestamp(oldbis.getTime()));
        pst.setInt(10,id.intValue());
        pst.executeUpdate();    
        return true;
        } catch (SQLException s) {
            logger.error(s.getMessage());
            throw new XmlRpcException(0,s.getMessage());
        } finally {
            try {con.close();pst.close();} catch (SQLException dummy){logger.error(dummy.getMessage());};
        }
    }

    
    public Boolean removeRows(Integer id, Vector bisDates) throws  XmlRpcException  {
        PreparedStatement pst = null;
        Connection con = null;
        try {
        if (logger.isDebugEnabled()) {  
         logger.debug("deleteRows(" + id + " )");
        }
        
        if (!checkWrite(id)) {
            throw new XmlRpcException(1,"Missing rights on Objekt " + id + ".");
          }
        con = getPooledConnection();
        pst = con.prepareStatement("delete from labordaten where bis = ? and id = ?");
        pst.setInt(2,id.intValue());
        Iterator it = bisDates.iterator();
        con.setAutoCommit(false);
         while (it.hasNext()){
            java.util.Date bisDate = (java.util.Date)it.next();
            java.sql.Timestamp sqlbis =  new java.sql.Timestamp(bisDate.getTime());
            pst.setTimestamp(1,sqlbis);
            pst.executeUpdate();    
        }
        con.commit();
        
        pst = con.prepareStatement("select update_labordaten_min_max(?)");
        pst.setInt(1,id.intValue());
        pst.executeUpdate();
        logger.debug("Border updated.");
        
        return true;
        } catch (SQLException s) {
            logger.error(s.getMessage());
            try {con.rollback(); } catch (SQLException dummy) {logger.error(s.getMessage());};
            throw new XmlRpcException(0,s.getMessage());
        } finally {
            try {con.close();pst.close();} catch (SQLException e){logger.error(e.getMessage());};
        }
    }
    
    public Vector getRows(Integer primaryKey, Vector timeFilter, Vector statusFilter) throws XmlRpcException {
        PreparedStatement pst = null;
        Connection con = null;
        ResultSet rs = null;
        Vector vReturn = null;        
                
        try {        
        if (logger.isDebugEnabled()) {  
         logger.debug("getLaborDaten(" + primaryKey +")" );
        }
        
        if (!checkRead(primaryKey)) {
            throw new XmlRpcException(1,"Objekt " + primaryKey + " does not exist.");
          }
        
        TimeFilter tFilter = new TimeFilter(timeFilter);
        StatusFilter sFilter = new StatusFilter(statusFilter);
        con = getPooledConnection();
        String query = "select id, status , von, bis, wert, labornummer, genauigkeit, bestimmungsgrenze, bemerkung " + 
        " from labordaten where id = ? and bis > ? and bis <= ? and status in " + 
        SelectUtils.getInInteger(sFilter.getIds()) + " order by bis asc";
        if (logger.isDebugEnabled()) {  
         logger.debug("Query:" + query);
        }
        pst = con.prepareStatement(query,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
        pst.setInt(1, primaryKey.intValue());
        
        // Umwandeln in java.sql.Date und binden 
        java.sql.Date sqlVonDate =  new java.sql.Date(tFilter.getVon().getTime());
        pst.setDate(2,sqlVonDate);
        
        java.sql.Date sqlBisDate =  new java.sql.Date(tFilter.getBis().getTime());
        pst.setDate(3,sqlBisDate);

        rs = pst.executeQuery();
        vReturn = new Vector();
        vReturn.add(XmlRpcUtils.getMetaData(rs));
        vReturn.add(XmlRpcUtils.resultSetToVector(rs));
        } catch(SQLException e){
    		logger.error(e.getMessage());
    		throw new XmlRpcException(1, e.getMessage());
    	} 
        finally {
            try {
				rs.close();con.close();pst.close();
			} catch (SQLException e) {
	    		logger.error(e.getMessage());
			}
        }
        return vReturn;
   }
    
       
     public Boolean updateRows(Integer id, Vector bisDates, Integer status) throws XmlRpcException {
        // Update Batch testen !
        PreparedStatement pst = null;
        Connection con = null;
        try {
        // create prepared statement 
        if (logger.isDebugEnabled()) {  
          logger.debug("updateRows(" + id + "," + status + ")" );
        }
        if (!checkWrite(id)) {
            throw new XmlRpcException(1,"Missing rights on Objekt " + id + ".");
        }
        
        con = getPooledConnection();
        String sql = "update labordaten set status = " + status + "  where bis = ? and id = " +  id;
        pst = con.prepareStatement(sql);
        Iterator it = bisDates.iterator();
        con.setAutoCommit(false);
         while (it.hasNext()){
            java.util.Date bisDate = (java.util.Date)it.next();
            java.sql.Timestamp sqlbis =  new java.sql.Timestamp(bisDate.getTime());
            pst.setTimestamp(1,sqlbis);
            pst.executeUpdate();    
        }
        con.commit();
        return true;
        } catch (SQLException e) {
            logger.error(e.getMessage());
            try {con.rollback(); } catch (SQLException s) {
            	logger.error(s.getMessage());
            };
            throw new XmlRpcException(0, e.getMessage());
            
        } finally {
            try {con.close();pst.close();} catch (SQLException e){
            	logger.error(e.getMessage());
            };
        }
    }
     
     
    public Boolean addRow(Integer id, Vector values) throws XmlRpcException {
        Connection con = null;
        logger.debug("addRow");
        
        if (!checkWrite(id)) {
            throw new XmlRpcException(1,"Missing rights on Objekt " + id + ".");
          }
        
        try {
            con = getPooledConnection();
            con.setAutoCommit(false);
            InsertLabordaten inserter = new InsertLabordaten(con);
            Integer status = (Integer)values.elementAt(0);
            java.util.Date von = (java.util.Date)values.elementAt(1);
            java.util.Date bis = (java.util.Date)values.elementAt(2);
            Double wert = (Double)values.elementAt(3);
            String labnr = (String)values.elementAt(4);
            Double genau = (Double)values.elementAt(5);
            Double nachw = (Double)values.elementAt(6);
            String bem = (String)values.elementAt(7);
              
        
            inserter.insert(id,new Short(status.shortValue()),(von==null)?null:new java.sql.Timestamp(von.getTime()), 
            new java.sql.Timestamp(bis.getTime()),(wert==null)?null:new Float(wert.floatValue()),labnr, 
            (genau==null)?null:new Float(genau.floatValue()), 
            (nachw==null)?null:new Float(nachw.floatValue()), bem);
            inserter.updateMinMax();
            con.commit();
            
        } catch (SQLException s) {
            logger.error(s.getMessage());
            try {con.rollback(); } catch (SQLException dummy) {logger.error(dummy.getMessage());};
            throw new XmlRpcException(0,s.getMessage());
        } finally {
            try {con.close();} catch (SQLException dummy){logger.error(dummy.getMessage());};
        }

       return Boolean.TRUE;
    }
     
     
     public Boolean importFlat(Integer Id, Vector rows, boolean shortCols) throws XmlRpcException {
        Connection con = null;
        ArrayList cols = null;
        try {
           logger.info("Importing " + rows.size() +  " rows with id:" + Id + " into table labordaten.");   
           con = getPooledConnection();
           con.setAutoCommit(false);
           InsertLabordaten inserter = new InsertLabordaten(con);
           try { 
             Iterator it = rows.iterator();             
             while (it.hasNext()) {
                 if (shortCols) {             
                   cols = getShortCols((String)it.next());
                   inserter.insert(Id,   // id
                   (Timestamp)cols.get(0), // bis
                   (Float)cols.get(1)); // wert
                 } else {
                   cols = getAllCols((String)it.next());
                   inserter.insert(Id,   // id
                   (Short)cols.get(1),  //status
                   (Timestamp)cols.get(2), // von
                   (Timestamp)cols.get(3), // bis
                   (Float)cols.get(4), // wert
                   (String)cols.get(5), // lbnr
                   (Float)cols.get(6), // genau
                   (Float)cols.get(7), // nach
                   (String)cols.get(8)); // bem
                   
                 }
             }
             logger.info(inserter.rowsInserted() + " rows imported.");
             inserter.updateMinMax();
             logger.debug(inserter.keysUpdated() + " meta information updated.");
             
           } catch (SQLException e) {
               con.rollback();
               String msg = e.getMessage() + " Row " + cols + ". Transaction rolled back.";
               logger.error(msg);
               throw new XmlRpcException(0, msg);
           } catch (InvalidRowStringException ir) {
               con.rollback();
               String msg = ir.getMessage() + " Row " + cols + ". Transaction rolled back.";
               logger.error(msg);
               throw new XmlRpcException(0, msg);
           } catch (InvalidRightException a) {
               con.rollback();
               String msg = a.getMessage() + " Row:" + cols +". Transaction rolled back.";
               logger.error(msg);
               throw new XmlRpcException(0,msg);
           } finally {            
               con.commit();
               con.setAutoCommit(true);
               con.close();
           }
           return Boolean.TRUE;
        } catch (SQLException x) {
            String msg = x.getMessage();
            try {con.close();} catch(SQLException z) {;}
            throw new XmlRpcException(0,msg);
        }
        
    }
     
     
    private ArrayList getAllCols(String row) throws InvalidRowStringException {
      try {
       ArrayList ret = new ArrayList(8);        
       StringTokenizer st = new StringTokenizer(row,";");
       if (st.countTokens() != 8) {
         throw new InvalidRowStringException("Illegal tokens in string " + row,null);
       }      
       ret.add(Short.valueOf(st.nextToken()));
       ret.add(getTimestamp(st.nextToken()));       
       ret.add(getTimestamp(st.nextToken())); 
       ret.add(new Float(st.nextToken())); 
       ret.add(String.valueOf(st.nextToken())); 
       ret.add(new Float(st.nextToken())); 
       ret.add(new Float(st.nextToken()));
       ret.add(String.valueOf(st.nextToken()));
       return ret;
     } catch (ParseException p) {
         throw new InvalidRowStringException(p.getMessage() + " Row: " + row,p);
     } catch (NumberFormatException f) {
         throw new InvalidRowStringException(f.getMessage() + " Row: " + row,f);
     }
    }
    
    private ArrayList getShortCols(String row) throws InvalidRowStringException {
      try {
       ArrayList ret = new ArrayList(2);        
       StringTokenizer st = new StringTokenizer(row,";");
       if (st.countTokens() != 2) {
         throw new InvalidRowStringException("Illegal tokens in string " + row,null);
       }      
       ret.add(getTimestamp(st.nextToken())); 
       ret.add(new Float(st.nextToken())); 
       return ret;
     } catch (ParseException p) {
         throw new InvalidRowStringException(p.getMessage() + " Row: " + row,p);
     } catch (NumberFormatException f) {
         throw new InvalidRowStringException(f.getMessage() + " Row: " + row,f);
     }
    }
    
    
    private static Timestamp getTimestamp(String value) throws ParseException {
        return new Timestamp(DateFormat.defaultDateFormat.parse(value).getTime());
    }


	public Boolean removeAllRows(Integer id) throws XmlRpcException {
		 PreparedStatement pst = null;
	        Connection con = null;
	        try {
	        if (logger.isDebugEnabled()) {  
	         logger.debug("removeAllRows(" + id + " )");
	        }	        
	        if (!checkWrite(id)) {
	            throw new XmlRpcException(1,"Missing rights on Objekt " + id + ".");
	          }
	        con = getPooledConnection();
	        pst = con.prepareStatement("delete from labordaten where id = ?");
	        pst.setInt(1,id.intValue());
	        int r = pst.executeUpdate();    
	        logger.debug(r + " Rows deleted.");
	        
	        pst = con.prepareStatement("update objekt set rec_start = null, rec_end = null where id = ?");
	        pst.setInt(1,id.intValue());
	        pst.executeUpdate();
	        logger.debug("Border updated.");
	        
	        
	        return true;
	        } catch (SQLException s) {
	            logger.error(s.getMessage());	           
	            throw new XmlRpcException(0,s.getMessage());
	        } finally {
	            try {con.close();pst.close();} catch (SQLException e){logger.error(e.getMessage());};
	        }
	}
       
    
}

