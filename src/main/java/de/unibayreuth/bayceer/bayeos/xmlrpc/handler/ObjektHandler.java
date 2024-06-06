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
 * ObjektHandler.java
 *
 * Created on 28. August 2002, 13:47
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.objekt.ObjektArt;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRightException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IObjektHandler;


/**
 *
 * @author  oliver
 */
public class ObjektHandler extends AccessHandler implements IObjektHandler  {
    
    final static Logger logger = LoggerFactory.getLogger(ObjektHandler.class); 
    
       
   
  
public Vector getObjekt(Integer Id, String objektArt) throws XmlRpcException {
        if (logger.isDebugEnabled()) {  
         logger.debug("getObjekt(" + Id + "," + objektArt + ")");
        }
        
        try {
        ObjektArt art = ObjektArt.get(objektArt);
        String sql = "select check_write(id,"+getUserId()+") , check_exec(id,"+getUserId()+"), * from v_" + art.toString()  
        + " where id = "  + Id + " and check_read(id," + getUserId() + ")";    
        return XmlRpcUtils.getRow(getPooledConnection(), sql);
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0,e.getMessage());
		} catch (IllegalArgumentException e){
			logger.error(e.getMessage());
			throw new XmlRpcException(0,"Illegal argument.");			
		}
   }
   
   

public Vector createReference(Integer vonObjId, Integer aufObjId, String aufArt) throws XmlRpcException {
        Integer id_verweis;
        String sql;
        if (logger.isDebugEnabled()) {  
         logger.debug("createReference(" + vonObjId + "," + aufObjId + "," + aufArt + ")");
        }
        
        if (!checkFullAccess(aufObjId)) 
            throw new InvalidRightException(0,"Missing rights on " + aufObjId + ".");
        try {
        
        if (ObjektArt.get(aufArt).isExtern()) {
         id_verweis = SelectUtils.getInt(getPooledConnection()
                    , "select create_web_verweis(" + vonObjId + "," + aufObjId + ")" );
         sql = " select case when o.uname like 'web_ls%' then 'Department'::text when o.uname like 'web_pro%' then 'Project'::text when o.uname like 'web_mit%' then 'Employee'::text end as type"  +
         ", htmldecode(o.de), ve.von, ve.bis, ve.id, ve.id_von, o.uname, ve.id_auf from v_web_objekt o,verweis_extern ve " +
         " where ve.id_von = o.id and ve.id = " + id_verweis;
        } else {
            id_verweis = SelectUtils.getInt(getPooledConnection()
            , "select create_verweis(" + vonObjId + "," + aufObjId + ")" );
            sql = "select art_objekt.kurz as type, objekt.de as description, verweis.von, verweis.bis, "  +
            "verweis.id as id_verweis, objekt.id as id_von,art_objekt.uname, verweis.id_auf " +
            "from verweis, objekt, art_objekt where verweis.id_von = objekt.id and objekt.id_art = art_objekt.id and verweis.id = " + id_verweis ;        
        }
        
        
        return XmlRpcUtils.getRow(getPooledConnection(), sql);
        
        } catch (SQLException e){
        	logger.error(e.getMessage());
        	throw new XmlRpcException(0,e.getMessage());
        	
        } catch (IllegalArgumentException e){
        	logger.error(e.getMessage());
        	throw new XmlRpcException(0,"Illegal argument.");
        }
        
        
   }
   
public Vector getLowestRefObjekt(Integer Id, String objektArt) throws XmlRpcException {
        if (logger.isDebugEnabled()) {  
         logger.debug("getLowestRefObjekt(" + Id + "," + objektArt + ")");
        }        
        // Direkte Referenz        
        ObjektArt art = ObjektArt.get(objektArt);
        
        String sql = "select get_lowest_ref(" + Id + "," + "'" + art.toString() + "')";
        try {
			return getObjekt(SelectUtils.getInt(getPooledConnection(), sql),art.toString().toLowerCase());
		} catch (SQLException e) {
			logger.error(e.getMessage());
        	throw new XmlRpcException(0,e.getMessage());
		}
        
   }
   
   

