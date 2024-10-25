package de.unibayreuth.bayceer.bayeos.server;

import java.net.InetSocketAddress;

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
				
		options.addOption(Option.builder("s").longOpt("server_hostname").hasArg().argName("SRV_HOSTNAME").desc("server hostname").build());		
		options.addOption(Option.builder("p").longOpt("server_port").hasArg().argName("SRV_PORT").desc("server port number").type(Integer.class).build());
		options.addOption(Option.builder("c").longOpt("server_context_path").hasArg().argName("SRV_CONTEXT_PATH").desc("server context path").build());
	
		options.addOption(Option.builder("h").longOpt("db_hostname").hasArg().argName("DB_HOSTNAME").desc("database hostname").build());
		options.addOption(Option.builder("d").longOpt("db_name").hasArg().argName("DB_NAME").desc("database name").build());
		options.addOption(Option.builder("u").longOpt("db_username").hasArg().argName("DB_USERNAME").desc("database user name").build());
		options.addOption(Option.builder("w").longOpt("db_password").hasArg().argName("DB_PASSWORD").desc("database user password").build());
						
		options.addOption(Option.builder("k").longOpt("api-key").hasArg().argName("API-KEY").desc("api key to sign user token").build());
		options.addOption(Option.builder("t").longOpt("api-tz").hasArg().argName("API-TZ").desc("api default time zone").build());		

		// create the parser
		CommandLineParser parser = new DefaultParser();
		try {
			// parse the command line arguments
			CommandLine cmdLine = parser.parse(options, args);

			ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
			context.setContextPath("/");
			
			context.setInitParameter("db_hostname", cmdLine.getOptionValue("db_hostname"));
			context.setInitParameter("db_name", cmdLine.getOptionValue("db_name"));
			context.setInitParameter("db_username", cmdLine.getOptionValue("db_username"));
			context.setInitParameter("db_password", cmdLine.getOptionValue("db_password"));
			context.setInitParameter("api-key", cmdLine.getOptionValue("api-key"));
			context.setInitParameter("api-tz", cmdLine.getOptionValue("api-tz"));
						
			Integer port = cmdLine.getParsedOptionValue("server_port");
			log.info("Running server on port:" + port);

			Server jettyServer = new Server(
					new InetSocketAddress(cmdLine.getOptionValue("server_hostname"), port));
			jettyServer.setHandler(context);

			String contextPath = cmdLine.getOptionValue("server_context_path");
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
