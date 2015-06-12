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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.objekt.ObjektArt;
import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
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
            if (logger.isDebugEnabled()) {  
             logger.debug("changePassword(" + getUserId() + ")");
            }
           
            if (!checkWrite(getUserId())){
            	throw new XmlRpcException(0, "Missing right to change password.");
            }
            
            ResultSet rs1 = null;    	
            ResultSet rs2 = null;
        	PreparedStatement st = null;
        	Connection con = null;
            try {
            	
            	rs1 = SelectUtils.getResultSet(ConnectionPool.getPooledConnection(), "select login, url, name, update_stmt, query_stmt, username, password from auth_db, benutzer where benutzer.fk_auth_db = auth_db.id and benutzer.id = " + getUserId());
            	// Check if user is authenticated by database        	
            	if (!rs1.next()){
            		throw new XmlRpcException(0, "Can't change password for none database users.");            		
            	} else {            		            		            		
            		String name   = rs1.getString("name");
            		String login = rs1.getString("login");
            		logger.info("Trying to update password for user: " + login + " by authentication method: " + name);
            		
            		// Open connection to database 
            		Properties props = new Properties();
            		props.setProperty("user",rs1.getString("username"));
            		props.setProperty("password",rs1.getString("password"));		
            		con = DriverManager.getConnection(rs1.getString("url"), props);
            		
            		// Verify old password 
            		st = con.prepareStatement(rs1.getString("query_stmt"));		
            		st.setString(1,oldPassword);
            		st.setString(2,login);
            		
            		rs2 = st.executeQuery();
            		rs2.next();
            		
            		if (!rs2.getBoolean(1)){            			
            			logger.warn("Failed to authenticate user with old password.");
            			throw new XmlRpcException(0, "Failed to authenticate user " + login + " with old password." );			
            		}
            		
            		// Save new password 
            		st = con.prepareStatement(rs1.getString("update_stmt"));
            		st.setString(1,newPassword);
            		st.setString(2,login);        		
            		st.executeUpdate();
            	
            	}
            } catch (SQLException e) {
        		logger.error(e.getMessage());
        		throw new XmlRpcException(0, "Error changing password for user " + this.getUserId());
        	} finally {
        		try {        			
        			rs1.close();
        			rs2.close();
        			if (con != null) con.close();
        		} catch (SQLException e) {
        			logger.error(e.getMessage());
        		}    		
        	}
            return true;
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

