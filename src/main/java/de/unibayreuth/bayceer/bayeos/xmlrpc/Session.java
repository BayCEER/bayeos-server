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
/*
 * Static methods for session management
 * OA, 22.08.2013: Refactoring of create and terminate function. Added function to drop expired sessions. 
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;


public class Session  {
	
  final static Double maxKey = Math.pow(2,31);
    
  final static Logger logger = Logger.getLogger(Session.class.getName()); 
    
  public static Integer create(Integer benutzerId) throws XmlRpcException  {	  	  
	  int key = ((Double)(Math.random() * maxKey)).intValue();	  
	  Connection con = null;
	  String sql = "insert into session (id_benutzer,key) values (?,?)";	  
	  PreparedStatement st = null; 
	    try {
	    	   con = ConnectionPool.getPooledConnection();
	           st = con.prepareStatement(sql);       
	           st.setInt(1,benutzerId);           
	           st.setInt(2,key);  
	           st.execute();	           
	    } catch (SQLException e){
	    	logger.error(e.getMessage());
	    	throw new XmlRpcException(0, "Failed to create session.");	    		    	    	
	    } finally {
	       try {
	    	   st.close();
	    	   if (!con.isClosed()) con.close();
	       } catch (SQLException e) { logger.error(e.getMessage());
		   }
	    }
	    return key;
  }
  
  private static void dropExpiredSessions() throws XmlRpcException {
	  Connection con = null;	  	  
	  Statement st = null; 
	    try {
	    	   con = ConnectionPool.getPooledConnection();	    	   
	    	   st = con.createStatement();	    	   	                  
	           st.execute("delete from session where von < now() - '1 hour'::interval");           	                                    	           	           
	    } catch (SQLException e){
	    	logger.error(e.getMessage());
	    	throw new XmlRpcException(0, "Failed to delete expired sessions.");	    		    	    	
	    } finally {
	       try {
	    	   st.close();
	    	   if (!con.isClosed()) con.close();
	       } catch (SQLException e) { logger.error(e.getMessage());
		   }
	    }
  }
   
  public static Boolean terminate(Integer key, Integer benutzerId) throws XmlRpcException{	  	  	  
	  Connection con = null;
	  dropExpiredSessions();
	  String sql = "delete from session where id_benutzer = ? and key = ?";	  
	  PreparedStatement st = null; 
	    try {
	    	   con = ConnectionPool.getPooledConnection();
	           st = con.prepareStatement(sql);       
	           st.setInt(1,benutzerId);           
	           st.setInt(2,key);  
	           st.execute();
	    } catch (SQLException e){
	    	logger.error(e.getMessage());
	    	throw new XmlRpcException(0, "Failed to terminate session.");	    		    	    	
	    } finally {
	       try {
	    	   st.close();
	    	   if (!con.isClosed()) con.close();
	       } catch (SQLException e) { logger.error(e.getMessage());
		   }
	    }
	    return true;	          
  }
     
  public static boolean loggedIn(Integer SessionId, Integer userId) {	
	  	  
	Connection con = null;
	String sql = "UPDATE session set von = current_timestamp WHERE key = ? and id_benutzer = ? and von + '1 Hours'::interval  >= current_timestamp";
	PreparedStatement st = null; 
    try {
    	   con = ConnectionPool.getPooledConnection();
           st = con.prepareStatement(sql);       
           st.setInt(1,SessionId);           
           st.setInt(2,userId);                         
           return st.executeUpdate()== 1;
            
    } catch (SQLException e){
    	logger.error(e.getMessage());
    	return false;    	
    } finally {
       try {
    	   st.close();
    	   if (!con.isClosed()) con.close();
       } catch (SQLException e) { logger.error(e.getMessage());
	}
    }
	
}
}

