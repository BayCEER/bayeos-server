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
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Vector;

import org.apache.xmlrpc.OctetRpcHandler;
import org.apache.xmlrpc.OctetServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRightException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.MatrixUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.AggregateFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.StatusFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.TimeFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.formats.DateFormat;



/*
 * MatrixExport.java
 *
 * Created on 24. Juni 2004, 12:49
 */

/**
 *
 * @author  oliver
 */
public class OctetMatrixHandler extends AccessHandler implements OctetRpcHandler  {
    
    private static final Logger logger = LoggerFactory.getLogger(OctetMatrixHandler.class);
    
    final static SimpleDateFormat df = new SimpleDateFormat();
    
    private final static String C_CHAR_STREAM = "char_stream";
    private final static String C_OBJ_STREAM = "obj_stream";
    
    /** Creates a new instance of MatrixExport */
    public OctetMatrixHandler() {
        df.applyPattern("yyyy-MM-dd HH:mm:ss");
    }
    
    public void execute(OutputStream out, String method, java.util.Vector params) throws OctetServerException {       
       try {
    	   logger.debug("Exceute method" + method);
     Vector ids = (Vector)params.elementAt(0);
     Iterator it = ids.iterator();
     while (it.hasNext()){
          if (!checkRead((Integer)it.next())) {
             throw new OctetServerException(1,"Objekt does not exist.");
           }    
     }          
     if (method.equals("getMatrixOrg")) {
    	 
         TimeFilter t = new TimeFilter((Vector)params.elementAt(1));
         StatusFilter s =  new StatusFilter((Vector)params.elementAt(2));
         Boolean withStatus = (Boolean)params.elementAt(3);
         String format = (String)params.elementAt(4);
         getMatrixOrg(out,ids,t,s,withStatus,format);
     } else if (method.equals("getMatrixAggr")) {
         TimeFilter t = new TimeFilter((Vector)params.elementAt(1));
         AggregateFilter a =  new AggregateFilter((Vector)params.elementAt(2));
         Boolean withCounts = (Boolean)params.elementAt(3);
         String format = (String)params.elementAt(4);
         getMatrixAggr(out,ids,t,a,withCounts,format);
     } else {
    	 throw new OctetServerException(0, "Method: " + method + " not found.");
     }
     } catch (InvalidRightException e){
         throw new OctetServerException(1,e.getMessage());
     }
    }
    
    
    private void writeObjectStream(ObjectOutputStream dout, String sql) throws OctetServerException {
        Connection con = null;
        try {
        con = ConnectionPool.getPooledConnection();
        ResultSet rs = SelectUtils.getResultSet(con, sql);    
        int c = rs.getMetaData().getColumnCount();
        while(rs.next()){  
             Timestamp t = rs.getTimestamp(1);
             if (rs.wasNull()) continue;
             dout.writeInt((int)(t.getTime()/1000L));
              for(int i=2;i<=c;i++){
                  dout.writeObject(rs.getObject(i));
              }
        }
        
        rs.close();
        } catch (SQLException e){
             throw new OctetServerException(0,e.getMessage());
        } catch (IOException i){
            throw new OctetServerException(0,i.getMessage());
        } finally {
            try { con.close();} catch (SQLException e){logger.error(e.getMessage());}
        }

    }
    
    private void writeCharacterStream(PrintWriter p, String sql, String delimeter) throws OctetServerException {
        Connection con = null;
        try {
        con = ConnectionPool.getPooledConnection();
        ResultSet rs = SelectUtils.getResultSet(con, sql);
        int c = rs.getMetaData().getColumnCount();
        while(rs.next()){  
             java.sql.Timestamp t = rs.getTimestamp(1);
             if (rs.wasNull()) continue;             
              p.print(DateFormat.exportDateFormat.format(t));
              for(int i=2;i<=c;i++){
                  p.print(delimeter);
                  Object o = rs.getObject(i);
                  p.print((o==null)?"":o.toString());
              }
              p.println();
        }
        p.flush();
        rs.close();
        } catch (SQLException e){
             throw new OctetServerException(0, e.getMessage());
        } finally {
            try { con.close();} catch (SQLException e){logger.error(e.getMessage());}
        }
    }

