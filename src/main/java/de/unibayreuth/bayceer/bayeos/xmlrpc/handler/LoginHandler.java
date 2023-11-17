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

import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPException;
import com.novell.ldap.LDAPJSSESecureSocketFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.SelectUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.Session;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XMLServlet;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ILoginHandler;

public class LoginHandler implements ILoginHandler {
	final static Logger logger = LoggerFactory.getLogger(LoginHandler.class.getName());

	public LoginHandler() {

	}

	public Vector createSession(String Login, String PassWord) throws XmlRpcException {

		Vector result = new Vector();
		Integer userId = authenticate(Login, PassWord);
		setLastSeen(userId);
		Integer sessionId = Session.create(userId);
		result.add(sessionId);
		result.add(userId);
		return result;

	}
	
	private void setLastSeen(Integer benutzerID) {
		Connection con = null;
		String sql = "UPDATE benutzer set last_seen = current_timestamp WHERE id = ?";
		PreparedStatement st = null;
		try {
			con = ConnectionPool.getPooledConnection();
			st = con.prepareStatement(sql);
			st.setInt(1, benutzerID);
			st.executeUpdate();
		} catch (SQLException e) {
			logger.error(e.getMessage());
		} finally {
			try {
				st.close();
				if (!con.isClosed())
					con.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		}
	}
	
	

	private Integer authenticate(String login, String passWord) throws XmlRpcException {

		Integer idBenutzer = getBenutzerId(login);
		if (idBenutzer == null) {
			throw new XmlRpcException(0, "Error authenticating user " + login + ".");
		}

		if (authenticateIP(idBenutzer, login, XMLServlet.getClientIpAddress())) {
			return idBenutzer;
		}
		
		if (authenticateDB(idBenutzer, login, passWord)) {
			return idBenutzer;
		}
		
		if (authenticatLdap(idBenutzer, login, passWord)) {
			return idBenutzer;
		}

		throw new XmlRpcException(0, "Error authenticating user " + login + ".");
	}

	private boolean authenticateIP(Integer id, String login, String ip) {
		// Authenticate by ip without password
		logger.info("Trying to authenticate user:" + login + " by IP:" + ip);
		String sql = "select access from auth_ip " + "where ?::inet<<=network and (login=? or login='*') "
				+ "order by case when login='*' then 1 else 0 end, "
				+ /* Direkte Nutzer kommen zuerst */
				"masklen(network) desc, " + /* Genauere Einträge zuerst */
				"access"; /* TRUST vor DENY vor PASSWORD */
		try (Connection con = ConnectionPool.getPooledConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
			pst.setString(1, ip);
			pst.setString(2, login);
			ResultSet rs = pst.executeQuery();
			if (rs.next()) {
				String access = rs.getString(1);
				if (access.equals("TRUST")) {
					return true;
				} else if (access.equals("DENY")) {
					return false;
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return false;
	}

	private boolean authenticateDB(Integer idBenutzer, String login, String passWord) {
		String sql = "select crypt(?,substr(pw,1,2)) = pw from benutzer where login like ?";
		logger.debug("Trying to authenticate user " + login + " by db");
		try (Connection con = ConnectionPool.getPooledConnection(); PreparedStatement pst = con.prepareStatement(sql)) {
			pst.setString(1, passWord);
			pst.setString(2, login);
			ResultSet rs = pst.executeQuery();
			rs.next();
			if (rs.getBoolean(1)) {
				logger.info("User " + login + " authenticated by db");
				return true; // Authentication successful
			} else {
				logger.warn("Failed to authenticate user " + login + " by db");
				return false;
			}
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return false;
		}
	}

	private boolean authenticatLdap(Integer idBenutzer, String login, String passWord) {

		int ldapVersion = LDAPConnection.LDAP_V3;
		LDAPConnection lc = null;

		try (Connection con = ConnectionPool.getPooledConnection()) {
			ResultSet rs1 = SelectUtils.getResultSet(con,
					"select name, host, dn, ssl, port from benutzer, auth_ldap where benutzer.fk_auth_ldap = auth_ldap.id and benutzer.id = "
							+ idBenutzer);
			if (!rs1.next()) {
				return false;
			}

			String name = rs1.getString("name");
			logger.debug("Trying to authenticat user " + login + " by LDAP:" + name);

			if (rs1.getBoolean("ssl")) {
				lc = new LDAPConnection(new LDAPJSSESecureSocketFactory());
			} else {
				lc = new LDAPConnection();
			}
			lc.connect(rs1.getString("host"), rs1.getInt("port"));
			lc.bind(ldapVersion, rs1.getString("dn").replace(":?", login), passWord.getBytes("UTF8"));
			if (lc.isBound()) {
				logger.info("User " + login + " authenticated by " + name);
				return true;
			} else {
				logger.warn("Failed to authenticate user: " + login + " by " + name);
				return false;
			}
		} catch (SQLException | LDAPException | UnsupportedEncodingException e) {
			logger.error(e.getMessage());
			return false;
		}
	}

	private Integer getBenutzerId(String login) {
		try (Connection con = ConnectionPool.getPooledConnection();
			 PreparedStatement st = con.prepareStatement("select id from benutzer where login like ? and locked = false")) {
			st.setString(1, login);
			ResultSet rs = st.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			} else {
				return null;
			}
		} catch (SQLException e) {
			logger.error(e.getMessage());
			return null;
		}

	}

}
