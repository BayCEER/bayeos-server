package de.unibayreuth.bayceer.bayeos.client;


import java.io.IOException;
import java.util.Properties;

import junit.framework.TestCase;


import org.apache.xmlrpc.XmlRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractClientTest extends TestCase {

	public Client cli;

	Properties p = new Properties();
	
	public String url;
	public String user;
	public String password;
	
	Logger logger = LoggerFactory.getLogger(AbstractClientTest.class);

	public void setUp() throws XmlRpcException, IOException {

		
		url = "http://develop:5532/XMLServlet";
		cli = Client.getInstance();
		user = "root";
		password = "bayeos";
		cli.connect(url,user,password);
	}

	public void tearDown() throws XmlRpcException {
		cli.close();
	}

}
