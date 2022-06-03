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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SelectUtils {
    final static Logger logger = LoggerFactory.getLogger(SelectUtils.class); 
 
    public static String addSlashes(String s){
    	return s.replaceAll("\\\\","\\\\\\\\").replaceAll("'","\\\\'");
    }	
    
    public static ResultSet getResultSet(Connection con, String Sql) throws SQLException{
           try {
           if (logger.isDebugEnabled()) {
            logger.debug("getResultSet():" + Sql );
           }
           Statement st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
           ResultSet rs = st.executeQuery(Sql);
           return rs;
           } catch (SQLException e) {
               logger.error(e.getMessage());
               throw new SQLException(e.getMessage());
           }
       }
       
       
       public static Object getObject(Connection con, String Sql) throws SQLException {         
          try {
           ResultSet rs = getResultSet(con,Sql);
           rs.next();
           Object o = rs.getObject(1);
           rs.getStatement().close();
           rs.close();
           return o;
          } finally {
             try {if (!con.isClosed()) con.close();} catch (SQLException i) {;}
          }
       }
       
       public static Integer getInt(Connection con, String Sql) throws SQLException{          
          try {
           ResultSet rs = getResultSet(con,Sql);
           rs.next();
           Integer in = new Integer(rs.getInt(1));
           rs.getStatement().close();
           rs.close();
           return in;
          } finally {
             try {if (!con.isClosed()) con.close();} catch (SQLException i) {;}
          }
       }
       
       public static String getString(Connection con, String Sql) throws SQLException{
          logger.debug("getString()");
          try {
           ResultSet rs = getResultSet(con,Sql);
           rs.next();
           String in = new String(rs.getString(1));
           rs.getStatement().close();
           rs.close();
           return in;
          } finally {
             try {if (!con.isClosed()) con.close();} catch (SQLException i) {;}
          }
       }
       
       
               
       
        public static Boolean getBoolean(Connection con, String Sql) throws SQLException{         
          try {
           ResultSet rs = getResultSet(con,Sql);
           if (rs == null) return null;
           rs.next();
           Boolean bol = new Boolean(rs.getBoolean(1));
           rs.getStatement().close();
           rs.close();
           return bol;
          } finally {
           try {if (!con.isClosed()) con.close();} catch (SQLException i) {;}
          }
       }
        
        
      /*
       * returns an integer in clause like := "(n,n,null)"
       */
      public static String getInInteger(Vector vec) {
          Iterator it = vec.iterator();
          StringBuffer strStatId = new StringBuffer("(");
          while(it.hasNext()){
              strStatId.append((Integer)it.next() + ",");              
          }
          return (strStatId.append("null)").toString());
      }

      /*
       * returns an string in clause like := "('n','n','')"
       */
      
      public static String getInString(Vector vec) {
  		Iterator it = vec.iterator();
  		StringBuffer b = new StringBuffer("(");
  		while (it.hasNext()) {
  			b.append("'");
  			b.append(SelectUtils.addSlashes(it.next().toString()));
  			b.append("',");
  		}
  		b.append("'')");
  		return (b.toString());
  	}
	
		
	}
	
	
       
       
       
       
       
    
        
     
