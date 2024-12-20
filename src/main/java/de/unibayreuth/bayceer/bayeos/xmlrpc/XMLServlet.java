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
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.crypto.SecretKey;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.xmlrpc.Base64;
import org.apache.xmlrpc.OctetServer;
import org.apache.xmlrpc.OctetServerException;
import org.apache.xmlrpc.XmlRpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.AggregationTableHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.DataFrameHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.LaborTableHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.LogOffHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.LoginHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.LookUpTableHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.MassenTableHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.MessungHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.ObjektArtHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.ObjektHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.OctetMatrixHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.PreferenceHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.RightHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.TokenHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.ToolsHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.TreeHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ILoginHandler;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

public class XMLServlet extends HttpServlet {

	protected static XmlRpcServer xmlRpcServer;
	protected static OctetServer octetServer;
	protected final static Logger log = LoggerFactory.getLogger(XMLServlet.class);
	
	private Integer sessionId, userId;
	
	private static ThreadLocal threadLocal = new ThreadLocal();
	
	private String version = "";
	

	/**
	 * Initializes the servlet.
	 */
	public void init(ServletConfig config) throws ServletException {     					
		// Load Application properties file from classpath
		final Properties prop = new Properties();		
		try {
			prop.load(this.getClass().getClassLoader().getResourceAsStream("application.properties"));
		} catch (IOException e) {
			log.warn("Failed to read application properties from file.");	
		}		
		
					
		version = prop.getProperty("version");
		LookUpTableHandler.setVersion(version);
	
		// TimeZone
		ServletContext context = config.getServletContext();		
		String timeZone = context.getInitParameter("server.tz");								
		System.setProperty("user.timezone", timeZone);
		TimeZone.setDefault(TimeZone.getTimeZone(timeZone));		
		log.info("Default timezone: " + TimeZone.getDefault().getID());
						
		
		// Encoding 
		log.info("Encoding:" + System.getProperty("file.encoding"));
	
			
		// Connection			
		ConnectionPool.setConnection(String.format("jdbc:postgresql://%s/%s",context.getInitParameter("db.hostname"),context.getInitParameter("db.name")), 
				context.getInitParameter("db.username"), context.getInitParameter("db.password"));

		// xml-rpc
		log.debug("Initialize XmlRpcServer ...");
		xmlRpcServer = new XmlRpcServer();
				
		String keyPath = context.getInitParameter("server.configPath") + File.separator + "api_key";		
		SecretKey apiKey;
		try {			
			apiKey = Keys.hmacShaKeyFor(Files.readAllBytes(Paths.get(keyPath)));
		} catch (WeakKeyException | IOException e) {
			log.error(String.format("Failed to create key from file:%s",keyPath));
			return;
		}	
		ILoginHandler loginHandler = new LoginHandler(apiKey);
		xmlRpcServer.addHandler("LoginHandler", loginHandler);
		xmlRpcServer.addHandler("LogOffHandler", new LogOffHandler());
		xmlRpcServer.addHandler("ObjektArtHandler", new ObjektArtHandler());
		xmlRpcServer.addHandler("ObjektHandler", new ObjektHandler());
		xmlRpcServer.addHandler("RightHandler", new RightHandler());
		xmlRpcServer.addHandler("MassenTableHandler", new MassenTableHandler());
		xmlRpcServer.addHandler("LaborTableHandler", new LaborTableHandler());
		xmlRpcServer.addHandler("LookUpTableHandler", new LookUpTableHandler());
		xmlRpcServer.addHandler("AggregationTableHandler", new AggregationTableHandler());
		xmlRpcServer.addHandler("TreeHandler", new TreeHandler());
		xmlRpcServer.addHandler("ToolsHandler", new ToolsHandler());
		xmlRpcServer.addHandler("MessungHandler", new MessungHandler());
		xmlRpcServer.addHandler("DataFrameHandler", new DataFrameHandler());
		xmlRpcServer.addHandler("PreferenceHandler", new PreferenceHandler());
		xmlRpcServer.addHandler("TokenHandler", new TokenHandler(apiKey,loginHandler));
		
		
		// OctetServer
		log.debug("Initialize OctetServer ...");
		octetServer = new OctetServer();
		octetServer.addHandler("OctetMatrixHandler", new OctetMatrixHandler());
				
		log.info("Now accepting requests...");

	}
	
	
	public static String getClientIpAddress() {
        return (String) threadLocal.get();
    }

	protected void doPost(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, java.io.IOException, NumberFormatException {
		
		try {

		ServletInputStream in = req.getInputStream();
		ServletOutputStream out = res.getOutputStream();
						
		
		String ip = req.getHeader("X-Forwarded-For");
		
		if (ip != null) {
			threadLocal.set(ip);
		} else {
			threadLocal.set(req.getRemoteAddr());		
		}
						
		
		String auth = req.getHeader("Authentication");
		if (auth != null)
			setAuth(auth);
		byte[] result;

		String type = req.getHeader("Response-Type");
		
		if (type == null || type.equals("text/xml")) {
			if (auth == null) {
				result = xmlRpcServer.execute(in);
			} else {
				result = xmlRpcServer.execute(in, sessionId, userId);								
			}
			res.setContentType("text/xml");
			res.setContentLength(result.length);
			out.write(result);
			res.getOutputStream().flush();
		} else if (type.equals("application/octet-stream")) {
			try {
				res.setContentType("application/octet-stream");
				octetServer.execute(in, out, sessionId, userId);
			} catch (OctetServerException e) {
				throw new ServletException(e.getMessage());
			}
		}
		
		} finally {
			threadLocal.remove();
		}
				
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		PrintWriter w = resp.getWriter();
		w.print("<h1>BayEOS Server ");
		w.print(version);
		w.print("</h1>");
	
	}

	
	private void setAuth(String auth) throws ServletException {
		// decode string
		String sesus = new String(Base64.decode(auth.getBytes())).trim();
		// split
		StringTokenizer st = new StringTokenizer(sesus, ":");
		sessionId = new Integer(st.nextToken());
		userId = new Integer(st.nextToken());

		if (sessionId == null || userId == null) {
			throw new ServletException("Missing header information.");
		}
	}

	/**
	 * Destroys the servlet.
	 */
	public void destroy() {
		super.destroy();
		log.info("Destroying servlet XMLServlet ...");		
		threadLocal.remove();
		threadLocal = null;
	}
	
	
}
