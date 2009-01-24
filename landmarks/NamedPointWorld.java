/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package landmarks;

import ij.*;
import ij.process.*;
import ij.io.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.text.*;


import java.io.*;

import math3d.Point3d;

import vib.transforms.OrderedTransformations;

import java.awt.*;
import java.awt.event.*;

import java.io.*;
import java.util.regex.*;

import tracing.PathAndFillManager;

import vib.FastMatrix;

/* This class replaces the old NamedPoint class, the difference being
   that these objects hold world co-ordinates rather than image
   co-ordinates...  i.e. the x, y, z in this class have already been
   scaled by the pixel(Width|Height|Depth) values in the image's
   calibration information.
 */

public class NamedPointWorld {

	public double x,y,z;
	public boolean set;

	String name;

	public NamedPointWorld(String name,
			  double x,
			  double y,
			  double z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.name = name;
		this.set = true;
	}

	public NamedPointWorld(String name) {
		this.name = name;
		this.set = false;
	}

	public void transformWith(FastMatrix m) {
		m.apply(x,y,z);
		x=m.x;
		y=m.y;
		z=m.z;
	}

	public NamedPointWorld transformWith(OrderedTransformations o) {
		double[] result=new double[3];
		o.apply(x,y,z,result);
		return new NamedPointWorld(name,result[0],result[1],result[2]);
	}

	public static String escape(String s) {
		String result = s.replaceAll("\\\\","\\\\\\\\");
		result = result.replaceAll("\\\"","\\\\\"");
		return result;
	}

	public static String unescape(String s) {
		StringBuffer result = new StringBuffer( s );
		int startNextSearch = 0;
		while( true ) {
			int nextBackslash = result.indexOf( s, startNextSearch );
			if( nextBackslash < 0 )
				return result.toString();
			result.deleteCharAt(nextBackslash);
			startNextSearch = nextBackslash + 1;
		}
	}

	public Point3d toPoint3d() {
		return new Point3d(x,y,z);
	}

	public String getName() {
		return name;
	}

	public String toXMLElement() {
		StringBuffer result = new StringBuffer("<pointworld set=\"");
		result.append( set );
		result.append( "\" name=\"" );
		result.append( PathAndFillManager.escapeForXMLAttributeValue(name) );
		result.append( "\"");
		if( set ) {
			result.append( " x=\"" );
			result.append( x );
			result.append( "\" y=\"" );
			result.append( y );
			result.append( "\" z=\"" );
			result.append( z );
			result.append( "\"" );
		}
		result.append( "/>" );
		return result.toString();
	}

	public String toYAML() {
		String line = "\""+
			escape(name)+
			"\": [ "+
			x+", "+
			y+", "+
			z+" ]";
		return line;
	}

	public String toString() {
		return ""+name+
			" at "+x+
			", "+y+
			", "+z;
	}
}
