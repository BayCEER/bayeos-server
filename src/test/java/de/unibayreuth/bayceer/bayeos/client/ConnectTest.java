package de.unibayreuth.bayceer.bayeos.client;

import java.util.Properties;

import org.apache.xmlrpc.XmlRpcException;

import junit.framework.TestCase;

public class ConnectTest   extends TestCase {
	
	public String url;
	public String user;
	public String password;
	public Client c;

	Properties p = new Properties();
	
	 
	@Override
	protected void setUp() throws Exception {
		super.setUp();
	
		p.load(getClass().getResourceAsStream("/test.properties"));
		url = p.getProperty("url");
		c = Client.getInstance();		
		password = p.getProperty("password");
		user = p.getProperty("user");
		
	}
	
	@Override
	protected void tearDown() throws Exception {
		// TODO Auto-generated method stub
		super.tearDown();
		
		if (c.isConnected()) {
			c.close();	
		}
		
		
	}
	
	public void testWrongUser(){		
				
	    try {
			c.connect(url,"asdf","false");
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		} 
		
	}
	
	
	public void testWrongPassWord(){		
				
	    try {
			c.connect(url,user,"wrong");
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		} 
		
	}
	
	
	public void testEmptyPassWord(){		
				
	    try {
			c.connect(url,user,"");
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		} 
		
	}

	
	public void testDefaultPassWord(){		
				
	    try {
			c.connect(url,user,password);			
		} catch (XmlRpcException e) {			
			fail();
		} 
		
	}


}
