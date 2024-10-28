package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.junit.Test;

import de.unibayreuth.bayceer.bayeos.client.AbstractClientTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.lang.Strings;
import io.jsonwebtoken.security.Keys;

@SuppressWarnings({ "rawtypes", "unchecked", "static-access" })
public class TestTokenHandlerIT extends AbstractClientTest {

	private static final String VALID_USERID = "100004";
	private static final String INVALID_USERID = "4";
	private static final String VALID_APIKEY = "01234567012345670123456701234567";
	private static final String INVALID_APIKEY = "00234567012345670123456701234567";
	
	
	@Test 
	public void testToken() {
				
				 
		Date iat = Date.from(ZonedDateTime.parse("2024-04-04T11:50+01:00").toInstant());
		Date eat = Date.from(ZonedDateTime.parse("2024-10-04T11:50+01:00").toInstant());
		 					
		String token = Jwts.builder().subject(VALID_USERID).issuedAt(iat)
				.expiration(eat).signWith(Keys.hmacShaKeyFor(VALID_APIKEY.getBytes())).compact();;
				
		assertNotNull(token);
		// Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMDAwMDQiLCJpYXQiOjE3MTIyMjc4MDAsImV4cCI6MTcyODAzOTAwMH0.r9VG2eUmWwET-8ZbhA4RF5-pMkYzs7lI6tYLJ4d0NWw
					
		String[] com = Strings.tokenizeToStringArray(token, ".");
		
		String s = new String(Base64.getDecoder().decode(com[1]));
		// {"sub":"100004","iat":1712227800,"exp":1728039000}
		// iat and eat as seconds since the epoch
		
		
		assertNotNull(s);
				
				
	}

	
	@Test
	public void testCreateTokenWithInvalidCredentials()  {
		Vector a = new Vector();
		a.add("root");
		a.add("adfsasf");		
		try {
			cli.getXmlRpcClient().execute("TokenHandler.createLoginToken", a);
		fail();
		} catch (XmlRpcException e) {			
			System.out.println(e.getMessage());
		}
	}
		
	
	@Test
	public void testCreateAndUseToken() throws XmlRpcException, MalformedURLException {
		
		Vector a = new Vector();
		a.add(user);
		a.add(password);				
		String token = (String) cli.getXmlRpcClient().execute("TokenHandler.createLoginToken", a);		
		assertNotNull(token);
					
		Vector r = (Vector)createSessionByToken(token);
		assertNotNull(r);
		Integer sessionID = (Integer) r.firstElement();
		assertNotNull(sessionID);
		Integer userID = (Integer) r.lastElement();
		assertNotNull(userID);
	}
	
	

	@Test
	public void testExpiredToken() throws MalformedURLException {
		Date now = new Date();		
		Calendar c = GregorianCalendar.getInstance();
		c.setTime(now);
		c.add(Calendar.DAY_OF_MONTH, -10);		
		Date eat = c.getTime();
		c.add(Calendar.DAY_OF_MONTH, -10);
		Date iat = c.getTime();				
		String token = Jwts.builder().subject(VALID_USERID).issuedAt(iat)
				.expiration(eat).signWith(Keys.hmacShaKeyFor(VALID_APIKEY.getBytes())).compact();;		
		try {
			createSessionByToken(token);
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		}
	}
	
	
	@Test
	public void testInvalidSignedToken() throws MalformedURLException {
					
		Calendar c = GregorianCalendar.getInstance();
		c.add(Calendar.DAY_OF_MONTH, -10);		
		Date iat = c.getTime();		
		c = GregorianCalendar.getInstance();
		c.add(Calendar.DAY_OF_MONTH, 10);
		Date eat = c.getTime();
		
		String token = Jwts.builder().subject(VALID_USERID).issuedAt(iat)
				.expiration(eat).signWith(Keys.hmacShaKeyFor(INVALID_APIKEY.getBytes())).compact();;		
		
		try {
			createSessionByToken(token);
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		}
	}

	@Test
	public void testInvalidTokenUserID() throws MalformedURLException {
		
		Calendar c = GregorianCalendar.getInstance();
		c.add(Calendar.DAY_OF_MONTH, -10);		
		Date iat = c.getTime();		
		c = GregorianCalendar.getInstance();
		c.add(Calendar.DAY_OF_MONTH, 10);
		Date eat = c.getTime();
		
		String token = Jwts.builder().subject(INVALID_USERID).issuedAt(iat)
				.expiration(eat).signWith(Keys.hmacShaKeyFor(VALID_APIKEY.getBytes())).compact();;		
		
		try {
			createSessionByToken(token);
			fail();
		} catch (XmlRpcException e) {
			System.out.println(e.getMessage());
		}
		
		

	}
	
	private Object createSessionByToken(String token) throws XmlRpcException, MalformedURLException {		
		XmlRpcClient xmlRpcClient = new XmlRpcClient(url);
		Vector v = new Vector();
		v.add(token);
		return xmlRpcClient.execute("LoginHandler.createSessionByToken", v);
		
	}

}
