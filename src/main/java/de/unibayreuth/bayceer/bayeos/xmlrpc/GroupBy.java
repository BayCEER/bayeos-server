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
  public class GroupBy {
    private final String name;
    private GroupBy(String name) {this.name = name;}
    public String toString() {return name;}     
    public static final GroupBy Time = new GroupBy("time");
    public static final GroupBy Id = new GroupBy("id");
  }

