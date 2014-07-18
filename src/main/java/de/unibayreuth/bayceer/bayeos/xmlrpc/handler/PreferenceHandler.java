package de.unibayreuth.bayceer.bayeos.xmlrpc.handler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Hashtable;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IPreferenceHandler;

public class PreferenceHandler extends AccessHandler implements
		IPreferenceHandler {

	final static Logger log = Logger.getLogger(PreferenceHandler.class);

	@Override
	public Hashtable<String, String> getPreferences(String application)
			throws XmlRpcException {

		log.debug("getPreferences for " + application + " and user "
				+ getUserId());

		if (application == null) {
			throw new XmlRpcException(0, "Invalid argument");
		}

		Connection con = null;
		Hashtable<String, String> ret = new Hashtable<>(30);
		try {
			con = ConnectionPool.getPooledConnection();

			PreparedStatement pst = con
					.prepareStatement("select key,value from preference where user_id = ? and application = ?");
			pst.setInt(1, getUserId());
			pst.setString(2, application);
			ResultSet rs = pst.executeQuery();
			while (rs.next()) {
				ret.put(rs.getString(1), rs.getString(2));
			}
		} catch (SQLException e) {
			log.error(e.getMessage());
			throw new XmlRpcException(0, "Failed to query preference for user:"	+ getUserId() + " and application: " + application);
		} finally {
			try {
				con.close();
			} catch (SQLException e) {
				log.error(e.getMessage());
			}
		}
		return ret;
	}

	@Override
	public Boolean setPreference(String application, String key, String value)	throws XmlRpcException {
		log.debug("setPreference:" + application + ":" + key + ":" + value);

		if (application == null || key == null) {
			log.warn("Invalid argument.");
			return false;
			
		}

		Connection con = null;
		try {

			con = ConnectionPool.getPooledConnection();
			con.setAutoCommit(false);

			// Delete key
			PreparedStatement pstmt = con
					.prepareStatement("delete from preference where user_id = ? and application = ? and key = ?");
			pstmt.setInt(1, getUserId());
			pstmt.setString(2, application);
			pstmt.setString(3, key);
			pstmt.executeUpdate();

			if (value != null) {
				// insert
				pstmt = con
						.prepareStatement("insert into preference (user_id,application,key,value) values (?,?,?,?)");
				pstmt.setInt(1, getUserId());
				pstmt.setString(2, application);
				pstmt.setString(3, key);
				pstmt.setString(4, value);
				pstmt.executeUpdate();

			}

			con.commit();
			
		} catch (SQLException e) {
			log.error(e.getMessage());
			try {
				con.rollback();
			} catch (SQLException e1) {
				log.error(e.getMessage());
			}
			return false;
			
		} finally {
			try {
				con.close();
			} catch (SQLException e) {
				log.error(e.getMessage());
			}
		}

		return true;
	}

}
