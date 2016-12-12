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
 */


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.log4j.Logger;


/**
 *
 * @author  oliver
 */
public class ConnectionPool {
    
   
    
    private final static Logger logger = Logger.getLogger(ConnectionPool.class.getName());
	
    private static String url;
    private static String user;
    private static String password;
    
    public static void setConnection(String _url, String _user, String _password){    	
    	url = _url;
    	user = _user;
    	password = _password;
    }
    
   
    private static BasicDataSource ds = null;
    
    
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
    		   	logger.info("Initialize datasource:" + url);
    		   	ds = new BasicDataSource();    		   	
    		   	ds.setDriverClassName("org.postgresql.Driver");    		   	
    		   	ds.setUrl(url);
    		   	ds.setUsername(user);
    		   	ds.setPassword(password);
    		   	ds.setMaxTotal(50);   				
       }
       Connection con = ds.getConnection();                     
       setUserId(con,userId);
       return con;
    }
    
                
    
    public static Connection getPooledConnection() 
        throws SQLException {
        return getPooledConnection(0);
    }
    
    
   


    
}
