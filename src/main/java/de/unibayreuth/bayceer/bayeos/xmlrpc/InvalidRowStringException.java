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
package de.unibayreuth.bayceer.bayeos.xmlrpc;
 public class InvalidRowStringException extends Exception {
    private Exception hiddenException;
    public InvalidRowStringException(String Message, Exception e){
        super(Message);
        hiddenException = e;
    }
    public Exception getHiddenException(){
        return hiddenException;
    }
}