public Vector getInheritedReferences(Integer obj_id) throws XmlRpcException {
	
        if (logger.isDebugEnabled()) {  
         logger.debug("getInheritedReferences(" + obj_id + ")");
        }
        Connection con = null;
        ResultSet rs = null;
        
        try {    
        // alle parent knoten der Messung bestimmen
        con = getPooledConnection();
        rs = SelectUtils.getResultSet(con, "select get_inherited_parent_ids(" + obj_id + "), get_parent_ids(" + obj_id + ")");
        rs.next(); // gibt immer einen Record zurück
        String inherit_ids = rs.getString(1);
        String parent_ids = rs.getString(2);
        rs.close();
        // vererbte referenzen 
        // objekt.id kann für sprung im Tree verwendet werden
        String sql = "select art_objekt.kurz as type, objekt.de as description, verweis.von, verweis.bis, " +
         "verweis.id as id_verweis, objekt.id as id_von, art_objekt.uname," + 
        " verweis.id_auf from verweis, objekt, art_objekt" + 
        " where objekt.id_art = art_objekt.id and verweis.id_von = objekt.id and" + 
        " id_auf in ("+ inherit_ids + ")" + 
        " union " +
        " select case when o.uname like 'web_ls%' then 'Department'::text when o.uname like 'web_pro%' then 'Project'::text when o.uname like 'web_mit%' then 'Employee'::text end " + 
        ", htmldecode(o.de), v.von, v.bis, v.id, v.id_von, o.uname, v.id_auf from v_web_objekt o,verweis_extern v " + 
        " where v.id_von = o.id and v.id_auf in (" + parent_ids + ")";
        rs = SelectUtils.getResultSet(con,sql);
        Vector vReturn = XmlRpcUtils.resultSetToVector(rs);
        rs.getStatement().close();
        rs.close();
        return vReturn;
        
		} catch (SQLException e) {
	  logger.error(e.getMessage());
    throw new XmlRpcException(0,e.getMessage());        	
		} finally {			
			try {if (!con.isClosed()) con.close();} catch (Exception i) {};
			}
          
     
   }
   

    public Vector getDirectReferences(Integer obj_id) throws XmlRpcException {
        if (logger.isDebugEnabled()) {  
         logger.debug("getDirectReferences(" + obj_id + ")");
        }
        // objekt.id kann für sprung im Tree verwendet werden
        String sql = "select art_objekt.kurz as type, objekt.de as description, verweis.von, verweis.bis, " + 
        " verweis.id as id_verweis, objekt.id as id_von, art_objekt.uname, verweis.id_auf" + 
        " from verweis, objekt, art_objekt " +
        " where" + 
        " verweis.id_von = objekt.id and" +
        " objekt.id_art = art_objekt.id and" +
        " verweis.id_auf = " + obj_id + 
        " union " + 
        " select case when o.uname like 'web_ls%' then 'Department'::text when o.uname like 'web_pro%' then 'Project'::text when o.uname like 'web_mit%' then 'Employee'::text end" +
        ", htmldecode(o.de), ve.von, ve.bis, ve.id, ve.id_von, o.uname, ve.id_auf from v_web_objekt o,verweis_extern ve " + 
        " where ve.id_von = o.id and id_auf = " + obj_id;
        try {
        	return XmlRpcUtils.getRows(getPooledConnection(), sql);
    	} catch (SQLException e) {
    		logger.error(e.getMessage());
    		throw new XmlRpcException(0,e.getMessage());
    	}
   }
    
    
    
    private Integer getRefAuf(Integer id_ref, String art) throws XmlRpcException {
    	String sql;
    	Connection con = null;
        PreparedStatement st = null;
        try {
            
            con = getPooledConnection();
            if (ObjektArt.get(art).isExtern()){
             sql = "select id_auf from verweis_extern where id = ?";
            } else {
             sql = "select id_auf from verweis where id = ?";
            }
            
            st = con.prepareStatement(sql);
            st.setInt(1,id_ref.intValue());
            
            ResultSet res = st.executeQuery();
            if (res.next()) {
            	return res.getInt(1);	
            } else {
            	return null;
            }            
        } catch (SQLException e) {
        	  logger.error(e.getMessage());
              throw new XmlRpcException(0,e.getMessage());        	
        } finally {
           try {st.close();} catch (Exception i) {};
           try {if (!con.isClosed()) con.close();} catch (Exception i) {};
        }    	    	
    	
    }

   
    
    public Boolean deleteReference(Integer id_ref, String art) throws XmlRpcException  {
        String sql;
        if (logger.isDebugEnabled()) {  
         logger.debug("deleteReference(" + id_ref + ")");
        }
                                
        Integer id = getRefAuf(id_ref, art);        
        if (!checkFullAccess(id)) 
            throw new InvalidRightException(0,"Missing rights on " + id + ".");
        
        Connection con = null;
        PreparedStatement st = null;
        try {
            
            con = getPooledConnection();
            if (ObjektArt.get(art).isExtern()){
             sql = "delete from verweis_extern where id = ?";
            } else {
             sql = "delete from verweis where id = ?";
            }
            st = con.prepareStatement(sql);
            st.setInt(1,id_ref.intValue());
            int rows = st.executeUpdate();
            if (rows == 1) {
              return true; 
            } else {
              logger.error("Delete statement deleted more than one row.");
              return false;
            }
        } catch (SQLException e) {
        	  logger.error(e.getMessage());
              throw new XmlRpcException(0,e.getMessage());        	
        } finally {
           try {st.close();} catch (Exception i) {};
           try {if (!con.isClosed()) con.close();} catch (Exception i) {};
        }
        
    }
    
    
    public Boolean updateObjekt(Integer id_obj,String art,Vector attrib) throws XmlRpcException {        
       Connection con = null;
       try {
        if (logger.isDebugEnabled()) {  
         logger.debug("updateObjekt(" + id_obj + "," + art + ")");
        }
        
        if (!checkWrite(id_obj)){
            throw new InvalidRightException(0,"Missing rights on " + id_obj + ".");
       }
        // UTime in Update wird übergeben falls Objekt in der Zwischenzeit verändert wurde
        con = getPooledConnection();
        if (art.equals(ObjektArt.MESSUNG_LABORDATEN.toString()) || art.equals(ObjektArt.MESSUNG_MASSENDATEN.toString())
        || art.equals(ObjektArt.MESSUNG_ORDNER.toString()) ) {
             updateMessungen(con,id_obj.intValue(),attrib);
        } else if (art.equals(ObjektArt.MESS_EINBAU.toString())){
             updateEinbau(con,id_obj.intValue(),attrib);
        } else if (art.equals(ObjektArt.MESS_GERAET.toString())){
             updateGeraet(con,id_obj.intValue(),attrib);
        } else if (art.equals(ObjektArt.MESS_KOMPARTIMENT.toString())){
             updateKompartiment(con,id_obj.intValue(),attrib);
        } else if (art.equals(ObjektArt.MESS_ORT.toString())){
             updateOrt(con,id_obj.intValue(),attrib);
        } else if (art.equals(ObjektArt.MESS_ZIEL.toString())){
             updateZiel(con,id_obj.intValue(),attrib);
        } else if (art.equals(ObjektArt.MESS_EINHEIT.toString())){
             updateEinheit(con,id_obj.intValue(),attrib);
        } else if (art.equals(ObjektArt.DATA_FRAME.toString())){
             updateDataFrame(con,id_obj.intValue(),attrib);
        } else if (art.equals(ObjektArt.DATA_COLUMN.toString())){
             updateDataColumn(con,id_obj.intValue(),attrib);
        } else {
          throw new IllegalArgumentException("Not a valid ObjektArt");
        }
       } catch (SQLException e) {
    	   logger.error(e.getMessage());
    	   throw new XmlRpcException(0, e.getMessage());
       }
       finally {
           try {if (!con.isClosed()) con.close();} catch (Exception i) {logger.error(i.getMessage());};
       }
       return true;
          
    }
    
    private void updateMessungen(Connection con, int id, Vector attrib) throws XmlRpcException{
        PreparedStatement st = null;
        try {
            con.setAutoCommit(false);
            String sql = "UPDATE messungen set bezeichnung = ? , beschreibung = ?, aufloesung = ?, id_intervaltyp = ?, fk_timezone_id = ? WHERE id = ?";                      
            st = con.prepareStatement(sql);
            
            // bezeichunng
            st.setString(1,(String)attrib.elementAt(0));
            
            // beschreibung
            st.setString(2,(String)attrib.elementAt(1));

            // auflösung
            if (attrib.elementAt(2) == null) {
            st.setNull(3,java.sql.Types.INTEGER);    
            } else {
            st.setInt(3,((Integer)attrib.elementAt(2)).intValue());    
            }       
            
            // id_intervaltyp
            st.setInt(4,((Integer)attrib.elementAt(5)).intValue());                
            
            // fk_timezone_id
            if (attrib.elementAt(6) == null) {
                st.setNull(5,java.sql.Types.INTEGER);    
            } else {
                st.setInt(5,((Integer)attrib.elementAt(6)).intValue());    
            }
            
            // id
            st.setInt(6,id);              
            st.executeUpdate();
            st = con.prepareStatement("UPDATE OBJEKT set plan_start = ?, plan_end = ? where id = ?");
            
            if (attrib.elementAt(3) == null) {
                st.setNull(1,java.sql.Types.TIMESTAMP);    
            } else {
                st.setTimestamp(1,new java.sql.Timestamp(((java.util.Date)attrib.elementAt(3)).getTime()));
            }               
            if (attrib.elementAt(4) == null) {
                st.setNull(2,java.sql.Types.TIMESTAMP);    
            } else {
                st.setTimestamp(2,new java.sql.Timestamp(((java.util.Date)attrib.elementAt(4)).getTime()));
            }               
            st.setInt(3,id);              
            st.execute();
            con.commit();
            st.close();          
        } catch (SQLException e){
        	logger.error(e.getMessage());        	
            try {con.rollback();st.close();con.close();} catch (SQLException z){;}
            throw new XmlRpcException(0,e.getMessage());
        }
                            
    }
    
    
     private void updateEinbau(Connection con, int id,Vector attrib) throws SQLException {
        PreparedStatement st = null;
        try {
        String sql = "UPDATE einbau set bezeichnung = ? , hoehe = ? WHERE id = ?";              
        st = con.prepareStatement(sql);
        st.setString(1,(String)attrib.elementAt(0));
        if (attrib.elementAt(1) == null) {
          st.setNull(2,java.sql.Types.FLOAT);    
        } else {
          st.setFloat(2,((Double)attrib.elementAt(1)).floatValue());    
        }       
        st.setInt(3,id);              
        int rows = st.executeUpdate();
        if (rows==0) {
         throw new SQLException("Any rows updated.");
        } else if (rows>1) {
         throw new SQLException("More than one row updated.");
        }
        
        } finally {
           st.close();
        }
        
    }
     
     
     
     public Boolean updateReference(Integer Id, String art , java.util.Date von,java.util.Date bis) throws XmlRpcException {
         PreparedStatement st = null;
         Connection con = null;
         String sql;
         if (logger.isDebugEnabled()) {
             logger.debug("updateReference");
         }
         
         if (!checkWrite(Id)){
             throw new InvalidRightException(0,"Missing rights on " + Id );
         }
         if (ObjektArt.get(art).isExtern()){
             sql = "UPDATE verweis_extern set von = ? , bis = ? WHERE id = ?";
         } else {
             sql = "UPDATE verweis set von = ? , bis = ? WHERE id = ?";
         }

         try {
            con = getPooledConnection();
            st = con.prepareStatement(sql);
            // von
            if (von == null) {
                st.setNull(1,java.sql.Types.TIMESTAMP);
            } else {
                st.setTimestamp(1,new java.sql.Timestamp(von.getTime()));
            }
            // bis
            if (bis == null) {
                st.setNull(2,java.sql.Types.TIMESTAMP);
            } else {
                st.setTimestamp(2,new java.sql.Timestamp(bis.getTime()));
            }
            st.setInt(3,Id.intValue());
            if (st.executeUpdate() != 1) {
              logger.error("Update statement failed.");
              return Boolean.FALSE;
            } else {
              return Boolean.TRUE;
            }
                     
            } catch (SQLException e) {
            	logger.error(e.getMessage());
            	throw new XmlRpcException(0,e.getMessage());
            } finally {
                 try {st.close();} catch (Exception i) {};
                 try {if (!con.isClosed()) con.close();} catch (Exception i) {};
            }             
                      
     }
     
     
    private void updateGeraet(Connection con, int id,Vector attrib) throws SQLException {
        PreparedStatement st = null;
        try {
        String sql = "UPDATE geraete set bezeichnung = ? , beschreibung = ?, seriennr = ? WHERE id = ?";              
        st = con.prepareStatement(sql);
        st.setString(1,(String)attrib.elementAt(0));
        st.setString(2,(String)attrib.elementAt(1));
        st.setString(3,(String)attrib.elementAt(2));
        st.setInt(4,id);              
        int rows = st.executeUpdate();
        if (rows==0) {
         throw new SQLException("Any rows updated.");
        } else if (rows>1) {
         throw new SQLException("More than one row updated.");
        }
        
        } finally {
           st.close();
        }
        
    }
    
    private void updateKompartiment(Connection con, int id,Vector attrib) throws SQLException {
        PreparedStatement st = null;
        try {
        String sql = "UPDATE kompartimente set bezeichnung = ? , beschreibung = ? WHERE id = ?";              
        st = con.prepareStatement(sql);
        st.setString(1,(String)attrib.elementAt(0));
        st.setString(2,(String)attrib.elementAt(1));
        st.setInt(3,id);              
        int rows = st.executeUpdate();
        if (rows==0) {
         throw new SQLException("Any rows updated.");
        } else if (rows>1) {
         throw new SQLException("More than one row updated.");
        }
        
        } finally {
           st.close();
        }
        
    }
    
    
    private void updateOrt(Connection con, int id,Vector attrib) throws SQLException {
        PreparedStatement st = null;
        try {
        String sql = "UPDATE messorte set bezeichnung = ? , beschreibung = ?, x = ?, y = ?, z = ?, fk_crs_id = ? WHERE id = ?";              
        st = con.prepareStatement(sql);
        st.setString(1,(String)attrib.elementAt(0));
        st.setString(2,(String)attrib.elementAt(1));
        
        // X
        if (attrib.elementAt(2) == null) {
          st.setNull(3,java.sql.Types.DOUBLE);    
        } else {
          st.setDouble(3,((Double)attrib.elementAt(2)).doubleValue());    
        }       
        
        // Y
        if (attrib.elementAt(3) == null) {
          st.setNull(4,java.sql.Types.DOUBLE);    
        } else {
          st.setDouble(4,((Double)attrib.elementAt(3)).doubleValue());    
        }       
        
        // Z
        if (attrib.elementAt(4) == null) {
          st.setNull(5,java.sql.Types.DOUBLE);    
        } else {
          st.setDouble(5,((Double)attrib.elementAt(4)).doubleValue());    
        }       
        
        // CRS
        if (attrib.elementAt(5) == null) {
          st.setNull(6,java.sql.Types.INTEGER);    
        } else {
          st.setInt(6,((Integer)attrib.elementAt(5)).intValue());    
        }       
        
        
        st.setInt(7,id);              
        int rows = st.executeUpdate();
        if (rows==0) {
         throw new SQLException("Any rows updated.");
        } else if (rows>1) {
         throw new SQLException("More than one row updated.");
        }
        
        } finally {
           st.close();
        }
        
    }
    
    
    private void updateZiel(Connection con, int id,Vector attrib) throws SQLException {
        PreparedStatement st = null;
        try {
        String sql = "UPDATE messziele set bezeichnung = ? , beschreibung = ?, formel = ? WHERE id = ?";              
        st = con.prepareStatement(sql);
        st.setString(1,(String)attrib.elementAt(0));
        st.setString(2,(String)attrib.elementAt(1));
        st.setString(3,(String)attrib.elementAt(2));
        st.setInt(4,id);              
        int rows = st.executeUpdate();
        if (rows==0) {
         throw new SQLException("Any rows updated.");
        } else if (rows>1) {
         throw new SQLException("More than one row updated.");
        }
        
        } finally {
           st.close();
        }
        
        
    }
    
    
    private void updateEinheit(Connection con, int id,Vector attrib) throws SQLException {
        PreparedStatement st = null;
        try {
        String sql = "UPDATE einheiten set bezeichnung = ? , beschreibung = ?, symbol = ? WHERE id = ?";              
        st = con.prepareStatement(sql);
        st.setString(1,(String)attrib.elementAt(0));
        st.setString(2,(String)attrib.elementAt(1));
        st.setString(3,(String)attrib.elementAt(2));
        st.setInt(4,id);              
        int rows = st.executeUpdate();
        if (rows==0) {
         throw new SQLException("Any rows updated.");
        } else if (rows>1) {
         throw new SQLException("More than one row updated.");
        }
        
        } finally {
           st.close();
        }
        
    }
    
    
    /** 
     * 
     * @param con
     * @param id
     * @param attrib 
     * 					0:plan_start 
     * 					1:plan_end
     * 					2:rec_start 
     * 					3:rec_end 
     * 					4:bezeichnung 
     * 					5:beschreibung 
     * 					6:col_index
     * 					7:data_type
     * @throws SQLException
     */
    private void updateDataColumn(Connection con, int id,Vector attrib) throws SQLException {
        PreparedStatement st = null;                
        try {
        con.setAutoCommit(false);
        updateObjektAttributes(con, id, attrib);	
        String sql = "UPDATE data_column set bezeichnung = ? , beschreibung = ?, col_index = ? , data_type = ? WHERE id = ?";              
        st = con.prepareStatement(sql);        
        if (attrib.get(4)==null){
        	st.setNull(1,java.sql.Types.VARCHAR);
        } else {
        	st.setString(1,(String)attrib.get(4));
        }        
        if (attrib.get(5)==null){
        	st.setNull(2,java.sql.Types.VARCHAR);
        } else {
        	st.setString(2,(String)attrib.get(5));
        }                
        st.setInt(3,(Integer)attrib.get(6));
        st.setString(4,(String)attrib.get(7));        
        st.setInt(5,id);
        st.executeUpdate();               
        con.commit();
        } catch (SQLException e){
        	con.rollback();
        	logger.error(e.getMessage());
        	throw new SQLException(e.getCause());       
        } finally {
           st.close();
        }
        
    }
    
    
    /** 
     * 
     * @param con
     * @param id
     * @param attrib 
     * 					0:plan_start 
     * 					1:plan_end
     * 					2:rec_start 
     * 					3:rec_end 
     * 					4:bezeichnung 
     * 					5:beschreibung 
     * 					6:timezone_id
     * @throws SQLException
     */
    private void updateDataFrame(Connection con, int id,Vector attrib) throws SQLException {
    	logger.debug("updateDataFrame()");
        PreparedStatement st = null;
        try {
        con.setAutoCommit(false);        
        updateObjektAttributes(con,id,attrib);                        
        String sql = "UPDATE data_frame set bezeichnung = ?, beschreibung = ?, timezone_id = ? WHERE id = ?";              
        st = con.prepareStatement(sql);                  
        if (attrib.get(4)==null){
        	st.setNull(1,java.sql.Types.VARCHAR);
        } else {
        	st.setString(1,(String)attrib.get(4));
        }        
        if (attrib.get(5)==null){
        	st.setNull(2,java.sql.Types.VARCHAR);
        } else {
        	st.setString(2,(String)attrib.get(5));
        }                
        if (attrib.get(6)==null){
        	st.setNull(3,java.sql.Types.INTEGER);
        } else {
        	st.setInt(3,(Integer)attrib.get(6));
        }                                             
        st.setInt(4,id);
        st.executeUpdate();        
        con.commit();                                  
        
        } catch (SQLException e){
        	con.rollback();
        	logger.error(e.getMessage());
        	throw new SQLException(e.getCause());       
        } finally {
           st.close();
        }   
    }
    
    
    private void updateObjektAttributes(Connection con, Integer id, Vector attrib) throws SQLException {
    	logger.debug("updateObjektAttributes(" + con + ";" + id + ";" + attrib);
    	PreparedStatement st = null;
    	try {
    	                
        String sql = "UPDATE objekt set plan_start = ?, plan_end = ?, rec_start = ?, rec_end = ? WHERE id = ?";
        st = con.prepareStatement(sql);

        if (attrib.get(0)==null){
        	st.setNull(1,java.sql.Types.TIMESTAMP);
        } else {        	
        	st.setTimestamp(1,new Timestamp( ((Date)attrib.get(0)).getTime()));        	
        }        
        if (attrib.get(1)==null){
        	st.setNull(2,java.sql.Types.TIMESTAMP);
        } else {
        	st.setTimestamp(2,new Timestamp( ((Date)attrib.get(1)).getTime()));        	
        }        
        if (attrib.get(2)==null){
        	st.setNull(3,java.sql.Types.TIMESTAMP);
        } else {
        	st.setTimestamp(3,new Timestamp( ((Date)attrib.get(2)).getTime()));        	
        }        
        if (attrib.get(3)==null){
        	st.setNull(4,java.sql.Types.TIMESTAMP);
        } else {
        	st.setTimestamp(4,new Timestamp( ((Date)attrib.get(3)).getTime()));        	
        }        
        st.setInt(5,id);
        st.executeUpdate();
        
    	} finally {
    		if (st!=null) {
    			st.close();
    		}
    	}
    }
    
    
    /* (non-Javadoc)
	 * @see IObjektHandler#getExternUrl(java.lang.Integer, java.lang.String)
	 */
    /* (non-Javadoc)
	 * @see IObjektHandler#getExternUrl(java.lang.Integer, java.lang.String)
	 */
    public String getExternUrl(Integer id, String art) throws XmlRpcException {
        String sql ;
        ObjektArt o  = ObjektArt.get(art);        
        if (o.isExtern()) {
             sql = "select link from objekt_extern where id = " + id;
        } else {
            throw new XmlRpcException(0,"No urls available for ObjektArt:" + art);
        }        
        
        try {
			return SelectUtils.getString(getPooledConnection(), sql);
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		}
    }
     

    
    
    
}

