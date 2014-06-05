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
 * MassenTableHandler.java
 *
 * Created on 28. August 2002, 13:47
 */

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ByteUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.GroupBy;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InsertMassendaten;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRightException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRowStringException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.MatrixUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.StatusFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.TimeFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.formats.DateFormat;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IMassenTableHandler;

public class MassenTableHandler extends AccessHandler implements
		IMassenTableHandler {

	final static Logger logger = Logger.getLogger(MassenTableHandler.class);
	
	
	public Boolean upsertByteRows(byte[] b) throws XmlRpcException {			
		return insertByteRows(b, true);
	}
	
	
	private Boolean insertByteRows(byte[] b, boolean overwrite) throws XmlRpcException {
		ByteArrayInputStream bi = null;
		DataInputStream di = null;
		Connection con = null;
		try {
			bi = new ByteArrayInputStream(b);
			di = new DataInputStream(bi);

			con = getPooledConnection();
			con.setAutoCommit(false);
			InsertMassendaten inserter = new InsertMassendaten(con);
			int id = 0;
			
			while (di.available() > 0) {
				id = di.readInt();
				Timestamp t = new Timestamp(di.readLong());
				Float w = di.readFloat();
				if (!inserter.isKeyInserted(id))
					checkWrite(id);
				if (overwrite){
					inserter.upsert(id,t,w);
				} else {
					inserter.insert(id,t,w);
				}					
			}
			inserter.updateMinMax();
			con.commit();
			logger.info(inserter.getRowsInserted() + " records imported.");
			return Boolean.TRUE;

		} catch (SQLException e) {
			logger.error(e.getMessage());
			try {
				con.rollback();
			} catch (SQLException e1) {
				logger.error(e.getMessage());
			}
			throw new XmlRpcException(1, "Failed to add rows.");
		} catch (IOException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(2, "Failed to add rows.");
		} catch (InvalidRightException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(3, "Failed to add rows.");
		} finally {
			try {
				di.close();
				bi.close();
				con.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		}

	}
	
	
	

	public Boolean addByteRows(byte[] b) throws XmlRpcException {
		return insertByteRows(b,false);
	}

	public Boolean addRow(Integer id, java.util.Date von, Double wert,
			Integer status) throws XmlRpcException {
		Connection con = null;
		if (!checkWrite(id)) {
			throw new XmlRpcException(1, "Missing rights on Objekt " + id + ".");
		}

		if (von == null)
			throw new XmlRpcException(1, "Missing von value.");
		if (wert == null)
			throw new XmlRpcException(1, "Missing wert value.");

		try {
			con = getPooledConnection();
			con.setAutoCommit(false);
			InsertMassendaten inserter = new InsertMassendaten(con);
			inserter.insert(id, new java.sql.Timestamp(von.getTime()),
					new Short(status.shortValue()),
					new Float(wert.floatValue()));
			inserter.updateMinMax();
			con.commit();

		} catch (SQLException s) {
			logger.error(s.getMessage());
			try {
				con.rollback();
			} catch (SQLException dummy) {
			}
			;
			throw new XmlRpcException(0, s.getMessage());
		} finally {
			try {
				con.close();
			} catch (SQLException dummy) {
			}
			;
		}

		return Boolean.TRUE;
	}

	public Boolean updateRow(Integer id, java.util.Date old_von,
			Integer statId, Double wert, java.util.Date new_von)
			throws XmlRpcException {
		PreparedStatement pst = null;
		Connection con = null;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("updateRow(" + id + "," + old_von + "," + statId + "," + wert + ")");
			}
			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id	+ ".");
			}

			con = getPooledConnection();
			String sql = "update massendaten set status = ? , wert = ?, von = ? where von = ? and id = ?";
			pst = con.prepareStatement(sql);
			pst.setShort(1, statId.shortValue());
			pst.setFloat(2, wert.floatValue());
			pst.setTimestamp(3, new java.sql.Timestamp(new_von.getTime()));
			pst.setTimestamp(4, new java.sql.Timestamp(old_von.getTime()));
			pst.setInt(5, id.intValue());
			pst.executeUpdate();
			return true;
		} catch (SQLException s) {
			logger.error(s.getMessage());
			throw new XmlRpcException(0, s.getMessage());
		} finally {
			try {
				con.close();
				pst.close();
			} catch (SQLException dummy) {
			}
			;
		}
	}

	public Boolean updateRows(Integer id, Vector vonDates, Integer statId)
			throws XmlRpcException {
		// Update Batch testen !
		PreparedStatement pst = null;
		Connection con = null;
		try {
			// create prepared statement
			if (logger.isDebugEnabled()) {
				logger.debug("updateRows(" + id + "," + statId + ")");
			}

			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id
						+ ".");
			}
			con = getPooledConnection();
			String sql = "update massendaten set status = ? where von = ? and id = ?";
			pst = con.prepareStatement(sql);
			pst.setShort(1, statId.shortValue());
			pst.setInt(3, id.intValue());
			Iterator it = vonDates.iterator();
			con.setAutoCommit(false);
			while (it.hasNext()) {
				java.util.Date vonDate = (java.util.Date) it.next();
				pst.setTimestamp(2, new java.sql.Timestamp(vonDate.getTime()));
				pst.executeUpdate();
			}
			con.commit();
			return true;
		} catch (SQLException s) {
			logger.error(s.getMessage());
			try {
				con.rollback();
			} catch (SQLException dummy) {
			}
			;
			throw new XmlRpcException(0, s.getMessage());
		} finally {
			try {
				con.close();
				pst.close();
			} catch (SQLException dummy) {
			}
			;
		}
	}

	public Boolean removeRows(Integer id, Vector vonDates)
			throws XmlRpcException {
		PreparedStatement pst = null;
		Connection con = null;
		try {
			// create prepared statement
			if (logger.isDebugEnabled()) {
				logger.debug("deleteRows(" + id + " )");
			}

			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id
						+ ".");
			}

			con = getPooledConnection();
			pst = con
					.prepareStatement("delete from massendaten where von = ? and id = ?");
			pst.setInt(2, id.intValue());
			Iterator it = vonDates.iterator();
			con.setAutoCommit(false);
			while (it.hasNext()) {
				java.util.Date vonDate = (java.util.Date) it.next();
				java.sql.Timestamp sqlvon = new java.sql.Timestamp(
						vonDate.getTime());
				pst.setTimestamp(1, sqlvon);
				pst.executeUpdate();
			}
			con.commit();
			return true;
		} catch (SQLException s) {
			logger.error(s.getMessage());
			try {
				con.rollback();
			} catch (SQLException dummy) {
			}
			;
			throw new XmlRpcException(0, s.getMessage());
		} finally {
			try {
				con.close();
				pst.close();
			} catch (SQLException dummy) {
			}
			;
		}
	}

	public Vector getRows(Integer id, Vector timeFilter, Vector statusFilter)
			throws XmlRpcException {
		PreparedStatement pst = null;
		Connection con = null;
		ResultSet rs = null;
		Vector vReturn = null;
		try {

			if (logger.isDebugEnabled()) {
				logger.debug("getRows(" + id + ")");
			}

			if (!checkRead(id)) {
				throw new XmlRpcException(1,
						"There is no series identified by id: " + id);
			}

			TimeFilter tFilter = new TimeFilter(timeFilter);
			StatusFilter sFilter = new StatusFilter(statusFilter);

			con = getPooledConnection();
			String query = "select von, wert, status "
					+ " from massendaten where id = ? and von >= ? and von <= ? and status in "
					+ SelectUtils.getInInteger(sFilter.getIds())
					+ " order by von asc";
			if (logger.isDebugEnabled()) {
				logger.debug("Query:" + query);
			}

			pst = con.prepareStatement(query);
			pst.setInt(1, id.intValue());
			pst.setTimestamp(2, new java.sql.Timestamp(tFilter.getVon()
					.getTime()));
			pst.setTimestamp(3, new java.sql.Timestamp(tFilter.getBis()
					.getTime()));

			rs = pst.executeQuery();
			vReturn = new Vector();
			vReturn.add(XmlRpcUtils.getMetaData(rs));
			vReturn.add(MatrixUtils.getShortMassendaten(rs));
			return vReturn;
		} catch (SQLException e) {
			logger.error("Query Error:" + e.getMessage());
			throw new XmlRpcException(1, e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(1, e.getMessage());
		} finally {
			try {
				pst.close();
				con.close();
				rs.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		}

	}

	public Boolean importById(Hashtable values) throws XmlRpcException {
		return importGrouped(values, GroupBy.Id);
	}

	public Boolean importByTime(Hashtable values) throws XmlRpcException {
		return importGrouped(values, GroupBy.Time);
	}

	private Boolean importGrouped(Hashtable hashTable, GroupBy group)
			throws XmlRpcException {
		Vector rows = null;
		Connection con = null;
		String key = null;
		List cols = null;
		try {
			con = getPooledConnection();
			con.setAutoCommit(false);
			InsertMassendaten inserter = new InsertMassendaten(con);
			try {
				Enumeration keys = hashTable.keys();
				while (keys.hasMoreElements()) {
					key = (String) keys.nextElement();
					rows = (Vector) hashTable.get(key);
					Iterator it = rows.iterator();
					while (it.hasNext()) {
						cols = getCols((String) it.next(), key, group);
						Integer id = (Integer) cols.get(0);
						if (!inserter.isKeyInserted(id))
							checkWrite(id);
						inserter.insert(id, (java.sql.Timestamp) cols.get(1),
								(Short) cols.get(2), (Float) cols.get(3));
					}
					logger.info(inserter.getRowsInserted()
							+ " records imported for key " + key + ".");
					inserter.setRowsInserted(0);
				}
				inserter.updateMinMax();
				logger.debug(inserter.keysUpdated()
						+ " meta information updated.");

			} catch (SQLException e) {
				con.rollback();
				String msg = e.getMessage() + " Row:" + cols
						+ ". Transaction rolled back.";
				logger.error(msg);
				throw new XmlRpcException(0, msg);
			} catch (InvalidRowStringException i) {
				con.rollback();
				String msg = i.getMessage() + " Row:" + cols
						+ ". Transaction rolled back.";
				logger.error(msg);
				throw new XmlRpcException(0, msg);
			} catch (InvalidRightException a) {
				con.rollback();
				String msg = a.getMessage() + " Row:" + cols
						+ ". Transaction rolled back.";
				logger.error(msg);
				throw new XmlRpcException(0, msg);
			} finally {
				con.commit();
				con.setAutoCommit(true);
				con.close();
			}
			return Boolean.TRUE;
		} catch (SQLException i) {
			String msg = i.getMessage();
			logger.error(msg);
			try {
				con.close();
			} catch (SQLException z) {
				;
			}
			throw new XmlRpcException(0, msg);
		}

	}

	private Vector getCols(String row, String key, GroupBy group)
			throws InvalidRowStringException {
		try {
			Vector ret = new Vector(3);
			StringTokenizer st = new StringTokenizer(row, ";");
			if (st.countTokens() != 3) {
				throw new InvalidRowStringException("Illegal tokens in string "
						+ row, null);
			}
			if (group.equals(GroupBy.Id)) {
				ret.add(Integer.valueOf(key));
				ret.add(getTimestamp(st.nextToken()));
			} else if (group.equals(GroupBy.Time)) {
				ret.add(Integer.valueOf(st.nextToken()));
				ret.add(getTimestamp(key));
			} else {
				throw new IllegalArgumentException("Group type "
						+ group.toString() + " unknown.");
			}
			ret.add(Short.valueOf(st.nextToken()));
			ret.add(Float.valueOf(st.nextToken()));
			return ret;
		} catch (ParseException p) {
			throw new InvalidRowStringException("ParseException Row: " + row, p);
		} catch (NumberFormatException f) {
			throw new InvalidRowStringException("NumberFormatException Row: "
					+ row, f);
		}
	}

	private Vector getAllCols(String row) throws InvalidRowStringException {
		try {
			Vector ret = new Vector(3);
			StringTokenizer st = new StringTokenizer(row, ";");
			if (st.countTokens() != 3) {
				throw new InvalidRowStringException("Illegal tokens in string "
						+ row, null);
			}
			ret.add(getTimestamp(st.nextToken()));
			ret.add(Short.valueOf(st.nextToken()));
			ret.add(Float.valueOf(st.nextToken()));
			return ret;
		} catch (ParseException p) {
			throw new InvalidRowStringException(
					p.getMessage() + " Row: " + row, p);
		} catch (NumberFormatException f) {
			throw new InvalidRowStringException(
					f.getMessage() + " Row: " + row, f);
		}
	}

	private Vector getShortCols(String row) throws InvalidRowStringException {
		try {
			Vector ret = new Vector(2);
			StringTokenizer st = new StringTokenizer(row, ";");
			if (st.countTokens() != 2) {
				throw new InvalidRowStringException("Illegal tokens in string "
						+ row, null);
			}
			ret.add(getTimestamp(st.nextToken()));
			ret.add(Float.valueOf(st.nextToken()));
			return ret;
		} catch (ParseException p) {
			throw new InvalidRowStringException(
					p.getMessage() + " Row: " + row, p);
		} catch (NumberFormatException f) {
			throw new InvalidRowStringException(
					f.getMessage() + " Row: " + row, f);
		}
	}

	private static Timestamp getTimestamp(String value) throws ParseException {
		return new Timestamp(DateFormat.importDateFormat.parse(value).getTime());
	}

	public Boolean importFlat(Integer Id, Vector rows, boolean shortCols)
			throws XmlRpcException {
		Connection con = null;
		Vector cols = null;
		try {
			logger.info("Importing " + rows.size() + " rows with id:" + Id
					+ " into table massendaten.");
			con = getPooledConnection();
			con.setAutoCommit(false);
			InsertMassendaten inserter = new InsertMassendaten(con);
			try {
				Iterator it = rows.iterator();
				while (it.hasNext()) {
					if (!inserter.isKeyInserted(Id))
						checkWrite(Id);
					if (shortCols) {
						cols = getShortCols((String) it.next());
						inserter.insert(Id, (java.sql.Timestamp) cols.get(0),
								(Float) cols.get(1));
					} else {
						cols = getAllCols((String) it.next());
						inserter.insert(Id, (java.sql.Timestamp) cols.get(0),
								(Short) cols.get(1), (Float) cols.get(2));
					}
				}
				logger.info(inserter.getRowsInserted() + " rows imported.");
				inserter.updateMinMax();
				logger.debug(inserter.keysUpdated()
						+ " meta information updated.");
			} catch (SQLException e) {
				con.rollback();
				throw new XmlRpcException(0, e.getMessage() + " Row " + cols
						+ ". Transaction rolled back.");
			} catch (InvalidRowStringException r) {
				con.rollback();
				throw new XmlRpcException(0, r.getMessage() + " Row " + cols
						+ ". Transaction rolled back.");
			} catch (InvalidRightException a) {
				con.rollback();
				String msg = a.getMessage() + " Row:" + cols
						+ ". Transaction rolled back.";
				logger.error(msg);
				throw new XmlRpcException(0, msg);
			} finally {
				con.commit();
				con.setAutoCommit(true);
				con.close();
			}
			return Boolean.TRUE;
		} catch (SQLException z) {
			String msg = z.getMessage();
			logger.error(msg);
			try {
				con.close();
			} catch (SQLException i) {
				;
			}
			throw new XmlRpcException(0, msg);
		}

	}

	public Boolean removeRowsByInterval(Integer id, Date startDate, Date endDate)
			throws XmlRpcException {

		PreparedStatement pst = null;
		Connection con = null;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("removeRowsByInterval(" + id + " )");
			}

			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id
						+ ".");
			}
			con = getPooledConnection();
			pst = con
					.prepareStatement("delete from massendaten where id = ? and von >= ? and von <= ?");
			pst.setInt(1, id.intValue());
			pst.setTimestamp(2, new java.sql.Timestamp(startDate.getTime()));
			pst.setTimestamp(3, new java.sql.Timestamp(endDate.getTime()));
			int r = pst.executeUpdate();
			logger.debug(r + " Rows deleted.");

			pst = con.prepareStatement("select update_massendaten_min_max(?)");
			pst.setInt(1, id.intValue());
			pst.executeQuery();
			logger.debug("Border updated.");

			return true;
		} catch (SQLException s) {
			logger.error(s.getMessage());
			throw new XmlRpcException(0, s.getMessage());
		} finally {
			try {
				con.close();
				pst.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
			;
		}
	}

	public Boolean removeAllRows(Integer id) throws XmlRpcException {
		PreparedStatement pst = null;
		Connection con = null;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("removeAllRows(" + id + " )");
			}

			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id
						+ ".");
			}
			con = getPooledConnection();
			pst = con.prepareStatement("delete from massendaten where id = ?");
			pst.setInt(1, id.intValue());
			int r = pst.executeUpdate();
			logger.debug(r + " rows deleted.");

			pst = con
					.prepareStatement("update objekt set rec_start = null, rec_end = null where id = ?");
			pst.setInt(1, id.intValue());
			pst.executeUpdate();
			logger.debug("Border updated.");

			return true;
		} catch (SQLException s) {
			logger.error(s.getMessage());
			throw new XmlRpcException(0, s.getMessage());
		} finally {
			try {
				con.close();
				pst.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
			;
		}

	}


	public Vector getMatrix(Vector ids, Vector timeFilter, Vector statusFilter,
			Boolean withStatus) throws XmlRpcException {

		if (ids == null || ids.size() == 0) {
			throw new XmlRpcException(0, "Vector of ids is empty");
		}

		// Check uniqueness of ids
		HashSet<Integer> i = new HashSet<Integer>(ids);
		if (i.size() != ids.size()) {
			throw new XmlRpcException(0, "Id values must be unique");
		}

		Iterator it = ids.iterator();
		while (it.hasNext()) {
			Integer id = (Integer) it.next();
			if (!checkRead(id)) {
				throw new XmlRpcException(1,
						"There is no series identified by id: " + id);
			}
		}
		TimeFilter tf = new TimeFilter(timeFilter);
		StatusFilter sf = new StatusFilter(statusFilter);
		Connection con = null;
		try {
			con = getPooledConnection();
			Vector ret = new Vector(2);
			ret.add(MatrixUtils.getHeader(con, ids, withStatus ? "_status" : null));
			String sql = MatrixUtils.getOrgQuerySep(ids, tf, sf, withStatus.booleanValue());
			logger.debug(sql);
			Statement st = con.createStatement();
			ResultSet rs = st.executeQuery(sql);
			ret.add(ByteUtils.getByte(rs));
			logger.debug("Returning matrix as byte");
			return ret;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(1, "Failed to get matrix");
		} catch (IOException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(1, "Failed to get matrix");
		} finally {
			try {
				con.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		}
	}

	

}
