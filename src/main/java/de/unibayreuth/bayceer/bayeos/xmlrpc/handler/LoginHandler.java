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


		


import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPException;
import com.novell.ldap.LDAPJSSESecureSocketFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.Session;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XMLServlet;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ILoginHandler;


public class LoginHandler implements ILoginHandler {
    final static Logger logger = Logger.getLogger(LoginHandler.class.getName());
	             
    
    public LoginHandler(){
    	
    }
    


	public Vector createSession(String Login,String PassWord) throws XmlRpcException {
                
          Vector result = new Vector();                     
          Integer userId = authenticate(Login,PassWord);          
          Integer sessionId = Session.create(userId);                                        
          result.add(sessionId);
          result.add(userId);
          return result;
	
   }
        
        
   private Integer authenticate(String Login, String PassWord) throws XmlRpcException {
	   
	   Integer idBenutzer = null;
       Integer idAuthLdap = null;
	   	   	   
	   Connection con = null;
	   ResultSet rs = null;
	   PreparedStatement pst = null;
	   
	   String ip = XMLServlet.getClientIpAddress();
	   logger.info("Authentication User:" + Login + " from IP:" + ip );
	   	   
	   
	   // Authenticate by ip without password  
	   try {
	   	   
	   con = ConnectionPool.getPooledConnection();
	   pst = con.prepareStatement(	"select access from auth_ip " + 
			   						"where ?::inet<<=network and (login=? or login='*') " +
			   						"order by case when login='*' then 1 else 0 end, "+  /*Direkte Nutzer kommen zuerst*/
			   						"masklen(network) desc, " + /*Genauere Einträge zuerst */
			   						"access" /*TRUST vor DENY vor PASSWORD*/
			   						);		   
	   pst.setString(1, ip);
	   pst.setString(2, Login);
	   rs = pst.executeQuery();	   
	   if (rs.next()){
		   String access = rs.getString(1);		   
		   if (access.equals("TRUST")){			   			   
			   idBenutzer = getBenutzerId(Login);
			   if (idBenutzer == null){
				   throw new XmlRpcException(0, "Error authenticating user " + Login + "." );
			   } else {
				   return idBenutzer;   
			   }			   
		   } else if (access.equals("DENY")){
			   throw new XmlRpcException(0, "Error authenticating user " + Login + "." );
		   }		   
	   }
	   
	   
 	   String query = "select id, fk_auth_ldap, fk_auth_db from benutzer where login = ? and locked = false";
 	   pst = con.prepareStatement(query); 	   
       pst.setString(1, Login);            
       rs = pst.executeQuery();
       if (!rs.next()) {
    	   throw new XmlRpcException(0, "Error authenticating user " + Login + "." );
       }
       
       idBenutzer = rs.getInt(1);
       idAuthLdap = rs.getInt(2);

       if (!rs.wasNull()){    
     	   authenticatLdap(idAuthLdap, idBenutzer,Login,PassWord);
     	   return idBenutzer;
       } else {    	  
     	  Integer idAuthDB = rs.getInt(3);    	  
     	  if (!rs.wasNull()){
     		  authenticateDB(idAuthDB, idBenutzer,Login,PassWord);
     		  return idBenutzer;
     		  
     	  } else {
     		  throw new XmlRpcException(0,"No authentication method defined.");     		  
     	  }    	  
       }
       
	   } catch (SQLException e){
		   logger.error(e.getMessage());
		   throw new XmlRpcException(0, "Error authenticating user " + Login + "." );
	   } finally {
		   try {
			   if (pst != null) {
				   pst.close();
			   }
			   if (con != null) {
				   con.close();
			   }
		} catch (SQLException e) {
			logger.error(e.getMessage());
		}
	   }
   }


private Integer getBenutzerId(String login)  {
	
	ResultSet rs = null;
	PreparedStatement st = null;
	Connection con = null;
	
	try {	
	con = ConnectionPool.getPooledConnection();
	
	st = con.prepareStatement("select id from benutzer where login like ?");
	st.setString(1, login);
	rs = st.executeQuery();	
	if (rs.next()) {
		return rs.getInt(1); 
	} else {
		return null;
	}	
	} catch (SQLException e){
		logger.error(e.getMessage());
		return null;
	} finally {
		try {
			st.close();
			con.close();
		} catch (SQLException e) {
			logger.error(e.getMessage());
		}
	}
	
	
	
	
	
	 
	
	

}


private void authenticateDB(Integer idAuthDB, Integer idBenutzer, String login, String passWord) throws XmlRpcException  {
	ResultSet rs1 = null;
	ResultSet rs2 = null;
	PreparedStatement st = null;
	Connection conAuth = null;
	Connection con = null;
	try {
		
		con = ConnectionPool.getPooledConnection();
		rs1 = SelectUtils.getResultSet(con, "select url, name, query_stmt, username, password from auth_db where id = " + idAuthDB);
		rs1.next();
		
		String name = rs1.getString("name");
		logger.debug("Trying to authenticate user " + login + " by " + name);

		Properties props = new Properties();
		props.setProperty("user",rs1.getString("username"));
		props.setProperty("password",rs1.getString("password"));		
		conAuth = DriverManager.getConnection(rs1.getString("url"), props);						
		st = conAuth.prepareStatement(rs1.getString("query_stmt"));		
		st.setString(1,passWord);
		st.setString(2,login );
		
		rs2 = st.executeQuery();
		rs2.next();
		
		if (rs2.getBoolean(1)){
			logger.info("User " + login + " authenticated by " + name);
			return; // Authentication successful
		} else {
			logger.warn("Failed to authenticate user " + login + " by " + name);
			throw new XmlRpcException(0, "Error authenticating user " + login );			
		}
		 

	} catch (SQLException e) {
		logger.error(e.getMessage());
		throw new XmlRpcException(0, "Error authenticating user " + login );
	} finally {		
			try {con.close();} catch (SQLException e) {}
			try {conAuth.close();} catch (SQLException e) {}			
	}
	

}


private void authenticatLdap(Integer idAuthLdap, Integer idBenutzer, String login, String passWord) throws XmlRpcException {
	ResultSet rs1 = null;
	int ldapVersion = LDAPConnection.LDAP_V3;
	LDAPConnection lc = null;
	Connection con = null;
	try {
	con = ConnectionPool.getPooledConnection();
	rs1 = SelectUtils.getResultSet(con, "select name, host, dn, ssl, port from auth_ldap where id = " + idAuthLdap);
	rs1.next();
	String name = rs1.getString("name");
	logger.debug("Trying to authenticat user " + login + " by " + name);	
	
	if (rs1.getBoolean("ssl")){
		lc = new LDAPConnection(new LDAPJSSESecureSocketFactory());
	} else {		
		lc = new LDAPConnection();
	}		
	lc.connect(rs1.getString("host"), rs1.getInt("port") );
    lc.bind(ldapVersion, rs1.getString("dn").replace(":?", login), passWord.getBytes("UTF8") );
    if (lc.isBound()) {
    	logger.info("User " + login + " authenticated by " + name);
    } else {
    	logger.warn("Failed to authenticate user: " + login + " by " + name);
		throw new XmlRpcException(0, "Error authenticating user: " + login );
    }
  } catch (SQLException e) {
	   logger.error(e.getMessage());
	   throw new XmlRpcException(0, "Error authenticating user: " + login );
  } catch (LDAPException e) {
	  logger.error(e.getMessage());
	   throw new XmlRpcException(0, "Error authenticating user: " + login );	
} catch (UnsupportedEncodingException e) {
	  logger.error(e.getMessage());
	   throw new XmlRpcException(0, "Error authenticating user: " + login );
} finally {
	try {
		con.close();		
	} catch (SQLException e) {
		logger.error(e.getMessage());
	}

	
}
	
}
        
        
    
                
}
       
        
