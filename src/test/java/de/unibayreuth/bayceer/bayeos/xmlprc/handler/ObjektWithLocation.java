package de.unibayreuth.bayceer.bayeos.xmlprc.handler;

import java.util.Date;

import de.unibayreuth.bayceer.bayeos.objekt.ArrayElement;
import de.unibayreuth.bayceer.bayeos.objekt.ArraySerializable;

@ArraySerializable
public class ObjektWithLocation {
	public ObjektWithLocation() {
		
	}
	@ArrayElement(0)
	public Integer id; 
	@ArrayElement(1)
	public Integer id_super;
	@ArrayElement(2)
	public Integer id_art ;
	@ArrayElement(3)
	public Date plan_start;
	@ArrayElement(4)
	public Date plan_end;
	@ArrayElement(5)
	public Date rec_start;
	@ArrayElement(6)
	public Date rec_end;
	@ArrayElement(7)
	public String name;
	@ArrayElement(8)
	public String path;
	@ArrayElement(9)
	public String art;
	@ArrayElement(10)	
	public Double lon;
	@ArrayElement(11)
	public Double lat;
	@ArrayElement(12)
	public Double alt;
	
	
	
	
}