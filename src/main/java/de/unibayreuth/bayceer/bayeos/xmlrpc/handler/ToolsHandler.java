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
 * ToolsHandler.java
 *
 * Created on 28. August 2002, 13:47
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.objekt.ObjektArt;
import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IToolsHandler;

/**
 *
 * @author  oliver
 */
public class ToolsHandler extends AccessHandler implements IToolsHandler {
    
    final static Logger logger = Logger.getLogger(ToolsHandler.class.getName()); 
    
    final static SimpleDateFormat df = new SimpleDateFormat();
        
    public ToolsHandler(){
      df.applyPattern("yyyy-MM-dd HH:mm:ss");      
    }
        
 
    public Boolean changePassword(String oldPassword,String newPassword) throws XmlRpcException{    	    	                        
            logger.debug("changePassword(" + getUserId() + ")");                       
            if (!checkWrite(getUserId())){
            	throw new XmlRpcException(0, "Missing right to change password.");
            }                       
            String sql = "update benutzer set pw = crypt(?,gen_salt('des')) where id = ? and crypt(?,substr(pw,1,2)) = ? and locked = false";
            try (Connection con = ConnectionPool.getPooledConnection();PreparedStatement pst = con.prepareStatement(sql)){            	            
            	pst.setString(1,newPassword);
            	pst.setInt(2, getUserId());
            	pst.setString(3, oldPassword);
            	pst.setString(4, oldPassword);
                return pst.executeUpdate() == 1;             	
            } catch (SQLException e){
            	logger.error(e.getMessage());
            	return false;
            }                            
        }
    
    

    public Integer updateRows(Integer id, String art, java.util.Date vonDate, java.util.Date bisDate, Integer statusId) throws XmlRpcException {
        PreparedStatement pst = null;
        Connection con = null;
        String sql;
        
        if (!checkWrite(id)) {
            throw new XmlRpcException(1,"Missing rights on Objekt " + id + ".");
          }
        
        try {
        // create prepared statement 
        logger.debug("updateRows(" + id + "," + art + "," + statusId + ")" );
        con = getPooledConnection();
        if (art.equalsIgnoreCase(ObjektArt.MESSUNG_MASSENDATEN.toString())) {
        sql = "update massendaten set status = ? where id = ? and von > ? and von <= ?";
        } else if (art.equalsIgnoreCase(ObjektArt.MESSUNG_LABORDATEN.toString())){
        sql = "update labordaten set status = ? where id = ? and bis > ? and bis <= ?";
        } else {
          throw new IllegalArgumentException("Unkown ObjektArt: " + art);
        }
        pst = con.prepareStatement(sql);
        pst.setInt(1,statusId.intValue());        
        pst.setInt(2,id.intValue());
        java.sql.Timestamp vonSql =  new java.sql.Timestamp(vonDate.getTime());
        pst.setTimestamp(3,vonSql);
        java.sql.Timestamp bisSql =  new java.sql.Timestamp(bisDate.getTime());
        pst.setTimestamp(4,bisSql);
        int i = pst.executeUpdate(); 
        logger.info("Updated " + i + " rows with status:" + statusId + " in messung: " + id + ".");
        return i;
        } catch (SQLException s) {
            logger.error(s.getMessage());
            throw new XmlRpcException(0, s.getMessage());
        } finally {
            try {con.close();pst.close();} catch (SQLException dummy){};
        }
        
    }
    
}

