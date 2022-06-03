package de.unibayreuth.bayceer.bayeos.server;

import java.net.InetSocketAddress;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unibayreuth.bayceer.bayeos.xmlrpc.XMLServlet;
import jakarta.servlet.Servlet;

public class App {
	
	private final static Logger log = LoggerFactory.getLogger(App.class);
	

	public static void main(String[] args) throws Exception {
		
		String address = "localhost";
		Integer port = 5532;
		String contextPath = "/XMLServlet";
		
		if (args.length == 3) {
			address = args[0];			
			port = Integer.parseInt(args[1]);
			contextPath = args[2];			
			
		}
		
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
                         
        log.info("Running server on port:" + port);
                
        Server jettyServer = new Server(new InetSocketAddress(address, port));
        jettyServer.setHandler(context);
        
        log.info("Adding servlet on path:" + contextPath);

        ServletHolder jerseyServlet = context.addServlet((Class<? extends Servlet>) XMLServlet.class, contextPath);
        jerseyServlet.setInitOrder(0);
        
                        
        try {
        	log.info("Server starting...");
            jettyServer.start();
            jettyServer.join();            
        } finally {
        	log.info("Server stopped.");
            jettyServer.destroy();
        }

	}

}
