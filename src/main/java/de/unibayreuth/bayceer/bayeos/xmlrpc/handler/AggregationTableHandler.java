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
 * AggregationTableHandler.java
 *
 * 
 */

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;


import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ByteUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.MatrixUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.AggregateFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.TimeFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IAggregationTableHandler;

/**
 *
 * @author  oliver
 */
public class AggregationTableHandler extends AccessHandler implements  IAggregationTableHandler {
    
    final static Logger logger = LoggerFactory.getLogger(AggregationTableHandler.class); 
    
    
    
    public static String getAggrTable(AggregateFilter a) throws SQLException {
        Connection con = null;
        PreparedStatement pst = null;
        try {
        con = ConnectionPool.getPooledConnection();
        
        String query = "select get_aggr_table(?,?)";
        pst = con.prepareStatement(query,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
        pst.setInt(1,a.getFunctionId().intValue());
        pst.setInt(2,a.getIntervallId().intValue());
        ResultSet rs = pst.executeQuery();        
        rs.next();
        String table = rs.getString(1);
        return table;
        } finally {
            try {pst.close();con.close();} catch (SQLException e){logger.error(e.getMessage());}
        }
        
        
    }
    
    public Vector getRows(Integer primaryKey, Vector timeFilter, Vector aggregateFilter) throws XmlRpcException {
        PreparedStatement pst = null;
        Connection con = null;
        ResultSet rs = null;
        Vector vReturn = null;
        
        try {
        
        if (!checkRead(primaryKey)) {
                throw new XmlRpcException(1,"Objekt " + primaryKey + " does not exist.");
        }
                    	
        TimeFilter tFilter = new TimeFilter(timeFilter);
        AggregateFilter aFilter = new AggregateFilter(aggregateFilter);
        
        if (logger.isDebugEnabled()) {  
         logger.debug("getAggregation(" + primaryKey + "," + tFilter.toString() + "," + aFilter.toString() + ")" );
        }
        
        
        String table = getAggrTable(aFilter);
        con = getPooledConnection();
        
        String query = "select VON , WERT , COUNTS from " + table + " where id = ? and von >= ? and von <= ? order by von asc";
        if (logger.isDebugEnabled()) {  
         logger.debug("Query:" + query);
        }
        pst = con.prepareStatement(query);
        pst.setInt(1, primaryKey.intValue());
        java.sql.Timestamp sqlVonDate =  new java.sql.Timestamp(tFilter.getVon().getTime());
        pst.setTimestamp(2,sqlVonDate);
        
        java.sql.Timestamp sqlBisDate =  new java.sql.Timestamp(tFilter.getBis().getTime());
        pst.setTimestamp(3,sqlBisDate);

        rs = pst.executeQuery();
        vReturn = new Vector();
        vReturn.add(XmlRpcUtils.getMetaData(rs));
        vReturn.add(XmlRpcUtils.resultSetToVector(rs));
        } catch (SQLException e){
            throw new XmlRpcException(0,e.getMessage());
        } finally {
            try {pst.close();con.close();} catch (SQLException e){logger.error(e.getMessage());}
        }
        return vReturn;
   }
    
    
   	public Vector getMatrix(Vector ids, Vector timeFilter,Vector aggrFilter, Boolean withCounts) throws XmlRpcException {
    	
    	if (ids == null || ids.size() == 0){
			throw new XmlRpcException(0, "Vector of ids is empty");			
		}
		
		// Check uniqueness of ids
		HashSet<Integer> i = new HashSet<Integer>(ids);		
		if (i.size() != ids.size()){
			throw new XmlRpcException(0, "Id values must be unique");
		}
		 
		Iterator it = ids.iterator();
	     while (it.hasNext()){
	    	 Integer id = (Integer)it.next();
	          if (!checkRead(id)) {
	             throw new XmlRpcException(1,"There is no series identified by id: " + id);
	           }    
	     }  
		 TimeFilter tf = new TimeFilter(timeFilter);
	     AggregateFilter af = new AggregateFilter(aggrFilter);	     	     
	     Connection con = null;
	     try {
	    	con = getPooledConnection();
	    	Vector ret = new Vector(2);
	    	
	    	ret.add(MatrixUtils.getHeader(con, ids, withCounts?"_counts":null));		    		    		    		    	 
			String sql = MatrixUtils.getAggrQuerySep(ids,tf,af,withCounts.booleanValue()); 
			logger.debug(sql);
			Statement st = con.createStatement();
			ResultSet rs = st.executeQuery(sql);										
			ret.add(ByteUtils.getByte(rs));
			return ret;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(1,"Failed to get matrix");
		} catch (IOException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(1,"Failed to get matrix");
		} finally {
			try {
				con.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		}


	}

	     
	     
	     
	         
           
}

