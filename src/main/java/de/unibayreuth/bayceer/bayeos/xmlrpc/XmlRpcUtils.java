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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Hashtable;
import java.util.Vector;

import org.apache.log4j.Logger;

import de.unibayreuth.bayceer.bayeos.xmlrpc.types.XmlRpcType;

/*
 * XMLRpcUtils.java
 *
 * Created on 28. November 2002, 16:36
 */

/**
 *
 * @author  oliver
 */
public class XmlRpcUtils {
	
	
    
    final static Logger logger = Logger.getLogger(XmlRpcUtils.class.getName()); 
    
     public static Vector getRow(Connection con, String Sql)  throws SQLException{       
        Vector row = null;
        try {
        ResultSet rs = SelectUtils.getResultSet(con, Sql);
        if (rs.next()) {
        row = rowToVector(rs);
        } 
        rs.getStatement().close();
        rs.close();
        return row;
        } finally {
          try {if (!con.isClosed()) con.close();} catch (SQLException i) {;}
        }
     }
     
     
     public static Vector getRow(PreparedStatement pst)  throws SQLException{       
         Vector row = null;         
         ResultSet rs = pst.executeQuery();
         if (rs.next()) {
         row = rowToVector(rs);
         }          
         rs.close();
         return row;         
      }
     
     
     public static Vector getRows(PreparedStatement pst) throws SQLException{
    	 return getRows(pst, false);
     }
     
     
     public static Vector getRows(PreparedStatement pst, boolean withMeta) throws SQLException{
    	     		 
    	 
             ResultSet rs = pst.executeQuery();
             Vector ret = new Vector();
             if (withMeta) {
                  ret.addElement(getMetaData(rs));
                  ret.addElement(resultSetToVector(rs));
             } else {
                 ret = resultSetToVector(rs);
             }            
             rs.close();
             return ret;         
     }
     
     public static Vector getRows(Connection con, String Sql) throws SQLException{
         return getRows(con,Sql,false);
     }
     
     public static Vector getRows(Connection con, String Sql, boolean withMeta) throws SQLException{
          
          try {
           ResultSet rs = SelectUtils.getResultSet(con,Sql);
           Vector ret = new Vector();
           if (withMeta) {
                ret.addElement(getMetaData(rs));
                ret.addElement(resultSetToVector(rs));
           } else {
               ret = resultSetToVector(rs);
           }
           rs.getStatement().close();
           rs.close();
           return ret;
          } finally {
           try {if (!con.isClosed()) con.close();} catch (SQLException i) {;}
          }
	}
     
     
     
              
       public static Vector rowToVector(ResultSet rs) throws SQLException {
         ResultSetMetaData md = rs.getMetaData();
           int colCount = md.getColumnCount();
            Vector row = new Vector(colCount +1 );
	    for (int i = 0; i < colCount; i++) {
                switch (getXmlRpcType(md.getColumnType(i + 1),md.getScale(i+1))) {
                case XmlRpcType.STRING:
                  String s = rs.getString(i + 1);
                  row.add(rs.wasNull() ? null : s);
		  break;
                case XmlRpcType.INTEGER:
                  Integer in = new Integer(rs.getInt(i+1));
                  row.add(rs.wasNull() ? null:in);  
                  break;
                case XmlRpcType.DOUBLE:
                  Double dbl = new Double(rs.getDouble(i+1)) ;
                  row.add(rs.wasNull() ? null:dbl);
		  break;
                case XmlRpcType.DATE:
                  java.util.Date d = rs.getTimestamp(i + 1);
                  row.add(rs.wasNull() ? null:d);
		  break;
                case XmlRpcType.BOOLEAN: 
		  Boolean bol = new Boolean(rs.getBoolean(i + 1));
                  row.add(rs.wasNull() ? null:bol);
   		  break;
  	        default:
		  throw new SQLException("ColumnType: " + md.getColumnType(i + 1) +
                  " ColumnName:" + md.getColumnName(i +1) + " not supported by Xml-Rpc");
	          }               //switch
	      }                   //for
	      // row.trimToSize();
              return row;
       }
       
       public static Vector resultSetToVector(ResultSet rs) throws SQLException {
           Vector rows = new Vector();
           while(rs.next()){
              rows.addElement(rowToVector(rs));
           }
           rows.trimToSize();
           return rows;
       }

     public static int getXmlRpcType(int sqlType, int sqlScale){
           switch (sqlType){
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
                            return XmlRpcType.STRING;
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.NUMERIC:
            case Types.REAL:
                // Sonderfall für Primärschlüssel und 
                // Numeric 
                if (sqlScale == 0) {
                    return XmlRpcType.INTEGER;
                } else {
                    return XmlRpcType.DOUBLE;
                }
            case Types.TIMESTAMP:
            case Types.TIME:
            case Types.DATE:
                            return XmlRpcType.DATE;
            case Types.BIT: 
                            return XmlRpcType.BOOLEAN;
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.BIGINT:
                            return XmlRpcType.INTEGER;
            default:
                return 0;
       }
      }
        
        
        
     public static Hashtable getLookUpHashtable(Connection con, String Sql) throws SQLException{
          Hashtable h = new Hashtable();
          try {
           ResultSet rs = SelectUtils.getResultSet(con,Sql);
           ResultSetMetaData md = rs.getMetaData();
           int colCount = md.getColumnCount();
           if (colCount != 2) {
               throw new SQLException("Wrong number of columns in SQL string");
           }           
           while(rs.next()){               
            h.put(new Integer(rs.getInt(1)),rs.getString(2));
           }
           rs.getStatement().close();
           rs.close();
           return h;
          } finally {
             try {if (!con.isClosed()) con.close();} catch (SQLException i) {;}
          }
       }
        
        
     public static Vector getMetaData(ResultSet rs) throws SQLException {    	      
         Vector vCols = new Vector();
         ResultSetMetaData md = rs.getMetaData();
         int colCount = md.getColumnCount();
         for (int i = 1; i <= colCount; i++) {
             Vector vCol = new Vector();
             vCol.add(md.getColumnName(i));
             vCol.add(md.getColumnLabel(i));
             vCol.add(new Integer(getXmlRpcType(md.getColumnType(i),md.getScale(i))));
	     vCols.add(vCol);
          }           
         return vCols;
        }

	
       
}
