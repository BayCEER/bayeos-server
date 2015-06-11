package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.util.Hashtable;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.AbstractClientTest;

public class TestPreferenceHandler  extends AbstractClientTest{

	
	@Test
	public void test() throws XmlRpcException {
		assertTrue((boolean) cli.execute("PreferenceHandler.setPreference","MyApplication","MyKey","MyValue" ));
		Hashtable<String, String> map = (Hashtable<String, String>) cli.execute("PreferenceHandler.getPreferences", "MyApplication");
		assertEquals("MyValue", map.get("MyKey"));
		
		assertTrue((boolean) cli.execute("PreferenceHandler.setPreference","MyApplication","MyKey",null ));		
		map = (Hashtable<String, String>) cli.execute("PreferenceHandler.getPreferences", "MyApplication");		
		assertEquals(0, map.size());
						
	}
	
	@Test
	public void testTwoPrefs() throws XmlRpcException {
		assertTrue((boolean) cli.execute("PreferenceHandler.setPreference","MyApplication","Key1","Value1" ));		
		assertTrue((boolean) cli.execute("PreferenceHandler.setPreference","MyApplication","Key2","Value2" ));
		
		Hashtable<String, String> map = (Hashtable<String, String>) cli.execute("PreferenceHandler.getPreferences", "MyApplication");
		assertEquals("Value1", map.get("Key1"));
		assertEquals("Value2", map.get("Key2"));
		
		assertTrue((boolean) cli.execute("PreferenceHandler.deletePreferences","MyApplication"));
		
		map = (Hashtable<String, String>) cli.execute("PreferenceHandler.getPreferences", "MyApplication");
		assertEquals(0, map.size());		
	}
	
	

}
