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
 * LookUpTableHandler.java
 *
 * Created on 28. August 2002, 13:47
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ILookUpTableHandler;
/**
 *
 * @author  oliver
 */
public class LookUpTableHandler extends AccessHandler implements ILookUpTableHandler {
    
    final static Logger logger = Logger.getLogger(LookUpTableHandler.class.getName()); 
    

    
    public Vector getIntervalTypes() throws XmlRpcException {
    	try {
        if (logger.isDebugEnabled()) {         
            logger.debug("getIntervalTypes()");
        }
        Connection con =  getPooledConnection();
        String query = "select id, bezeichnung from intervaltyp order by id asc";
        if (logger.isDebugEnabled()) {         
        logger.debug("Query:" + query);
        }
		return XmlRpcUtils.getRows(con, query);
		} catch (SQLException e) {
			throw new XmlRpcException(1, e.getMessage());
		}
   }
    
    
    

    
    
    public Vector getStatus() throws XmlRpcException {
    	
    	try {
    	if (logger.isDebugEnabled()) {         
            logger.debug("getStatus()");
        }
        Connection con =  getPooledConnection();
        String query = "select id, bezeichnung, beschreibung from stati order by id asc";
        if (logger.isDebugEnabled()) {         
        logger.debug("Query:" + query);
        }
        return XmlRpcUtils.getRows(con, query);
    	} catch(SQLException e){
    		logger.error(e.getMessage());
    		throw new XmlRpcException(1, e.getMessage());
    	}
   }
    
    public Vector getBenutzer() throws XmlRpcException {
     	try {
        logger.debug("getBenutzer()");
        Connection con =  getPooledConnection();       
        String query = "select true , true, * from v_benutzer order by de asc";
        if (logger.isDebugEnabled()) {  
            logger.debug("Query:" + query);
        }
        return XmlRpcUtils.getRows(con, query);
    } catch(SQLException e){
		logger.error(e.getMessage());
		throw new XmlRpcException(1, e.getMessage());
	}
   }
   
    public Vector getGruppen() throws XmlRpcException {
     	try {
        logger.debug("getGruppen()");
        Connection con =  getPooledConnection();
        String query = "select true, true, * from v_gruppe order by de asc";
         if (logger.isDebugEnabled()) {  
        logger.debug("Query:" + query);
         }
        return XmlRpcUtils.getRows(con, query);
    } catch(SQLException e){
		logger.error(e.getMessage());
		throw new XmlRpcException(1, e.getMessage());
	}
   }
    
   
   public Vector getAgrIntervalle() throws XmlRpcException {
	 	try {
        logger.debug("getAgrIntervalle()");
        Connection con =  getPooledConnection();
        String query = "select id, name, extract(epoch from intervall)::integer as secs from aggr_intervall order by id";
        if (logger.isDebugEnabled()) {  
         logger.debug("Query:" + query);
        }               
        return XmlRpcUtils.getRows(con, query);
   } catch(SQLException e){
		logger.error(e.getMessage());
		throw new XmlRpcException(1, e.getMessage());
	}
   }
   
   public Vector getAgrFunktionen() throws XmlRpcException {
	 	try {
        logger.debug("getAgrFunktionen()");
        Connection con =  getPooledConnection();
        String query = "select id, name from aggr_funktion order by id";
        if (logger.isDebugEnabled()) {  
          logger.debug("Query:" + query);
         }
        return XmlRpcUtils.getRows(con, query);
   } catch(SQLException e){
		logger.error(e.getMessage());
		throw new XmlRpcException(1, e.getMessage());
	}
   }




public Vector getCRS() throws XmlRpcException {
	try {
        logger.debug("getCRS()");
        Connection con =  getPooledConnection();
        String query = "select id, short_name, fk_srid_id from coordinate_ref_sys order by short_name";
        if (logger.isDebugEnabled()) {  
          logger.debug("Query:" + query);
         }
        return XmlRpcUtils.getRows(con, query);
   } catch(SQLException e){
		logger.error(e.getMessage());
		throw new XmlRpcException(1, e.getMessage());
	}
}






public Vector getTimeZones() throws XmlRpcException {
	try {
        if (logger.isDebugEnabled()) {         
            logger.debug("geTimeZones()");
        }
        Connection con =  getPooledConnection();
        String query = "select id, name from timezone order by id asc";
        if (logger.isDebugEnabled()) {         
        logger.debug("Query:" + query);
        }
		return XmlRpcUtils.getRows(con, query);
		} catch (SQLException e) {
			throw new XmlRpcException(1, e.getMessage());
		}
}


public String getVersion() throws XmlRpcException {
	return getSysVariable("version");		
}


private String getSysVariable(String name) throws XmlRpcException {
	 Connection con = null;
	 PreparedStatement pst = null;
	 try {
	 con =  getPooledConnection();
	 pst = con.prepareStatement("select value from sys_variablen where name like ?");
	 pst.setString(1, name);
	 ResultSet rs = pst.executeQuery();
	 if(rs.next()){
		 return rs.getString(1);
	 } else {
		 throw new XmlRpcException(0, "Variable " + name + " not found in table sys_variablen.");
	 } 
	 
	 } catch (SQLException e){
		 return null;
	 } finally {
		 try {
			 if (pst != null) pst.close();
			 if (con != null) con.close();
		} catch (SQLException e) {
			logger.error(e.getMessage());
		}
	 }
	 
     
     
}
   
   
    
       
    
}

