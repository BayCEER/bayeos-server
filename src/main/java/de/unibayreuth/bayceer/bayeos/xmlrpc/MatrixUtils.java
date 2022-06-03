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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.AggregateFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.StatusFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.TimeFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.AggregationTableHandler;


public class MatrixUtils {
	
	private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final Logger logger = LoggerFactory.getLogger(MatrixUtils.class); 
					
	
	/* Get a aggregated matrix query with value and status flag in on column seperated by semicolon
	 * @since 1.6
	 */	
	public static String getAggrQueryCom(Vector ids, TimeFilter t, AggregateFilter a, boolean withCounts) throws SQLException {
        String von = df.format(t.getVon());
        String bis = df.format(t.getBis());    
        Vector sids = new Vector(ids);
        Collections.sort(sids);
        String wert = (withCounts)? "wert || '';'' || counts" : "wert";
        String cols = (withCounts)? getColString(ids,"coalesce(m_",",';')") : getColString(ids, "m_", "");
        String sql = "SELECT von " + cols  + " from crosstab(" +
         "'select von,id," + wert  + " from " + AggregationTableHandler.getAggrTable(a) + " where id in (" + getInString(ids) + ") " +
        "and von >= ''" + von + " GMT-1''::timestamptz and von <= ''"  + bis + " GMT-1''::timestamptz " +
        // " union select null,null,null " +  // vermeidet get_crosstab_tuplestore: SPI_finish() failed wenn rowcount = 0
        " order by 1,2;', '" + getColSelect(sids) + "') " +
        "as ct(von timestamp with time zone " + getColString(sids,"m_", (withCounts)? " text":" real") + "); ";        
        return sql;
    }
	
	
	
	/* Get a matrix query with value and status flag in on column seperated by semicolon
	 * @since 1.6
	 */
	
    public static String getOrgQueryCom(Vector ids, TimeFilter t, StatusFilter s, Boolean withStatus){
        String von = df.format(t.getVon());
        String bis = df.format(t.getBis());
        
        String statFilter = SelectUtils.getInInteger(s.getIds());        
        Vector sids = new Vector(ids);
        Collections.sort(sids);
        String wert = (withStatus.booleanValue())? "wert || '';'' || status as wert" : "wert";
        String cols = (withStatus.booleanValue())? getColString(ids,"coalesce(m_",",';')") : getColString(ids, "m_", "");
        String sql = "SELECT von " + cols  + " from crosstab(" +
         "'select von,id," + wert  + " from v_messungen_all where id in (" + getInString(ids) + ") " +
        "and von >= ''" + von + " GMT-1''::timestamptz and von <= ''"  + bis + " GMT-1''::timestamptz and status in  " + statFilter +
        // " union select null,null,null " +  // vermeidet get_crosstab_tuplestore: SPI_finish() failed wenn rowcount = 0
        " order by 1,2;', '" + getColSelect(sids) + "') " +
        "as ct(von timestamp with time zone " + getColString(sids,"m_", (withStatus.booleanValue())? " text":" real") + "); ";        
        return sql;
    }
    
    /* Get a matrix query using separate columns for status flags
     * @since 1.7 
     */
    public static String getOrgQuerySep(Vector ids, TimeFilter t, StatusFilter s, boolean withStatus){
    	    	 
        String von = df.format(t.getVon());
        String bis = df.format(t.getBis());        
        String statFilter = SelectUtils.getInInteger(s.getIds());        
        Vector sids = new Vector(ids);
        Collections.sort(sids);        
                                                        
        String sqlM = "SELECT von " + getColString(ids, "m_", "")  + " from crosstab(" +
         "'select von,id,wert from v_messungen_all where id in (" + getInString(ids) + ") " +
        "and von >= ''" + von + " GMT-1''::timestamptz and von <= ''"  + bis + " GMT-1''::timestamptz and status in  " + statFilter +        
        " order by 1,2;', '" + getColSelect(sids) + "') " +
        "as ct(von timestamp with time zone " + getColString(sids,"m_"," real") + ") ";
        
        if (withStatus == false){
        	return sqlM + ";";	
        } else {        	        	
        	String sqlS = "SELECT von " + getColString(ids, "s_", "")  + " from crosstab(" +
            "'select von,id,status from v_messungen_all where id in (" + getInString(ids) + ") " +
            "and von >= ''" + von + " GMT-1''::timestamptz and von <= ''"  + bis + " GMT-1''::timestamptz and status in  " + statFilter +        
            " order by 1,2;', '" + getColSelect(sids) + "') " +
            "as ct(von timestamp with time zone " + getColString(sids,"s_"," integer") + ") ";        	
        	return "SELECT a.von " + getColString(ids, new String[]{"m_","s_"}) + " FROM ( " + sqlM + ") as a,(" + sqlS + ") as b where a.von = b.von;";  
        }
        
    }
    