     private void writeHeaderAggr(PrintWriter p, Vector ids, TimeFilter tFilter, AggregateFilter aFilter,Boolean withCounts){
         p.print("# Exported at: "); p.println(DateFormat.exportDateFormat.format(new java.util.Date()));               
         p.print("# Time Filter: "); p.println(tFilter.toString());
         p.print("# Aggregation: "); p.println(aFilter.toString());         
         p.println("# Column [0]: Measurement Date , Timezone GMT+1");         
         Iterator it = ids.iterator();
         int c = 1;
         while(it.hasNext()){
             p.print("# Column ["); p.print(c++); p.print("]: ");
             try {
             p.println(SelectUtils.getString(getPooledConnection(),"select get_measurement_description(" + ((Integer)it.next()).toString() + ")"));
             } catch (SQLException e){
                 logger.error(e.getMessage());
             }
             if (withCounts.booleanValue()) {
                 p.print("# Column ["); p.print(c++); p.println("]: Counts");
             }
         }
         p.flush();
     }
     
     private void writeHeaderStatus(PrintWriter p, Vector ids, TimeFilter tFilter, StatusFilter sFilter,Boolean withStatus){
         p.print("# Exported at: "); p.println(DateFormat.exportDateFormat.format(new java.util.Date()));               
         p.print("# Time Filter: "); p.println(tFilter.toString());
         p.print("# Status Filter: "); p.println(sFilter.toString());
         p.println("# Column [0]: Measurement Date (Timezone GMT+1)");         
         Iterator it = ids.iterator();
         int c = 1;
         while(it.hasNext()){
             p.print("# Column ["); p.print(c++); p.print("]: ");
             try {
                 String sql = "select get_measurement_description(" + ((Integer)it.next()).toString() + ")";
                 p.println(SelectUtils.getString(ConnectionPool.getPooledConnection(),sql));
             } catch (SQLException e){
                 logger.error(e.getMessage());
             }
             if (withStatus.booleanValue()) {
                 p.print("# Column ["); p.print(c++); p.print("]: Status of Column ");p.println(c-2);
             }
         }
         p.flush();
     }
     
     
     
               
               
    
     private void getMatrixAggr(OutputStream out, Vector ids, TimeFilter tFilter, AggregateFilter aFilter, Boolean withCounts, String format) throws OctetServerException {
        try {
            String sql = MatrixUtils.getAggrQueryCom(ids, tFilter, aFilter, withCounts);
            if (format.equals(C_CHAR_STREAM)){
               PrintWriter p = new PrintWriter(new BufferedOutputStream(out));
               writeHeaderAggr(p,ids,tFilter,aFilter,withCounts);
               writeCharacterStream(p,sql,";");
               p.flush(); p.close();               
            } else if (format.equals(C_OBJ_STREAM)){
               ObjectOutputStream dout = new ObjectOutputStream(new BufferedOutputStream(out));        
               writeObjectStream(dout,sql);
               dout.flush();dout.close();
            }
         
        } catch (SQLException e){
             logger.error(e.getMessage());
             throw new OctetServerException(0,e.getMessage());
        }  catch (IOException i){
             logger.error(i.getMessage());
             throw new OctetServerException(0,i.getMessage());
        }
     }
     
     private void getMatrixOrg(OutputStream out, Vector ids, TimeFilter tFilter, StatusFilter sFilter, Boolean withStatus, String format) throws OctetServerException {
         String sql = MatrixUtils.getOrgQueryCom(ids, tFilter, sFilter, withStatus);
         try {
         if (format.equals(C_CHAR_STREAM)){
               PrintWriter p = new PrintWriter(new BufferedOutputStream(out));
               writeHeaderStatus(p, ids, tFilter, sFilter, withStatus);
               writeCharacterStream(p,sql,";");
               p.flush(); p.close();               
         } else if (format.equals(C_OBJ_STREAM)){
               ObjectOutputStream dout = new ObjectOutputStream(new BufferedOutputStream(out));        
               writeObjectStream(dout,sql);
               dout.flush();dout.close();
         }    
         } catch (IOException i){
             logger.error(i.getMessage());
             throw new OctetServerException(0,i.getMessage());
         }
    }
    
         
     
     
    
    
     
    
     
    
    
    
         
        

}
