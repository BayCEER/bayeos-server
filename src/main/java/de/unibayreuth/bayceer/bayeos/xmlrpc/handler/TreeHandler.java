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
 * TreeHandler.java
 *
 * Created on 6.03.2003 
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.objekt.ObjektArt;
import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.InvalidRightException;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.filter.TimeFilter;
import de.unibayreuth.bayceer.bayeos.xmlrpc.formats.DateFormat;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ITreeHandler;

/**
 * 
 * @author oliver
 */

public class TreeHandler extends AccessHandler implements ITreeHandler {

	final static Logger logger = LoggerFactory.getLogger(TreeHandler.class);

	/**
	 * Get Root Node
	 * 
	 * @author oliver 24.03.2011 OA Refactored paramater calls
	 * @author oliver 03.08.2012 Added new Methods
	 * @author oliver 24.02.2016 New try with resource statement 
	 * 
	 */
	public Vector getRoot(String art, Boolean activeFilter, String missingInterval, Vector timeFilter)
			throws XmlRpcException {
		Connection con = null;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("getRoot(" + art + ")");
			}
			con = ConnectionPool.getPooledConnection();			
			try (PreparedStatement pst = con.prepareStatement(
					"select check_write(o.id,?),check_exec(o.id,?),o.id,o.id_super, art_objekt.uname as objektart,"
							+ " o.de, o.rec_start, o.rec_end, o.plan_start, o.plan_end,isActive(o.plan_start,o.plan_end),"
							+ "isMissing(o.rec_end,?), true from objekt o, art_objekt where o.id_super is null "
							+ "and o.id_art = art_objekt.id and art_objekt.uname = ? and check_read(o.id,?)")) {
				pst.setInt(1, getUserId());
				pst.setInt(2, getUserId());
				pst.setString(3, missingInterval);
				pst.setString(4, art);
				pst.setInt(5, getUserId());
				ResultSet rs = pst.executeQuery();
				if (rs.next()) {
					return XmlRpcUtils.rowToVector(rs);
				} else {
					try (PreparedStatement st = con.prepareStatement(
							"select false, false,id,id_super,uname ,de, null,null,null,null,true,false,true from objekt_extern where id_super is null and uname = ?")) {
						st.setString(1, art);
						rs = st.executeQuery();
						if (rs.next()) {
							return XmlRpcUtils.rowToVector(rs);
						} else {
							return null;
						}
					}
				}
			}
		} catch (SQLException e) {
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				con.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		}
	}

	// Liefert den kompletten Subbaum mit Pfad
	// Implementiert für R-Package
	// SH 16.02.2011
	public Vector getAllChildren(Integer SuperId, Boolean isExtern, Vector webFilter, String antFilter,
			String artFilter, Integer maxDepth, Boolean activeFilter, String missingInterval, Vector timeFilter)
					throws XmlRpcException {
		TimeFilter tFilter;
		if (timeFilter == null) {
			tFilter = null;
		} else {
			tFilter = new TimeFilter(timeFilter);
		}
		String isMissing, isActive, Active, von, bis;
		StringBuffer sql;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("getAllChildren(" + SuperId + ",...");
			}
			String arts = SelectUtils.getInString(webFilter);
			String userId = getUserId().toString();

			isActive = "isActive(plan_start,plan_end)";
			isMissing = (missingInterval != null) ? "isMissing(rec_end,'" + missingInterval + "')" : "false";
			Active = (activeFilter != null && activeFilter.booleanValue()) ? "true" : "false";
			von = (tFilter != null ? DateFormat.databaseDateFormat.format(tFilter.getVon()) : "null");
			bis = (tFilter != null ? DateFormat.databaseDateFormat.format(tFilter.getBis()) : "null");
			if (isExtern) {
				sql = new StringBuffer("select *,null,null from get_web_objekt_baum(");
				sql.append(SuperId);
				sql.append(",'./',");
				sql.append(maxDepth);
				sql.append(") where art not in");// Stimmt das??
				sql.append(arts);
			} else {
				// ID's der eigenen Childs + Verweise
				sql = new StringBuffer("select *,");
				sql.append(isActive);
				sql.append(",");
				sql.append(isMissing);
				sql.append(" from get_objekt_baum(");
				sql.append(SuperId);
				sql.append(",");
				sql.append(userId);
				sql.append(",'./',");
				sql.append(maxDepth);
				sql.append(",");
				sql.append(Active);
				sql.append(",");
				sql.append(von);
				sql.append(",");
				sql.append(bis);
				sql.append(") where true");
			}
			if (artFilter.length() > 0) {
				sql.append(" and art ilike E'");
				sql.append(SelectUtils.addSlashes(artFilter));
				sql.append("%'");
			}
			if (antFilter.length() > 0) {
				sql.append(" and escape_path_separator(path||replace(name,'/',E'\\\\/')) ~* ant_to_regexp(E'");
				sql.append(SelectUtils.addSlashes(antFilter));
				sql.append("')");
			}

			if (logger.isDebugEnabled()) {
				logger.debug(sql.toString());
			}
			return XmlRpcUtils.getRows(getPooledConnection(), sql.toString());
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		}
	}

	public Boolean renameNode(Integer obj_id, String art, String name) throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("renameNode(" + obj_id + ")");
		}

		if (!checkWrite(obj_id))
			throw new InvalidRightException(0, "Missing rights on " + obj_id + ".");

		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;
		try {
			con = getPooledConnection();
			String sql = "select rename_objekt_and_detail(?,?,?,?)";
			st = con.prepareStatement(sql);
			st.setInt(1, obj_id.intValue());
			st.setString(2, art);
			st.setString(3, name);
			st.setString(4, name);
			rs = st.executeQuery();
			rs.next();
			return (new Boolean(rs.getBoolean(1)));
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				st.close();
			} catch (SQLException i) {
			}
			;
			try {
				rs.close();
			} catch (SQLException i) {
			}
			;
			try {
				if (!con.isClosed())
					con.close();
			} catch (SQLException i) {
			}
			;
		}

	}

	public Boolean deleteNode(Integer obj_id) throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("deleteNode(" + obj_id + ")");
		}

		if (!checkFullAccess(obj_id))
			throw new InvalidRightException(0, "Missing rights on " + obj_id + ".");

		Connection con = null;
		PreparedStatement st = null;
		try {
			con = getPooledConnection();
			String sql = "select delete_objekt(?)";
			st = con.prepareStatement(sql);
			st.setInt(1, obj_id.intValue());
			st.execute();
			return Boolean.TRUE;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				st.close();
			} catch (SQLException i) {
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

	public Boolean moveNode(Integer id, Integer targetId) throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("moveNode(" + id + ";" + targetId + ")");
		}
		if (!(checkWrite(targetId) && checkWrite(id))) {
			throw new InvalidRightException(0, "Missing rights.");
		}
		Connection con = null;
		PreparedStatement st = null;
		try {
			con = getPooledConnection();
			String sql = "select move_objekt(?,?)";
			st = con.prepareStatement(sql);
			st.setInt(1, id.intValue());
			st.setInt(2, targetId.intValue());
			return new Boolean(st.execute());
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				st.close();
			} catch (SQLException i) {
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

	public Boolean linkNode(Integer id_von, Integer id_auf) throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("linkNode(" + id_von + ";" + id_auf + ")");
		}
		if (!(checkWrite(id_von) && checkWrite(id_auf))) {
			throw new InvalidRightException(0, "Missing rights.");
		}
		Connection con = null;
		PreparedStatement st = null;
		try {
			con = getPooledConnection();
			String sql = "insert into verweis (id_von, id_auf) values (?,?)";
			st = con.prepareStatement(sql);
			st.setInt(1, id_von.intValue());
			st.setInt(2, id_auf.intValue());
			return new Boolean(st.execute());
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				st.close();
			} catch (SQLException i) {
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

	public Integer copyNode(Integer id, Integer targetId) throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("copyNode(" + id + ";" + targetId + ")");
		}
		if (!(checkWrite(id) && checkWrite(targetId))) {
			throw new InvalidRightException(0, "Missing rights.");
		}
		Connection con = null;
		PreparedStatement st = null;
		try {
			con = getPooledConnection();
			String sql = "select copy_with_child(?,?)";
			st = con.prepareStatement(sql);
			st.setInt(1, id.intValue());
			st.setInt(2, targetId.intValue());
			ResultSet s = st.executeQuery();
			s.next();
			int i = s.getInt(1);
			s.close();
			return new Integer(i);
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				st.close();
			} catch (SQLException i) {
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

	public Vector newNode(String art, String name, Integer parent_id) throws XmlRpcException {
		if (logger.isDebugEnabled()) {
			logger.debug("newNode(" + art + ")");
		}

		if (!(checkWrite(parent_id) && checkExecute(art))) {
			throw new InvalidRightException(0, "Missing rights on " + parent_id + ".");
		}
		Connection con = null;
		ResultSet rs = null;
		PreparedStatement st = null;
		try {
			con = getPooledConnection();
			String sql = "select create_objekt_with_detail(?,?,?,?,?)";
			st = con.prepareStatement(sql);
			st.setInt(1, getUserId().intValue());
			st.setInt(2, parent_id.intValue());
			st.setString(3, art);
			st.setString(4, name);
			st.setString(5, name);
			rs = st.executeQuery();
			rs.next();
			int id = rs.getInt(1);
			// ObjektNode zur�ck geben
			Vector ret = new Vector();
			ret.add(Boolean.TRUE); // check_write;
			ret.add(Boolean.TRUE); // check_exec;
			ret.add(new Integer(id));
			ret.add(parent_id);
			ret.add(art);
			ret.add(name);
			ret.add(null); // rec_start
			ret.add(null); // rec_end
			ret.add(null); // plan_start
			ret.add(null); // plan_end
			ret.add(Boolean.FALSE); // active
			ret.add(Boolean.FALSE); // missing
			ret.add(Boolean.FALSE); // has_child

			return ret;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				rs.close();
				st.close();
			} catch (SQLException e) {
				;
			}
			try {
				if (!con.isClosed())
					con.close();
			} catch (SQLException i) {
			}
			;
		}

	}

	public Vector getInheritedParentIds(Integer obj_id, String art) throws XmlRpcException {

		if (logger.isDebugEnabled()) {
			logger.debug("getInheritedParentIds(" + obj_id + "," + art + ")");
		}
		Connection con = null;
		ResultSet rs = null;
		try {
			// alle parent knoten bestimmen
			con = getPooledConnection();
			if (ObjektArt.get(art).isExtern()) {
				rs = SelectUtils.getResultSet(con, "select get_web_parent_ids(" + obj_id + ")");
			} else {
				rs = SelectUtils.getResultSet(con, "select get_inherited_parent_ids(" + obj_id + ")");
			}
			rs.next(); // gibt immer einen Record zur�ck
			String ParentIds = rs.getString(1);
			Vector vReturn = new Vector(Arrays.asList(ParentIds.split(",")));
			rs.close();
			return vReturn;
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				if (!con.isClosed())
					con.close();
			} catch (SQLException i) {
			}
			;
		}
	}

	public java.util.Vector findNode(java.lang.String name) throws XmlRpcException {
		String sql;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Searching..");
			}
			sql = "select u.de,u.id,u.id_super, u.uname from (";
			sql = sql + "select de, id, id_super, uname, true as a from v_web_objekt where lower(de) like '%"
					+ SelectUtils.addSlashes(name.toLowerCase()) + "%' "
					+ "union select o.de ,o.id, o.id_super, uname, check_read(o.id," + getUserId()
					+ ") as a  from objekt o, art_objekt where " + "o.id_art = art_objekt.id and lower(o.de) like '%"
					+ SelectUtils.addSlashes(name.toLowerCase()) + "%' and uname in "
					+ "('mess_einheit','mess_ziel','messung_ordner','messung_massendaten','messung_labordaten','mess_geraet','mess_kompartiment','mess_ort')) u ";
			sql = sql + " where u.a = true";
			return XmlRpcUtils.getRows(getPooledConnection(), sql);
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		}

	}

	public Vector getChilds(Integer SuperId, String art, Boolean activeFilter, String missingInterval,
			Vector timeFilter) throws XmlRpcException {
		StringBuffer sql;
		PreparedStatement pst = null;
		Connection con = null;
		Timestamp fvon = null;
		Timestamp fbis = null;

		try {
			if (logger.isDebugEnabled()) {
				logger.debug("getChilds(" + SuperId + ")");
			}
			con = ConnectionPool.getPooledConnection();

			if (timeFilter != null) {
				TimeFilter tFilter = new TimeFilter(timeFilter);
				fvon = new java.sql.Timestamp(tFilter.getVon().getTime());
				fbis = new java.sql.Timestamp(tFilter.getBis().getTime());
			}

			ObjektArt artObjekt = ObjektArt.get(art);

			if (!artObjekt.isExtern()) {
				sql = new StringBuffer("select check_write(s.id,?), check_exec(s.id,?), s.* from ( "
						+ "select o.id, o.id_super, a.uname as objektart, o.de, o.rec_start, o.rec_end, o.plan_start, o.plan_end, "
						+ "isActive(o.plan_start,o.plan_end),isMissing(o.rec_end,?),has_child(o.id) "
						+ "from objekt o, art_objekt a where o.id_art = a.id and o.id_super = ? " + "union "
						+ "select v.id_auf as id,o.id_super, a.uname as objektart, o.de, o.rec_start, o.rec_end, o.plan_start, o.plan_end,"
						+ "isActive(o.plan_start,o.plan_end),isMissing(o.rec_end,?),has_child(o.id) from objekt o,verweis v,"
						+ "art_objekt a where o.id = v.id_auf and v.id_von = ? and o.id_art = a.id ) S "
						+ " where check_read(s.id,?)");

				if (timeFilter != null) {
					sql.append(
							" and ((? <= s.rec_start AND ? > s.rec_start) OR ( ? >= s.rec_start AND ? < s.rec_end)) ");

				}
				if (activeFilter != null && activeFilter == true) {
					sql.append(" and (isActive = true)");

				}
				sql.append(" order by 6 ");

				pst = con.prepareStatement(sql.toString());
				pst.setInt(1, getUserId());
				pst.setInt(2, getUserId());
				pst.setString(3, missingInterval);
				pst.setInt(4, SuperId);
				pst.setString(5, missingInterval);
				pst.setInt(6, SuperId);
				pst.setInt(7, getUserId());

				if (timeFilter != null) {
					pst.setTimestamp(8, fvon);
					pst.setTimestamp(9, fbis);
					pst.setTimestamp(10, fvon);
					pst.setTimestamp(11, fbis);
				}

			} else {

				sql = new StringBuffer(
						"select check_write(o.id,?), check_exec(o.id,?), o.id, o.id_super, a.uname as objektart, o.de, o.rec_start, o.rec_end, o.plan_start, o.plan_end, "
								+ "isActive(o.plan_start,o.plan_end),isMissing(o.rec_end,?),true "
								+ "from objekt o, art_objekt a, verweis_extern v where  "
								+ "v.id_auf = o.id and v.id_von = ? and o.id_art = a.id and check_read(o.id,?) "
								+ "union "
								+ "select false as check_write, false as check_exec, o.id, o.id_super, o.uname as objektart,o.de,null,null,null,null,false,false,"
								+ "true from objekt_extern o where o.id_super = ? ");
				if (timeFilter != null) {
					sql.append(
							" and ((? <= o.rec_start AND ? > o.rec_start) OR ( ? >= o.rec_start AND ? < o.rec_end)) ");
				}
				if (activeFilter != null && activeFilter == true) {
					sql.append(" and (isActive = true)");
				}

				sql.append(" order by 6 ");

				pst = con.prepareStatement(sql.toString());
				pst.setInt(1, getUserId());
				pst.setInt(2, getUserId());
				pst.setString(3, missingInterval);
				pst.setInt(4, SuperId);
				pst.setInt(5, getUserId());
				pst.setInt(6, SuperId);

				if (timeFilter != null) {
					pst.setTimestamp(7, fvon);
					pst.setTimestamp(8, fbis);
					pst.setTimestamp(9, fvon);
					pst.setTimestamp(10, fbis);
				}
			}
			return XmlRpcUtils.getRows(pst);
		} catch (SQLException e) {
			logger.error(e.getMessage());
			throw new XmlRpcException(0, e.getMessage());
		} finally {
			try {
				pst.close();
				con.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());

			}
		}

	}

	public Vector getChildren(Integer SuperId, String art, Vector webFilter, Boolean activeFilter,
			String missingInterval, Vector timeFilter) throws XmlRpcException {
		return getChilds(SuperId, art, activeFilter, missingInterval, timeFilter);
	}

	public Vector findOrSaveNode(String art, String name, Integer parent_id) throws XmlRpcException {

		if (logger.isDebugEnabled()) {
			logger.debug("findOrSaveNode()");
		}
		if (parent_id == null) {
			Vector root = getRoot(art, null, null, null);
			if (root == null) {
				throw new XmlRpcException(0, "Can't find root node for art:" + art);
			} else {
				parent_id = (Integer) root.get(3);
			}
		}

		Vector childs = getChilds(parent_id, art, null, null, null);
		Iterator i = childs.iterator();

		while (i.hasNext()) {
			Vector node = (Vector) i.next();
			if (node.get(5).equals(name)) {
				return node;
			}
		}

		return newNode(art, name, parent_id);
	}

	public String getNodePath(Integer id) throws XmlRpcException {
		Connection con = null;
		PreparedStatement pst = null;
		ResultSet rs = null;
		String path = null;
		if (!checkRead(id)) {
			throw new InvalidRightException(0, "Missing rights.");
		}
		try {
			con = ConnectionPool.getPooledConnection();
			pst = con.prepareStatement("select get_path(?)");
			pst.setInt(1, id);
			rs = pst.executeQuery();
			rs.next();
			path = rs.getString(1);
		} catch (SQLException e) {
			logger.error(e.getMessage());
		} finally {
			try {
				rs.close();
				pst.close();
				con.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		}
		return path;
	}

	@Override
	public Vector getNode(Integer id) throws XmlRpcException {
		Connection con = null;
		PreparedStatement pst = null;
		Vector ret = null;
		if (!checkRead(id)) {
			throw new InvalidRightException(0, "Missing rights.");
		}
		try {
			con = ConnectionPool.getPooledConnection();
			pst = con.prepareStatement(
					"select check_write(o.id,?), check_exec(o.id,?), o.id, o.id_super, a.uname as objektart, o.de, o.rec_start, o.rec_end, o.plan_start, o.plan_end, "
							+ "isActive(o.plan_start,o.plan_end),null,has_child(o.id) from objekt o, art_objekt a where o.id_art = a.id and o.id = ?");
			pst.setInt(1, getUserId());
			pst.setInt(2, getUserId());
			pst.setInt(3, id);
			ret = XmlRpcUtils.getRow(pst);
		} catch (SQLException e) {
			logger.error(e.getMessage());
		} finally {
			try {
				pst.close();
				con.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		}
		return ret;
	}

}
