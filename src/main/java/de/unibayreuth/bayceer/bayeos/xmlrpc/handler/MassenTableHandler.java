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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ByteUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InsertMassendaten;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRightException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.MatrixUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.StatusFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.TimeFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IMassenTableHandler;

public class MassenTableHandler extends AccessHandler implements IMassenTableHandler {

	final static Logger logger = LoggerFactory.getLogger(MassenTableHandler.class);

	public Boolean addByteRows(byte[] b) throws XmlRpcException {
		logger.debug("addByteRows");
		return insertByteRows(b, false);
	}

	public Boolean upsertByteRows(byte[] b) throws XmlRpcException {
		logger.debug("upsertByteRows");
		return insertByteRows(b, true);
	}

	private Boolean insertByteRows(byte[] payload, boolean overwrite) throws XmlRpcException {
		logger.debug("insertByteRows");

		if (payload.length == 0)
			return true;

		try (Connection con = getPooledConnection()) {
			try {
				con.setAutoCommit(false);
				InsertMassendaten n = new InsertMassendaten(con,this.userId);
				n.insertByteArray(payload, overwrite);
				n.updateObjektInterval();
				con.commit();
				con.setAutoCommit(true);
			} catch (IOException | SQLException e) {
				logger.error(e.getMessage());
				con.rollback();
				return Boolean.FALSE;
			}

		} catch (SQLException e) {
			logger.error(e.getMessage());
			return Boolean.FALSE;
		}

		return Boolean.TRUE;
	}

	public Boolean addRow(Integer id, java.util.Date von, Double wert, Integer status) throws XmlRpcException {
		return insertRow(id, von, wert, status, false);
	}

	public Boolean upsertRow(Integer id, java.util.Date von, Double wert, Integer status) throws XmlRpcException {
		return insertRow(id, von, wert, status, true);
	}

	public Boolean insertRow(Integer id, java.util.Date von, Double wert, Integer status, boolean overwrite)
			throws XmlRpcException {

		if (!checkWrite(id)) throw new InvalidRightException(1, "Missing rights on Objekt " + id + ".");
		if (von == null || wert == null) throw new XmlRpcException(1, "Missing von value.");
		
		try (Connection con = getPooledConnection()) {
			try {
				con.setAutoCommit(false);
				InsertMassendaten inserter = new InsertMassendaten(con, this.userId);
				if (overwrite) {
					inserter.upsert(id, new java.sql.Timestamp(von.getTime()), new Short(status.shortValue()),
							new Float(wert.floatValue()));

				} else {
					inserter.insert(id, new java.sql.Timestamp(von.getTime()), new Short(status.shortValue()),
							new Float(wert.floatValue()));
				}
				inserter.updateObjektInterval();
				con.commit();
				con.setAutoCommit(true);

			} catch (SQLException s) {
				logger.error(s.getMessage());
				con.rollback();
				return Boolean.FALSE;
			}
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return Boolean.FALSE;
		}

		return Boolean.TRUE;

	}

	public Boolean updateRow(Integer id, java.util.Date old_von, Integer statId, Double wert, java.util.Date new_von)
			throws XmlRpcException {
		PreparedStatement pst = null;
		Connection con = null;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("updateRow(" + id + "," + old_von + "," + statId + "," + wert + ")");
			}
			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id + ".");
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

	public Boolean updateRows(Integer id, Vector vonDates, Integer statId) throws XmlRpcException {
		// Update Batch testen !
		PreparedStatement pst = null;
		Connection con = null;
		try {
			// create prepared statement
			if (logger.isDebugEnabled()) {
				logger.debug("updateRows(" + id + "," + statId + ")");
			}

			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id + ".");
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

	public Boolean removeRows(Integer id, Vector vonDates) throws XmlRpcException {
		PreparedStatement pst = null;
		Connection con = null;
		try {
			// create prepared statement
			if (logger.isDebugEnabled()) {
				logger.debug("deleteRows(" + id + " )");
			}

			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id + ".");
			}

			con = getPooledConnection();
			pst = con.prepareStatement("delete from massendaten where von = ? and id = ?");
			pst.setInt(2, id.intValue());
			Iterator it = vonDates.iterator();
			con.setAutoCommit(false);
			while (it.hasNext()) {
				java.util.Date vonDate = (java.util.Date) it.next();
				java.sql.Timestamp sqlvon = new java.sql.Timestamp(vonDate.getTime());
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

	public Vector getRows(Integer id, Vector timeFilter, Vector statusFilter) throws XmlRpcException {
		PreparedStatement pst = null;
		Connection con = null;
		ResultSet rs = null;
		Vector vReturn = null;
		try {

			if (logger.isDebugEnabled()) {
				logger.debug("getRows(" + id + ")");
			}

			if (!checkRead(id)) {
				throw new XmlRpcException(1, "There is no series identified by id: " + id);
			}

			TimeFilter tFilter = new TimeFilter(timeFilter);
			StatusFilter sFilter = new StatusFilter(statusFilter);

			con = getPooledConnection();
			String query = "select von, wert, status "
					+ " from massendaten where id = ? and von >= ? and von <= ? and status in "
					+ SelectUtils.getInInteger(sFilter.getIds()) + " order by von asc";
			if (logger.isDebugEnabled()) {
				logger.debug("Query:" + query);
			}

			pst = con.prepareStatement(query);
			pst.setInt(1, id.intValue());
			pst.setTimestamp(2, new java.sql.Timestamp(tFilter.getVon().getTime()));
			pst.setTimestamp(3, new java.sql.Timestamp(tFilter.getBis().getTime()));

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

	public Boolean removeRowsByInterval(Integer id, Date startDate, Date endDate) throws XmlRpcException {

		PreparedStatement pst = null;
		Connection con = null;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("removeRowsByInterval(" + id + " )");
			}

			if (!checkWrite(id)) {
				throw new XmlRpcException(1, "Missing rights on Objekt " + id + ".");
			}
			con = getPooledConnection();
			pst = con.prepareStatement("delete from massendaten where id = ? and von >= ? and von <= ?");
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
				throw new XmlRpcException(1, "Missing rights on Objekt " + id + ".");
			}
			con = getPooledConnection();
			pst = con.prepareStatement("delete from massendaten where id = ?");
			pst.setInt(1, id.intValue());
			int r = pst.executeUpdate();
			logger.debug(r + " rows deleted.");

			pst = con.prepareStatement("update objekt set rec_start = null, rec_end = null where id = ?");
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

	public Vector getMatrix(Vector ids, Vector timeFilter, Vector statusFilter, Boolean withStatus)
			throws XmlRpcException {

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
				throw new XmlRpcException(1, "There is no series identified by id: " + id);
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
