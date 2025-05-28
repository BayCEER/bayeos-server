package de.unibayreuth.bayceer.bayeos.client;

import org.apache.xmlrpc.XmlRpcException;

import junit.framework.TestCase;

public class ConnectTest  extends TestCase {
	 
	public String user = "root";
	public String password = "bayeos";
	public String url = "http://localhost:5532/XMLServlet";
	
	
	public void testWrongUser(){		
		Client c =Client.getInstance();		
	    try {
			c.connect(url,"asdf","false");
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		} 
		
	}
	
	
	public void testWrongPassWord(){		
		Client c =Client.getInstance();		
	    try {
			c.connect(url,user,"wrong");
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		} 
		
	}
	
	
	public void testEmptyPassWord(){		
		Client c =Client.getInstance();		
	    try {
			c.connect(url,user,"");
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		} 
		
	}

	
	public void testDefaultPassWord(){		
		Client c =Client.getInstance();		
	    try {
			c.connect(url,user,password);			
		} catch (XmlRpcException e) {			
			fail();
		} finally {
			try {
				c.close();
			} catch (XmlRpcException e) {				
				fail(e.getMessage());
			}
		}
		
	}


}
