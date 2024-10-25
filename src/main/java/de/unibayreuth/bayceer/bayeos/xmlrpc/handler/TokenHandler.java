package de.unibayreuth.bayceer.bayeos.xmlrpc.handler;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.crypto.SecretKey;

import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ITokenHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public class TokenHandler extends AccessHandler implements ITokenHandler {
	
	private static final int VALID_YEARS = 1;
	private SecretKey apiKey;	

	public TokenHandler(SecretKey apiKey) {
		this.apiKey = apiKey;			
	}
	
	@Override
	/*
	 * Returns a JWS (with key signed JWT)
	 */
	public String createLoginToken() throws XmlRpcException {		
		Date now = new Date();
		Calendar c = GregorianCalendar.getInstance();
		c.setTime(now);
		c.add(Calendar.YEAR, VALID_YEARS);
		Date exp = c.getTime();				
		String token = Jwts.builder().subject(getUserId().toString()).issuedAt(now).expiration(exp).signWith(apiKey).compact(); 
		return token;
	}

}
