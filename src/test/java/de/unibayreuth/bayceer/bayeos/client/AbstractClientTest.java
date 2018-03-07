package de.unibayreuth.bayceer.bayeos.client;


import java.io.IOException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;

import junit.framework.TestCase;


public abstract class AbstractClientTest extends TestCase {

	public XmlRpcClient cli;
		
	public Logger log = Logger.getLogger(AbstractClientTest.class);

	public void setUp() throws XmlRpcException, IOException {
		BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.DEBUG);
		
		String url = System.getProperty("server.url");
		Client client = Client.getInstance();
		client.connect(url, System.getProperty("server.user"), System.getProperty("server.password"));
		cli = client.getXmlRpcClient();
	}

	public void tearDown() throws XmlRpcException {
		cli.close();
		BasicConfigurator.resetConfiguration();
	}

}
