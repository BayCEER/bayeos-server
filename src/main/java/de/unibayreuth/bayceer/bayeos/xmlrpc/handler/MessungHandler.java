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
 * MessungHandler.java
 *
 * Created on 28. August 2002, 13:47
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IMessungHandler;

/**
 *
 * @author  oliver
 */
public class MessungHandler extends AccessHandler implements IMessungHandler {
    
    final static Logger logger = Logger.getLogger(MessungHandler.class.getName()); 
    
   
   public Integer getResolution(Integer Id) throws XmlRpcException {
	   try {
        if (logger.isDebugEnabled()) {  
         logger.debug("getResolution(" + Id + ")");
        }
        String sql = "select aufloesung from messungen where id = " + Id ;
        return SelectUtils.getInt(getPooledConnection(), sql);
   } catch (SQLException e) {
       logger.error(e.getMessage());
       throw new XmlRpcException(1,e.getMessage());
  }
  }
   
   
   
   
   
  
   
   public Vector getNumberAxisLabels(Vector ids) throws XmlRpcException {	   
       if (logger.isDebugEnabled()) {  
         logger.debug("getNumberAxisLabels(" + ids + ")");         
        }
       Vector ret = new Vector(ids.size());
       Iterator it = ids.iterator();
       while(it.hasNext()){
           ret.add(getNumberAxisLabel((Integer)it.next()));
       }
       return ret;
   }
   
   
   public String getNumberAxisLabel(Integer Id) throws XmlRpcException {
	   try {
        if (logger.isDebugEnabled()) {  
         logger.debug("getNumberAxisLabel(" + Id + ")");
        }
        StringBuffer sql = new StringBuffer("select get_numberaxislabel(");
        sql.append(Id);
        sql.append(")");        
        return SelectUtils.getString(getPooledConnection(), sql.toString());
   } catch (SQLException e) {
       logger.error(e.getMessage());
       throw new XmlRpcException(1,e.getMessage());
  }
   }







public Integer getMinResolution(Vector Ids) throws XmlRpcException {
	try {
	if (logger.isDebugEnabled()) {  
        logger.debug("getMinResolution(" + Ids + ")");
       }
       String sql = "select min(aufloesung) from messungen where id in " + SelectUtils.getInInteger(Ids) ;
       return SelectUtils.getInt(getPooledConnection(), sql);
	} catch (SQLException e) {
	       logger.error(e.getMessage());
	       throw new XmlRpcException(1,e.getMessage());
	  }
		
	
}







public String getTimeZone(Integer Id) throws XmlRpcException {
	try {
		if (logger.isDebugEnabled()) {  
	        logger.debug("getTimeZone(" + Id + ")");
	       }
	       String sql = "select tz.name from messungen m, timezone tz where m.id = " + Id + " and m.fk_timezone_id = tz.id" ;
	       return SelectUtils.getString(getPooledConnection(), sql);
		} catch (SQLException e) {
		       logger.error(e.getMessage());
		       throw new XmlRpcException(1,e.getMessage());
		  }
}







@Override
public Boolean refreshRecInterval(Integer id) throws XmlRpcException {
	
	if (id == null){
		throw new XmlRpcException(0, "Invalid argument");
	}
	
	if (logger.isDebugEnabled()){
		logger.debug("refreshRecInterval(" + id + ")");		
	}
		
	Connection con = null;		
	try {
		con = ConnectionPool.getPooledConnection();
		PreparedStatement pstm = con.prepareStatement("WITH rec AS (select min(von) as min, max(von) as max from massendaten where id = ? union " + 
	     "select CASE WHEN min(von) <  min(bis) THEN min(von) ELSE min(bis) END as min," +
	     "CASE WHEN max(von) > max(bis) THEN max(von) ELSE max(bis) END as max from labordaten where id = ?) " +   
	     "update objekt set rec_start = rec.min, rec_end = rec.max from rec where id = ?");		
		pstm.setInt(1, id);
		pstm.setInt(2, id);
		pstm.setInt(3, id);
		pstm.execute();
		pstm.close();		
		return true;		
	} catch (SQLException e) {
		logger.error(e.getMessage());
		return false;
	} finally {
		try {
			con.close();
		} catch (SQLException e) {
			logger.error(e.getMessage());
		}
	}
	
	
	
}
   
   
   
   
   
    
    
}

