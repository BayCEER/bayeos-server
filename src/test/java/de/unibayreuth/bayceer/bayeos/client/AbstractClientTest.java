package de.unibayreuth.bayceer.bayeos.client;


import java.io.IOException;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import junit.framework.TestCase;


public abstract class AbstractClientTest extends TestCase {

	public XmlRpcClient cli;
		
	public Logger log = LoggerFactory.getLogger(AbstractClientTest.class);

	public void setUp() throws XmlRpcException, IOException {
			
		String url = System.getProperty("server.url");
		Client client = Client.getInstance();
		client.connect(url, System.getProperty("server.user"), System.getProperty("server.password"));
		cli = client.getXmlRpcClient();
	}

	public void tearDown() throws XmlRpcException {
		cli.close();
	}

}
