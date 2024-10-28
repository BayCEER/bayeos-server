package de.unibayreuth.bayceer.bayeos.xmlrpc.handler;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.crypto.SecretKey;

import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ILoginHandler;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ITokenHandler;
import io.jsonwebtoken.Jwts;

public class TokenHandler extends AccessHandler implements ITokenHandler {
	
	private static final int VALID_YEARS = 1;
	private SecretKey apiKey;	
	
	private ILoginHandler loginHandler;

	public TokenHandler(SecretKey apiKey, ILoginHandler loginHandler) {
		this.apiKey = apiKey;			
		this.loginHandler = loginHandler;
	}
	
	@Override	
	public String createLoginToken(String userName, String passWord) throws XmlRpcException {
		
		Integer userId = loginHandler.authenticate(userName, passWord);			
		if (!userId.equals(getUserId())) {
			throw new XmlRpcException(401, String.format("Not authorized to create a token for user:%s",userId));
		}					
		Date now = new Date();
		Calendar c = GregorianCalendar.getInstance();
		c.setTime(now);
		c.add(Calendar.YEAR, VALID_YEARS);
		Date exp = c.getTime();				
		String token = Jwts.builder().subject(userId.toString()).issuedAt(now).expiration(exp).signWith(apiKey).compact(); 
		return token;
	}

}
