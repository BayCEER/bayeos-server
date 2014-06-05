package de.unibayreuth.bayceer.bayeos.xmlrpc.handler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.objekt.DataType;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IDataFrame;

public class DataFrameHandler extends AccessHandler implements IDataFrame {

	private static final int batchSize = 1000;	
	
	@Override
	public Vector getFrameRows(Integer frameId, Vector rowIndex) throws XmlRpcException {
		logger.debug("Get frameRows of " + frameId);
				

		Vector ret = new Vector(2);		
		if (frameId == null)
			throw new XmlRpcException(1, "Invalid values argument");
		if (!checkRead(frameId)) {
			throw new XmlRpcException(1, "Missing rights on Objekt " + frameId
					+ ".");
		}
		
		
		Vector meta = getColumnsMeta(frameId); 		
		if (meta.size() > 0) {;
			ret.add(meta);
			ret.add(getRowMatrix(frameId, meta, rowIndex));
		}
		return ret;
	}
	
	@Override
	public Boolean writeColValues(Integer colId, Vector values)
			throws XmlRpcException {
		logger.debug("Write column values for " + colId);

		if (values == null || colId == null)
			throw new XmlRpcException(1, "Invalid values argument");
		
		Connection con = null;
		PreparedStatement pstmt = null;

		if (!checkWrite(colId)) {
			throw new XmlRpcException(1, "Missing rights on Objekt " + colId
					+ ".");
		}
		

		try {
			
			DataType dataType = getDataType(colId);
			con = getPooledConnection();
			pstmt = con.prepareStatement("insert into data_value(data_column_id, row_index, value) values (?,?,?::text)");
			pstmt.setInt(1, colId);
			for (int i = 0; i < values.size(); i++) {
				pstmt.setInt(2, i + 1);
				switch (dataType) {
				case STRING:
					pstmt.setString(3, (String) values.get(i));
					break;
				case BOOLEAN:
					pstmt.setBoolean(3, (Boolean) values.get(i));
					break;
				case DATE:
					pstmt.setTimestamp(3,new java.sql.Timestamp(((Date) values.get(i)).getTime()));
					break;
				case DOUBLE:
					pstmt.setDouble(3, (Double) values.get(i));
					break;
				case INTEGER:
					pstmt.setDouble(3, (Integer) values.get(i));
					break;
				default:
					throw new XmlRpcException(0, "Data type not supported");
				}
				pstmt.addBatch();
				if (i % batchSize == 0) { pstmt.executeBatch(); }
				
			}
			pstmt.executeBatch();
			pstmt.close();

			logger.debug("Finished insert of data frame column");
			return true;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return false;
		} finally {
			try {
				if (pstmt != null)
					pstmt.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
			try {
				if (con != null)
					con.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
		}
	}
	
	@Override
	public Boolean deleteColValues(Integer colId) throws XmlRpcException {
		logger.debug("Delete column values for " + colId);

		if (colId == null){
			throw new XmlRpcException(1, "Invalid argument");
		}	
		
		if (!checkWrite((Integer)colId)){
			throw new XmlRpcException(1, "Missing rights on Objekt: " + colId + ".");
		}
		
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = getPooledConnection();
			pstmt = con.prepareStatement("delete from data_value where data_column_id = ?");
			pstmt.setInt(1, colId);
			pstmt.execute();
			return true;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return false;
		} finally {
			if (pstmt != null)
				try {
					pstmt.close();
				} catch (SQLException e) {
				}
			;
			if (con != null)
				try {
					con.close();
				} catch (SQLException e) {
				}
			;
		}

	}
	
	
	@Override
	public Vector getColumnRows(Vector colIds, Vector rowIndexes) throws XmlRpcException {
		logger.debug("getColumnRows:" + colIds + ";" + rowIndexes);
		if (colIds == null) throw new XmlRpcException(0, "Invalid argument for colIds");
		if (checkRead(colIds)==false){
			throw new XmlRpcException(0, "Missing rights");
		}
		Vector ret = new Vector(2);
		Vector meta = getColumnsMeta(colIds); 		
		if (meta.size() > 0) {
			ret.add(meta);
			ret.add(getRowMatrix(colIds, meta, rowIndexes));
		}
		return ret;				
	}

	@Override
	public Boolean updateColValues(Integer colId, Vector rowIndexes, Vector values) throws XmlRpcException {
		logger.debug("updateColValues(" + colId + ";" + rowIndexes + ";" + values + ")");

		if (rowIndexes == null || values == null || colId == null) {
			throw new XmlRpcException(0, "Invalid arguments, null values are not allowed.");
		}	
		
		if (rowIndexes.size()!=values.size()){
			throw new XmlRpcException(0, "Invalid arguments, indexes length != values length.");
		}
	
		if (checkWrite(colId)==false){
			throw new XmlRpcException(0, "Missing rights on column " + colId);
		}
						
		Connection con = null;
		PreparedStatement pInsert = null;
		PreparedStatement pUpdate = null;
		
	    try{
			con = getPooledConnection();
			pInsert = con.prepareStatement("insert into data_value (value, data_column_id,row_index) values (?::text,?,?)");			
			pUpdate = con.prepareStatement("update data_value set value = ?::text where data_column_id = ? and row_index = ?");
				
			pInsert.setInt(2, colId);
			pUpdate.setInt(2, colId);	
			
			for(int i=0;i<rowIndexes.size();i++){				 
					DataType dataType = getDataType(colId);
					switch (dataType) {
					case STRING:
						String v = (String) values.get(i);
						pInsert.setString(1,v);
						pUpdate.setString(1,v);
						break;
					case BOOLEAN:
						Boolean b = (Boolean) values.get(i);
						pInsert.setBoolean(1, b);
						pUpdate.setBoolean(1, b);
						break;
					case DATE:
						Timestamp t = new java.sql.Timestamp(((Date) values.get(i)).getTime());
						pInsert.setTimestamp(1,t);
						pUpdate.setTimestamp(1,t);
						break;
					case DOUBLE:
						Double d = (Double) values.get(i);
						pInsert.setDouble(1,d);
						pUpdate.setDouble(1,d);
						break;
					case INTEGER:
						Integer I = (Integer) values.get(i);
						pInsert.setDouble(1,I);
						pUpdate.setDouble(1,I);
						break;
					default:
						throw new XmlRpcException(0, "Data type not supported");
					}
					
								
					pInsert.setInt(3, (int) rowIndexes.get(i));
					pUpdate.setInt(3, (int) rowIndexes.get(i));
					
					int rows = pUpdate.executeUpdate();					
					if (rows == 0){						
						pInsert.execute();
					}					
				
			}
			
			
			
			
				
	   } catch (SQLException e) {
			logger.error(e.getMessage());
			 return false;
		} finally {
			try {
				if (pInsert != null)	pInsert.close();
			} catch (SQLException e) {	
				logger.error(e);
			};
			try {
				if (pUpdate != null)	pUpdate.close();
			} catch (SQLException e) {	
				logger.error(e);
			};
			
			try {
				if (con != null) con.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;		
		}
		
		return true;
	}

	@Override
	public Integer getMaxRowIndex(Vector colIds) throws XmlRpcException {
		logger.debug("getMaxRowIndex(" + colIds + ")");

		if (checkRead(colIds)==false){
			throw new XmlRpcException(0, "Missing rights");
		}		
		String in = SelectUtils.getInInteger(colIds);
		try {
			String sql =  String.format("select max(row_index) from data_value where data_column_id in %s",in);
			if (logger.isDebugEnabled()) logger.debug(sql);
			return SelectUtils.getInt(getPooledConnection(),sql);
		} catch (SQLException e) {
			logger.error(e);
			return null;
		}
	}
	
	
	private Vector getRowMatrix(Vector colIds, Vector colsMeta, Vector rowIndexes) throws XmlRpcException {
		logger.debug("getRowMatrix(" + colIds + ";" + colsMeta + ";" + rowIndexes + ")");
		Vector ret = new Vector();				
		
		Connection con = null;
		Statement stmt = null;
		try {
			con = getPooledConnection();			
			
			StringBuffer col = new StringBuffer(100);
			StringBuffer rec = new StringBuffer(100);
			
			Integer id;
			String type;
			
			for(Object o:colsMeta){				
				 // id, col_index, bezeichnung, data_type	
				id = (Integer)((Vector)o).get(0);
				type = (String) ((Vector)o).get(3);
				// "cr.c1::text, cr.c2::integer ..."
				col.append(String.format(", cr.c%d::%s",id,getSQLString(DataType.valueOf(type))));
			}
			
			// Sorted by id asc  
			// "c1 text, c2 text ...."			
			Collections.sort(colIds);			
			for(Object o:colIds){
				id = (Integer)o;
				rec.append(String.format(", c%d text",o));	
			}
																				
			String colString = SelectUtils.getInInteger(colIds);					
			String sql;
			if (rowIndexes == null || rowIndexes.size()==0){
				sql = String.format("select cr.row %s from (select * from crosstab('select v.row_index, c.id, v.value from data_column c, data_value v " + 
					" where c.id in %s and c.id = v.data_column_id order by 1,2'," +
					"'select c.id from data_column c where c.id in %s order by 1') as ct(row integer %s)) cr;",col.toString(),colString,colString,rec.toString());
			} else {
				String rowIndex = SelectUtils.getInInteger(rowIndexes);
				sql = String.format("select cr.row %s from (select * from crosstab('select v.row_index, c.id, v.value from data_column c, data_value v " + 
					" where c.id in %s and c.id = v.data_column_id and v.row_index in %s order by 1,2'," +
					"'select c.id from data_column c where c.id in %s order by 1') as ct(row integer %s)) cr;",col.toString(),colString,rowIndex,colString,rec.toString());				
			}
						
			if (logger.isDebugEnabled()){
				logger.debug(sql);
			}
			stmt = con.createStatement();								
			ResultSet rs = stmt.executeQuery(sql);			
			ret = XmlRpcUtils.resultSetToVector(rs);
			rs.close();
			return ret;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return null;
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
			try {
				if (con != null)
					con.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
		}
		
		
	}
	

	
	private Vector getRowMatrix(Integer frameId, Vector colsMeta, Vector rowIndexes) throws XmlRpcException {
		logger.debug("getRowMatrix(" + frameId + ";" + colsMeta + ";" + rowIndexes + ")");
		Vector ret = new Vector();				
				
		Connection con = null;
		Statement stmt = null;
		try {
			con = getPooledConnection();			
			
			StringBuffer col = new StringBuffer(50);
			StringBuffer rec = new StringBuffer(50);
			
			for(Object o:colsMeta){				
				 // id, col_index, bezeichnung, data_type				
				Integer index = (Integer) ((Vector)o).get(1);
				String type = (String) ((Vector)o).get(3);
				// "cr.c1::text, cr.c2::integer ..."
				col.append(String.format(", cr.c%d::%s",index,getSQLString(DataType.valueOf(type))));
				// "c1 text, c2 text ...."
				rec.append(String.format(", c%d text",index));								
			}
			
			String sql;
			if (rowIndexes == null || rowIndexes.size()==0){
				sql = String.format("select cr.row %s from (select * from crosstab('select v.row_index, c.col_index, v.value from data_column c, data_value v, objekt o " + 
						"where c.id = v.data_column_id and o.id_super = %d and o.id = c.id order by 1,2','select c.col_index from data_column c, objekt o where o.id = c.id and o.id_super = %d order by 1') " + 
						" as ct(row integer %s)) cr;",col.toString(),frameId,frameId,rec.toString());	
			} else {
				String rowIndex = SelectUtils.getInInteger(rowIndexes);
				sql = String.format("select cr.row %s from (select * from crosstab('select v.row_index, c.col_index, v.value from data_column c, data_value v, objekt o " + 
						"where c.id = v.data_column_id and o.id_super = %d and o.id = c.id and v.row_index in %s order by 1,2','select c.col_index from data_column c, objekt o where o.id = c.id and o.id_super = %d order by 1') " + 
						" as ct(row integer %s)) cr;",col.toString(),frameId,rowIndex,frameId,rec.toString());
			}
			if (logger.isDebugEnabled()){
				logger.debug(sql);
			}
			stmt = con.createStatement();								
			ResultSet rs = stmt.executeQuery(sql);			
			ret = XmlRpcUtils.resultSetToVector(rs);
			rs.close();
			return ret;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return null;
		} finally {
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
			try {
				if (con != null)
					con.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
		}
		
	}
	
	private String getSQLString(DataType e){		
		switch (e) {
			case STRING: return "text";
			case INTEGER: return "integer";
			case DOUBLE: return "double precision";
			case DATE: return "timestamp with time zone";
			case BOOLEAN: return "boolean";
			default: return "";
		}				
	}


	private DataType getDataType(Integer colId){
		Connection con = null;
		PreparedStatement pstmt = null;
		try{
			con = getPooledConnection();
			pstmt = con.prepareStatement("select data_type from data_column where id=?");
			pstmt.setInt(1, colId);
			ResultSet r = pstmt.executeQuery();
			r.next();
			DataType dataType = DataType.valueOf(r.getString(1));
			pstmt.close();
			return dataType;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return null;
		} finally {
			if (pstmt != null)
				try {
					pstmt.close();
				} catch (SQLException e) {
				}
			;
			if (con != null)
				try {
					con.close();
				} catch (SQLException e) {
				}
			;
		}
	}
	
	
	private Vector getColumMeta(Integer colId) throws XmlRpcException  {	
		logger.debug("getColumMeta(" + colId + ")" );
		Vector ret = new Vector(4);	
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = getPooledConnection();
			pstmt = con.prepareStatement("select col_index, bezeichnung, data_type from data_column where id = ?");
			pstmt.setInt(1, colId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()){
				ret.add(colId);
				ret.add(rs.getInt(1));
				ret.add(rs.getString(2));
				ret.add(rs.getString(3));				
			} else {
				throw new XmlRpcException(0, "Data column:" + colId + " not found.");
			}
			return ret;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return null;
		} finally {
			try {
				if (pstmt != null)
					pstmt.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
			try {
				if (con != null)
					con.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
		}						
		
				
	}
	
	
	
	private Vector getColumnsMeta(Vector colIds) throws XmlRpcException {
		Vector ret = new Vector(colIds.size());
		for(int i=0;i<colIds.size();i++){
			Vector meta = getColumMeta((Integer) colIds.get(i));
			meta.set(1, i+1); // index is dynamic for combined columns
			ret.add(meta);
		}
		return ret;
	}
	
	private Vector getColumnsMeta(Integer frameId) throws XmlRpcException {
		Vector ret = new Vector();	
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = getPooledConnection();
			pstmt = con.prepareStatement("select c.id, c.col_index, c.bezeichnung, c.data_type from objekt o, data_column c where o.id_super = ? and o.id = c.id order by 2");
			pstmt.setInt(1, frameId);
			ResultSet rs = pstmt.executeQuery();			
			while(rs.next()){
				Vector r = new Vector(4);				
				if (!checkRead(rs.getInt(1))) {
					throw new XmlRpcException(1, "Missing rights on DataColumn: " + rs.getInt(1)
							+ ".");
				}				
				r.add(rs.getInt(1));
				r.add(rs.getInt(2));
				r.add(rs.getString(3));
				r.add(rs.getString(4));
				ret.add(r);
			}			
			return ret;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return null;
		} finally {
			try {
				if (pstmt != null)
					pstmt.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
			try {
				if (con != null)
					con.close();
			} catch (SQLException e) {
				logger.error(e);
			}
			;
		}						
	}

	
	public Integer returnVecCount(Vector r){
		return r.size();
		
	}

}
