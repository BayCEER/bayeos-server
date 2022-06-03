/**
 * 
 */
package de.unibayreuth.bayceer.bayeos.xmlrpc.handler;

import java.sql.SQLException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.xmlrpc.ConnectionPool;
import de.unibayreuth.bayceer.bayeos.xmlrpc.XmlRpcUtils;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IObjektArtHandler;

/**
 * @author oliver
 *
 */
public class ObjektArtHandler implements IObjektArtHandler {
	
	

	/* 
	 * 
	 * @see de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.IObjektArtHandler#getObjektArts()
	 */
	@Override
	public Vector getObjektArts() throws XmlRpcException {				
		try {
			String sql = "select uname from art_objekt order by 1;";			 
			return XmlRpcUtils.getRows(ConnectionPool.getPooledConnection(), sql);											
		} catch (SQLException e){
			throw new XmlRpcException(0,e.getMessage());
		} 
		
	}

}
