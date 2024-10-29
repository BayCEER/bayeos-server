package de.unibayreuth.bayceer.bayeos.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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

		
		// All options must be set on startup!
		
		Options options = new Options();
				
		options.addOption(Option.builder("c").longOpt("config_path").hasArg().argName("CONFIG_PATH").desc("server config path").build());
	
		// create the parser
		CommandLineParser parser = new DefaultParser();
		try {
			// parse the command line arguments
			CommandLine cmdLine = parser.parse(options, args);
			
			String confFile = cmdLine.getOptionValue("config_path", "/etc/bayeos-server") + File.separator + "application.properties";
			
			final Properties prop = new Properties();		
			try {
				prop.load(new FileInputStream(confFile));
			} catch (IOException e) {
				log.error(String.format("Failed to read application properties from file:%s",confFile));	
				System.exit(-1);
			}		
									

			ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
			context.setContextPath("/");
			
			context.setInitParameter("db.hostname", prop.getProperty("db.hostname"));
			context.setInitParameter("db.name", prop.getProperty("db.name"));
			context.setInitParameter("db.username", prop.getProperty("db.username"));
			context.setInitParameter("db.password", prop.getProperty("db.password"));			
			context.setInitParameter("server.tz", prop.getProperty("server.tz"));			
			context.setInitParameter("server.configPath", cmdLine.getOptionValue("config_path", "/etc/bayeos-server"));
																		
			Integer port = Integer.valueOf(prop.getProperty("server.port"));
			log.info("Running server on port:" + port);

			Server jettyServer = new Server(
					new InetSocketAddress(prop.getProperty("server.hostname"), port));
			jettyServer.setHandler(context);

			String contextPath = prop.getProperty("server.contextPath");
			log.info("Adding servlet on path:" + contextPath);

			ServletHolder jerseyServlet = context.addServlet((Class<? extends Servlet>) XMLServlet.class, contextPath);
			jerseyServlet.setInitOrder(0);

			log.info("Server starting...");
			try {
				jettyServer.start();
				jettyServer.join();
			} finally {
				log.info("Server stopped.");
				jettyServer.destroy();
			}

		} catch (ParseException exp) {
			// oops, something went wrong
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
		} finally {

		}

	}
	
}
