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
 * AccessHandler.java
 *
 * Created on 28. August 2002, 13:47
 */

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Vector;

import org.apache.xmlrpc.InvalidSessionException;
import org.apache.xmlrpc.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRightException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.Session;



/**
 *
 * @author  oliver
 */
/**
 * @author oliver
 *
 */
public abstract class AccessHandler implements SessionHandler {
    
    final static Logger logger = LoggerFactory.getLogger(AccessHandler.class); 
    
     
    protected Integer sessionId;
    protected Integer userId;
    
    
   public void checkSession(Integer SessionId, Integer UserId) throws InvalidSessionException  {       
       if (!Session.loggedIn(SessionId, UserId)) {
    	   throw new InvalidSessionException(0,"Session does not exist. Please login again.");
       } else {
    	   this.sessionId = SessionId;
    	   this.userId = UserId;    	  
       }       
     
    }
   
   protected Boolean checkFullAccess(Integer objektId) throws InvalidRightException  {
        if (logger.isDebugEnabled()) {  
        logger.debug("checkFullAccess(" + objektId +"," + this.userId +")");
        }
        try {
          String sql =  "select check_rwx("+ objektId + "," + this.userId + ")";
          return SelectUtils.getBoolean(getPooledConnection(),sql).booleanValue();
        } catch (SQLException e) {
            throw new InvalidRightException(0,e.getMessage());
        }
     
    }
   
    
    protected Boolean checkRead(Integer objektId) throws InvalidRightException {
        // Check Read Access 
        if (logger.isDebugEnabled()) {  
        logger.debug("checkRead(" + objektId +"," + this.userId +")");
        }
        try {
          return SelectUtils.getBoolean(getPooledConnection(),
          "select check_read("+ objektId + "," + this.userId + ")").booleanValue();
        } catch (SQLException e) {
            throw new InvalidRightException(0,e.getMessage());
        }
    }
    
    protected Boolean checkWrite(Integer objektId) throws InvalidRightException{
        // Check Write Access 
        if (logger.isDebugEnabled()) {  
        logger.debug("checkWrite(" + objektId +"," + this.userId + ")");
        }
        try {
         return SelectUtils.getBoolean(getPooledConnection(),
         "select check_write("+ objektId + "," + this.userId + ")").booleanValue();
        } catch (SQLException e) {
            throw new InvalidRightException(0,e.getMessage());
        }
    }
    
    protected Boolean checkExecute(Integer objektId) throws InvalidRightException{
        // Check Execute Access 
        if (logger.isDebugEnabled()) {  
        logger.debug("checkExecute(" + objektId +"," + this.userId + ")");
        }
        try {
         return SelectUtils.getBoolean(getPooledConnection(),
         "select check_exec("+ objektId + "," + this.userId + ")").booleanValue();
        } catch (SQLException e) {
          throw new InvalidRightException(0, e.getMessage());
        }        
    }
    
    
    public Boolean checkRead(Vector ids) throws InvalidRightException {    	
    	for(Object i:ids){
    		if (!checkRead((Integer) i)){
    			return false;
    		}
    	}
    	return true;
    }
    
    public Boolean checkWrite(Vector ids) throws InvalidRightException {    	
    	for(Object i:ids){
    		if (!checkWrite((Integer) i)){
    			return false;
    		}
    	}
    	return true;
    }
    
    public Boolean checkExecute(Vector ids) throws InvalidRightException {    	
    	for(Object i:ids){
    		if (!checkExecute((Integer) i)){
    			return false;
    		}
    	}
    	return true;
    }
    
    public Boolean checkFullAccess(Vector ids) throws InvalidRightException {    	
    	for(Object i:ids){
    		if (!checkFullAccess((Integer) i)){
    			return false;
    		}
    	}
    	return true;
    }
    
    
    
    
    
    
    
    
    protected Boolean checkExecute(String art) throws InvalidRightException{    	
    	if (logger.isDebugEnabled()) {  
            logger.debug("checkExecute(" + art +"," + this.userId + ")");
            }
            try {
             return SelectUtils.getBoolean(getPooledConnection(),
             "select check_exec(id," + this.userId + ") from art_objekt where uname like '" + SelectUtils.addSlashes(art) + "'").booleanValue();
            } catch (SQLException e) {
              throw new InvalidRightException(0, e.getMessage());
            }
            
    	
    }
        
    protected Connection getPooledConnection() throws SQLException {
        return ConnectionPool.getPooledConnection(userId);
    }
    
    
        	
        	
        
        	
               
        
    
    
    /** Getter for property userId.
     * @return Value of property userId.
     *
     */
    protected java.lang.Integer getUserId() {
        return userId;
    }    
    
    /** Setter for property userId.
     * @param userId New value of property userId.
     *
     */
    protected void setUserId(java.lang.Integer userId) {
        this.userId = userId;
    }    
    
}

