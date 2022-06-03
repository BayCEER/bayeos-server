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
 * RightHandler.java
 *
 * Created on 28. August 2002, 13:47
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.objekt.Right;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRightException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IRightHandler;


/**
 * 
 * @author oliver
 */
public class RightHandler extends AccessHandler implements IRightHandler {

	final static Logger logger = LoggerFactory.getLogger(RightHandler.class);

	/**
	 * Return the result, or throw an Exception if something went wrong.
	 */

	public Vector getRights(Integer obj_id) throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("getRights(" + obj_id + ")");
		}
		Connection con = null;
		ResultSet rs = null;
		try {
			con = getPooledConnection();
			rs = SelectUtils.getResultSet(con,
					"select get_inherited_parent_ids(" + obj_id + ")");
			rs.next(); // gibt immer einen Record zurück
			String ParentIds = rs.getString(1);
			rs.close();
			// Alle Direkt zugeordneten Rechte
			String sql = "select o.de as User , z.read as Read, z.write as Write, z.exec as Execute, z.inherit as Inherit, true as Editable, a.uname, z.id_benutzer "
					+ "from zugriff z, benutzer b, art_objekt a, objekt o where o.id = z.id_benutzer and o.id_art = a.id and z.id_obj = "
					+ obj_id
					+ " and z.id_benutzer = b.id "
					+ "union "
					+
					// Vererbte Rechte
					"select o.de as User, z.read as Read, z.write as Write, z.exec as Execute, z.inherit as Inherit, false as Editable, a.uname, z.id_benutzer "
					+ "from zugriff z, benutzer b, art_objekt a, objekt o where o.id = z.id_benutzer and o.id_art = a.id and z.id_obj in ("
					+ ParentIds
					+ ") and z.id_benutzer = b.id and z.inherit = true";
			rs = SelectUtils.getResultSet(con, sql);
			Vector vReturn = new Vector();
			vReturn.add(XmlRpcUtils.resultSetToVector(rs));
			rs.close();
			return vReturn;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				if (!con.isClosed())
					con.close();
			} catch (Exception i) {
			}
			;
		}
	}

	public Boolean hasFullAccess(Integer id) throws XmlRpcException {
		return checkFullAccess(id);
	}

	public Boolean updateRight(Integer id_obj, Integer id_benutzer,
			String right, Boolean enabled) throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("updateRight(" + id_obj + "," + id_benutzer + ","
					+ right + "," + enabled + ")");
		}

		if (!checkFullAccess(id_obj))
			throw new InvalidRightException(0, "Missing rights on " + id_obj
					+ ".");		
		
			Connection con = null;
			PreparedStatement st = null;
		try {
			Right r = Right.get(right);
			con = getPooledConnection();
			String sql = "update zugriff set " + r.toString()
					+ " = ? where id_obj = ? and id_benutzer = ?";
			st = con.prepareStatement(sql);
			st.setBoolean(1, enabled.booleanValue());
			st.setInt(2, id_obj.intValue());
			st.setInt(3, id_benutzer.intValue());
			int rows = st.executeUpdate();
			if (rows == 1) {
				return true;
			} else {
				logger.error("Update statement changed more than one row.");
				return false;
			}
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, "Illegal argument.");
		} finally {
			try {
				st.close();
			} catch (Exception i) {
			}
			;
			try {
				if (!con.isClosed())
					con.close();
			} catch (Exception i) {
			}
			;
		}

	}

	public Boolean createRight(Integer id_obj, Integer id_benutzer,
			Boolean read, Boolean write, Boolean exec, Boolean inherit)
			throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("createRight(" + id_obj + "," + id_benutzer + ")");
		}
		if (!checkFullAccess(id_obj))
			throw new InvalidRightException(0, "Missing rights on " + id_obj
					+ ".");

		Connection con = null;
		PreparedStatement st = null;
		try {
			con = getPooledConnection();
			String sql = "insert into zugriff (id_obj,id_benutzer,read,write,exec,inherit) values (? ,?, ?, ?,?,?)";
			st = con.prepareStatement(sql);
			st.setInt(1, id_obj.intValue());
			st.setInt(2, id_benutzer.intValue());
			st.setBoolean(3, read.booleanValue());
			st.setBoolean(4, write.booleanValue());
			st.setBoolean(5, exec.booleanValue());
			st.setBoolean(6, inherit.booleanValue());
			int rows = st.executeUpdate();
			if (rows == 1) {
				return true;
			} else {
				logger.error("Insert statement inserted more than one row.");
				return false;
			}
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				st.close();
			} catch (Exception i) {
			}
			;
			try {
				if (!con.isClosed())
					con.close();
			} catch (Exception i) {
			}
			;
		}

	}

	public Boolean deleteRight(Integer id_obj, Integer id_benutzer)
			throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("deleteRight(" + id_obj + "," + id_benutzer + ")");
		}

		if (!checkFullAccess(id_obj))
			throw new InvalidRightException(0, "Missing rights on " + id_obj
					+ ".");

		Connection con = null;
		PreparedStatement st = null;
		try {
			con = getPooledConnection();
			String sql = "delete from zugriff where id_obj = ? and id_benutzer = ?";
			st = con.prepareStatement(sql);
			st.setInt(1, id_obj.intValue());
			st.setInt(2, id_benutzer.intValue());
			int rows = st.executeUpdate();
			if (rows == 1) {
				return true;
			} else {
				logger.error("Delete statement deleted more than one row.");
				return false;
			}
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				st.close();
			} catch (Exception i) {
			}
			;
			try {
				if (!con.isClosed())
					con.close();
			} catch (Exception i) {
			}
			;
		}

	}

}
