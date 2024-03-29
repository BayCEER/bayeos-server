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
import java.sql.SQLException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ILookUpTableHandler;
/**
 *
 * @author  oliver
 */
public class LookUpTableHandler extends AccessHandler implements ILookUpTableHandler {
    
    final static Logger logger = LoggerFactory.getLogger(LookUpTableHandler.class); 
    
    private static String version; 
    

    
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
	return version;		
}


public static void setVersion(String version) {
	LookUpTableHandler.version = version;
}
   
   
   
    
}

