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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.Base64;
import org.apache.xmlrpc.OctetServer;
import org.apache.xmlrpc.OctetServerException;
import org.apache.xmlrpc.XmlRpcServer;

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
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.ToolsHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.TreeHandler;

public class XMLServlet extends HttpServlet {

	protected static XmlRpcServer xmlRpcServer;
	protected static OctetServer octetServer;
	protected final static Logger log = Logger.getRootLogger();
	
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
	
		// TimeZone 
		String timeZone = prop.getProperty("timeZone","GMT+1");								
		System.setProperty("user.timezone", timeZone);
		TimeZone.setDefault(TimeZone.getTimeZone(timeZone));		
		log.info("Default timezone: " + TimeZone.getDefault().getID());
				
		
		version = prop.getProperty("version");
		
		// Encoding 
		log.info("Encoding:" + System.getProperty("file.encoding"));
		

		// Version 
		String version = prop.getProperty("version","");
		LookUpTableHandler.setVersion(version);
		
		
		// Connection 
		ConnectionPool.setConnection(prop.getProperty("url","jdbc:postgresql://localhost/bayeos"), 
				prop.getProperty("username","bayeos"), prop.getProperty("password","4336bc9de7a6b11940e897ee22956d51"));

		// xml-rpc
		log.debug("Initialize XmlRpcServer ...");
		xmlRpcServer = new XmlRpcServer();
					
		xmlRpcServer.addHandler("LoginHandler", new LoginHandler());
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
						
		
		threadLocal.set(req.getRemoteAddr());				
		
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
