package de.unibayreuth.bayceer.bayeos.server;

import java.net.InetSocketAddress;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import de.unibayreuth.bayceer.bayeos.xmlrpc.XMLServlet;

public class App {
	
	private final static Logger log = Logger.getRootLogger();

	// run with -Dlog4j.configuration=file:/etc/bayeos-server/log4j.properties

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

        ServletHolder jerseyServlet = context.addServlet(XMLServlet.class, contextPath);
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
