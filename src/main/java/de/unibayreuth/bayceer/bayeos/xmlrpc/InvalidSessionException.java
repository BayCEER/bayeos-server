package de.unibayreuth.bayceer.bayeos.xmlrpc;

import org.apache.xmlrpc.XmlRpcException;

public class InvalidSessionException extends XmlRpcException {
    
    public InvalidSessionException(int code, String msg){
        super(code,msg);
    }
    
}
