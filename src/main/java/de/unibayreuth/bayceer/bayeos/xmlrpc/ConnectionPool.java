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
 * ConnectionPool.java
 *
 * Created on 18. Juli 2002, 16:39
 * Adapted to tomcat connection pool
 */


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.log4j.Logger;



/**
 *
 * @author  oliver
 */
public class ConnectionPool {
    
   
    
    private final static Logger logger = Logger.getLogger(ConnectionPool.class.getName());
	
   
    private static DataSource ds;
    
    
    /** Sets the user id for the current session
     */
    private static void setUserId(Connection con, int Id) throws SQLException {
        PreparedStatement st = con.prepareStatement("select set_userid(?)");
        st.setInt(1, Id);        
        st.execute();
        st.close();
    }

    public static Connection getPooledConnection(int userId)  throws SQLException {    	
       logger.debug("Trying to get connection from pool");   
       
       if (ds == null) {
    	   try {
    		   	logger.debug("Initialize datasource");
   				Context initCtx = new InitialContext();			
   				ds = (DataSource) initCtx.lookup("java:comp/env/jdbc/bayeos");
   				
   				logger.debug("Datasource look up completed.");
   			} catch (NamingException e) {
   				logger.error(e);
   				return null; 
   			}   
       }
       Connection con = ds.getConnection();       
       assert(con!=null);
       
       setUserId(con,userId);
       return con;
    }
    
                
    
    public static Connection getPooledConnection() 
        throws SQLException {
        return getPooledConnection(0);
    }
    
    
   


    
}