    private static String getColString(Vector<Integer> ids, String[] prefix){
    	StringBuffer ret = new StringBuffer();    	
    	for(Integer id:ids){    		
    		for (String string : prefix) {
    			ret.append(", ") ;
    			ret.append(string);
    			ret.append(id);
    		}
    	}
    	return ret.toString();     	
    }
    
    
    
    /* Get a matrix query of aggregated values using separate columns for status flags
     * @since 1.7
     */ 
     
    public static String getAggrQuerySep(Vector ids, TimeFilter t, AggregateFilter a, Boolean withCounts) throws SQLException {
        String von = df.format(t.getVon());
        String bis = df.format(t.getBis());
        
        Vector sids = new Vector(ids);
        Collections.sort(sids);
        
        String sqlA = "SELECT von " + getColString(ids, "m_", "")  + " from crosstab(" +
         "'select von,id,wert from " + AggregationTableHandler.getAggrTable(a) + " where id in (" + getInString(ids) + ") " +
        "and von >= ''" + von + " GMT-1''::timestamptz and von <= ''"  + bis + " GMT-1''::timestamptz " +
        " order by 1,2;', '" + getColSelect(sids) + "') " +
        "as ct(von timestamp with time zone " + getColString(sids,"m_"," real") + ")"; 
        
        if (withCounts == false){
        	return sqlA + ";";
        } else {
        	String sqlB = "SELECT von " + getColString(ids, "c_", "")  + " from crosstab(" +
            "'select von,id,counts from " + AggregationTableHandler.getAggrTable(a) + " where id in (" + getInString(ids) + ") " +
            "and von >= ''" + von + " GMT-1''::timestamptz and von <= ''"  + bis + " GMT-1''::timestamptz " + 
            " order by 1,2;', '" + getColSelect(sids) + "') " +
            "as ct(von timestamp with time zone " + getColString(sids,"c_"," integer") + ") ";        	
        	return "SELECT a.von " + getColString(ids, new String[]{"m_","c_"}) + " FROM ( " + sqlA + ") as a,(" + sqlB + ") as b where a.von = b.von;";        		
        }                        
    }
   
	
	
    
	
	private static String getColString(Vector ids, String prefix, String postfix) {
	       Iterator it;
	       it = ids.iterator();  
	       StringBuffer buf = new StringBuffer(50);
	        while(it.hasNext()){
	         buf.append(",") ;
	         buf.append(prefix);
	         buf.append(it.next());
	         buf.append(postfix);
	        }
	        return buf.toString();
	    }
	    
	
	/* 
	 * O.A 21.01.2013 Added Order by 1 
	 */
	private static String getColSelect(Vector ids) {
	       Iterator it;
	       it = ids.iterator();
	       StringBuffer buf = new StringBuffer(50);
	       boolean firstrow = true;
	        while(it.hasNext()){         
	         if (firstrow) {
	             buf.append("select ") ;
	             firstrow = false;
	         } else {
	             buf.append(" union select ") ;
	         }
	         buf.append(it.next());
	        }
	        buf.append(" order by 1;");
	        return buf.toString();       
	   }
	
	
	public static  byte[] getShortMassendaten(ResultSet rs) throws SQLException, IOException{
        
		logger.debug("getShortMassendaten");
		ByteArrayOutputStream bs = new ByteArrayOutputStream(3*1024*1024);;        
        
        ByteBuffer bb = ByteBuffer.allocate(9);
          while(rs.next()){
          bb.putInt((int)(rs.getTimestamp(1).getTime()/1000)); // Timestamp as int 4 Byte Max Date 2036 !           
          bb.putFloat(rs.getFloat(2)); // Wert als 4 Byte
          bb.put(rs.getByte(3)); // Status as byte : 1 Byte
          bs.write(bb.array());
          bb.clear();
         }  // while
                
        return bs.toByteArray();
       }
	
	
		
	
	private static String getInString(Vector ids) {
        StringBuffer buf = new StringBuffer(50);
        buf.append("-1");       
        // Dummy Wert, Sequenzen beginnen immer bei 0
        // null funktioniert mit crosstab Funktion nicht
        Iterator it = ids.iterator();
        while(it.hasNext()){
            buf.append(",");
            buf.append((Integer)it.next());            
        }
        return buf.toString();
    }
	
	
	
	
	
	public static Vector getHeader(Connection con, Vector<Integer> ids, String tag) throws SQLException {							
			Hashtable t = new Hashtable(ids.size());
			Vector ret = new Vector(ids.size());						
			String sql = "select id, de from objekt where id in " + SelectUtils.getInInteger(ids) ;
			ResultSet rs;	
			rs = SelectUtils.getResultSet(con, sql);			
			while(rs.next()){				
			  t.put(rs.getInt(1),rs.getString(2));
			}			
			for (Integer id : ids) {
				ret.add(t.get(id));
				if (tag != null){
					 ret.add(t.get(id) + tag);
				}
			}
			return ret;

	 }
}
