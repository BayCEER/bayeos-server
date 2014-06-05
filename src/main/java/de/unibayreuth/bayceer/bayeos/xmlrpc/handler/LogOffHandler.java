/*******************************************************************************
 * Copyright (c) 2011 University of Bayreuth - BayCEER.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     University of Bayreuth - BayCEER - initial API and implementation
 ******************************************************************************/
package de.unibayreuth.bayceer.bayeos.xmlrpc.handler;
/*
 * LogOffHandler.java
 *
 * Created on 27. August 2002, 16:25
 */

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import de.unibayreuth.bayceer.bayeos.xmlrpc.Session;
import de.unibayreuth.bayceer.bayeos.xmlrpc.handler.inf.ILogOffHandler;

/**
 *
 * @author  oliver
 */
public class LogOffHandler extends AccessHandler implements ILogOffHandler {
      final static Logger logger = Logger.getLogger(LogOffHandler.class.getName());
      
      
      public Boolean terminateSession() throws XmlRpcException{
    	  
    	  if (!Session.loggedIn(sessionId,userId)) {
              throw new XmlRpcException(1,"Session " + sessionId + " does not exist.");
          }
          Boolean ret = Session.terminate(sessionId,userId);
              if (ret.booleanValue()) {
                logger.info("Terminated session " + sessionId + " for userid " + userId + ".");
              } else {
                logger.warn("Could not terminate session + " + sessionId + " for userid " + userId+ ".");
              }
              return ret; 	  
      }
    
          

    }
      
        

