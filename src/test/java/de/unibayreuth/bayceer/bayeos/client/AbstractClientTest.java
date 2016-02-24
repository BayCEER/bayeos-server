package de.unibayreuth.bayceer.bayeos.client;


import java.io.IOException;
import java.util.Properties;

import junit.framework.TestCase;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.client.Client;


public abstract class AbstractClientTest extends TestCase {
/* 
 * Run a single test with: mvn -Dtest=<TestClass> test
 * 
 */
	public XmlRpcClient cli;
	Properties p = new Properties();
	
	public Logger log = Logger.getLogger(AbstractClientTest.class);

	public void setUp() throws XmlRpcException, IOException {
		BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.DEBUG);
		p.load(getClass().getResourceAsStream("/test.properties"));
		String url = p.getProperty("bayeos.url");
		Client client = Client.getInstance();
		client.connect(url, p.getProperty("bayeos.user.name"), p.getProperty("bayeos.user.password"));
		cli = client.getXmlRpcClient();
	}

	public void tearDown() throws XmlRpcException {
		cli.close();
		BasicConfigurator.resetConfiguration();
	}

}
