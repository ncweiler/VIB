/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package util;

import ij.ImageJ;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.PlugIn;
import ij.measure.Calibration;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import util.BatchOpener;
import landmarks.NamedPointSet;
import math3d.Point3d;
import vib.FastMatrix;
import vib.TransformedImage;
import vib.Resample_;
import distance.Correlation;
import distance.MutualInformation;

import landmarks.Affine_From_Landmarks;
import landmarks.Bookstein_From_Landmarks;
import landmarks.Rigid_From_Landmarks;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;

import util.FileCreation;

import util.Overlay_Registered;

import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CMTK_Transformation {

	File originalFile;
	Inverse inverse;

	public CMTK_Transformation( ) { }

	int dimsx = -1, dimsy = -1, dimsz = -1;
	double domainx = Double.MIN_VALUE, domainy = Double.MIN_VALUE, domainz = Double.MIN_VALUE;
	double originx = Double.MIN_VALUE, originy = Double.MIN_VALUE, originz = Double.MIN_VALUE;
	double coeffs[][];

	double deltax = -1, deltay = -1, deltaz = -1;

	protected CMTK_Transformation( int dimsx, int dimsy, int dimsz,
				       double domainx, double domainy, double domainz,
				       double originx, double originy, double originz,
				       double coeffs[][] ) {
		this.dimsx = dimsx;
		this.dimsy = dimsy;
		this.dimsz = dimsz;
		this.domainx = domainx;
		this.domainy = domainy;
		this.domainz = domainz;
		this.originx = originx;
		this.originy = originy;
		this.originz = originz;
		this.coeffs = coeffs;

		this.deltax = domainx / (dimsx - 3);
		this.deltay = domainy / (dimsy - 3);
		this.deltaz = domainz / (dimsz - 3);
	}

	public static double degToRad( double d ) {
		return (d * Math.PI) / 180;
	}


	public byte trilinearInterpolate( double image_x, double image_y, double image_z, int width, int height, int depth, byte [][] v ) {

		double x_d = image_x - Math.floor(image_x);
		double y_d = image_y - Math.floor(image_y);
		double z_d = image_z - Math.floor(image_z);

		int x_f = (int)Math.floor(image_x); int x_c = (int)Math.ceil(image_x);
		int y_f = (int)Math.floor(image_y); int y_c = (int)Math.ceil(image_y);
		int z_f = (int)Math.floor(image_z); int z_c = (int)Math.ceil(image_z);

		/* Check that these values aren't
		   poking off the edge of the screen -
		   if so then make them zero. */

		double fff;
		double cff;
		double fcf;
		double ccf;

		double ffc;
		double cfc;
		double fcc;
		double ccc;

		if( (x_f < 0) || (x_c < 0) || (y_f < 0) || (y_c < 0) || (z_f < 0) || (z_c < 0) ||
		    (x_f >= width) || (x_c >= width) || (y_f >= height) || (y_c >= height) || (z_f >= depth) || (z_c >= depth) ) {

			fff = 0;
			cff = 0;
			fcf = 0;
			ccf = 0;
			ffc = 0;
			cfc = 0;
			fcc = 0;
			ccc = 0;

		} else {

			fff = v[z_f][width*y_f+x_f]&0xFF;
			cff = v[z_c][width*y_f+x_f]&0xFF;

			fcf = v[z_f][width*y_c+x_f]&0xFF;
			ccf = v[z_c][width*y_c+x_f]&0xFF;

			ffc = v[z_f][width*y_f+x_c]&0xFF;
			cfc = v[z_c][width*y_f+x_c]&0xFF;

			fcc = v[z_f][width*y_c+x_c]&0xFF;
			ccc = v[z_c][width*y_c+x_c]&0xFF;
		}

		// Now we should be OK to do the interpolation for real:

		double i1 = (1 - z_d) * (fff) + (cff) * z_d;
		double i2 = (1 - z_d) * (fcf) + (ccf) * z_d;

		double j1 = (1 - z_d) * (ffc) + (cfc) * z_d;
		double j2 = (1 - z_d) * (fcc) + (ccc) * z_d;

		double w1 = i1 * (1 - y_d) + i2 * y_d;
		double w2 = j1 * (1 - y_d) + j2 * y_d;

		double value_f = w1 * (1 - x_d) + w2 * x_d;

		int value = (int)Math.round(value_f);
		if( (value < 0) || (value > 255) )
			throw new RuntimeException("BUG: Out of range value!");

		return (byte)value;
	}


	public ImagePlus transform( ImagePlus templateImage, ImagePlus modelImage ) {

		int modelWidth = modelImage.getWidth();
		int modelHeight = modelImage.getHeight();
		int modelDepth = modelImage.getStackSize();
		double modelXSpacing = 1;
		double modelYSpacing = 1;
		double modelZSpacing = 1;
		Calibration modelCalibration = modelImage.getCalibration();
		if( modelCalibration != null ) {
			modelXSpacing = modelCalibration.pixelWidth;
			modelYSpacing = modelCalibration.pixelHeight;
			modelZSpacing = modelCalibration.pixelDepth;
		}

		int templateWidth = templateImage.getWidth();
		int templateHeight = templateImage.getHeight();
		int templateDepth = templateImage.getStackSize();
		double templateXSpacing = 1;
		double templateYSpacing = 1;
		double templateZSpacing = 1;
		Calibration templateCalibration = templateImage.getCalibration();
		if( templateCalibration != null ) {
			templateXSpacing = templateCalibration.pixelWidth;
			templateYSpacing = templateCalibration.pixelHeight;
			templateZSpacing = templateCalibration.pixelDepth;
		}

		IJ.showProgress( 0 );

		double [] result = new double[3];
		byte [][] imageBytes = new byte[templateDepth][templateWidth*templateHeight];

		ImageStack modelStack = modelImage.getStack();
		byte [][] modelBytes = new byte[modelDepth][];
		for( int z = 0; z < modelDepth; ++z )
			modelBytes[z] = (byte[])modelStack.getPixels( z + 1 );

		for( int zi = 0; zi < templateDepth; ++zi ) {
			for( int yi = 0; yi < templateHeight; ++yi )
				for( int xi = 0; xi < templateWidth; ++xi ) {
					double x = xi * templateXSpacing;
					double y = yi * templateYSpacing;
					double z = zi * templateZSpacing;
					// System.out.println( "Mapping "+x+", "+y+", "+z );
					transformPoint( x, y, z, result );
					double xt = result[0];
					double yt = result[1];
					double zt = result[2];
					byte value = trilinearInterpolate(
						xt/modelXSpacing,
						yt/modelYSpacing,
						zt/modelZSpacing,
						modelWidth,
						modelHeight,
						modelDepth,
						modelBytes );
					imageBytes[zi][yi*templateWidth+xi] = value;
				}
			IJ.showProgress( zi / (double)( templateDepth + 1 ) );
		}
		ImageStack newStack = new ImageStack( templateWidth, templateHeight );
		for( int z = 0; z < templateDepth; ++z ) {
			ByteProcessor bp = new ByteProcessor( templateWidth, templateHeight );
			bp.setPixels( imageBytes[z] );
			newStack.addSlice( "", bp );
		}
		IJ.showProgress( 1.0 );
		ImagePlus resultImage = new ImagePlus( "Transformed", newStack );
		resultImage.setCalibration( templateCalibration );
		return resultImage;
	}

	public double bSpline( int l, double u ) {
		switch (l) {
		case 0:
			double oneMinusU = 1 - u;
			return (oneMinusU * oneMinusU * oneMinusU) / 6.0;
		case 1:
			return ( 3 * u * u * u
				 - 6 * u * u
				 + 4 ) / 6.0;
		case 2:
			return ( -3 * u * u * u
				 + 3 * u * u
				 + 3 * u
				 + 1 ) / 6.0;
		case 3:
			return ( u * u * u ) / 6.0;
		default:
			throw new RuntimeException("bSpline()'s first parameter must be one of 0, 1, 2 or 3");
		}
	}

	public void transformPoint( double x, double y, double z, double [] result ) {

		double cellxD = x / deltax;
		double cellyD = y / deltay;
		double cellzD = z / deltaz;

		int uncappedgridi = (int)cellxD;
		int uncappedgridj = (int)cellyD;
		int uncappedgridk = (int)cellzD;

		int gridi = Math.min( uncappedgridi, dimsx - 4 );
		int gridj = Math.min( uncappedgridj, dimsy - 4 );
		int gridk = Math.min( uncappedgridk, dimsz - 4 );

		double u = cellxD - uncappedgridi;
		double v = cellyD - uncappedgridj;
		double w = cellzD - uncappedgridk;

		result[0] = 0;
		result[1] = 0;
		result[2] = 0;

		{
			for( int l = 0; l < 4; ++l )
				for( int m = 0; m < 4; ++m )
					for( int n = 0; n < 4; ++n ) {
						int c = (gridi+l) + dimsx * ((gridj+m) + dimsy * (gridk+n));
						double splineProduct = bSpline(l,u) * bSpline(m,v) * bSpline(n,w);
						result[0] += splineProduct * coeffs[c][0];
						result[1] += splineProduct * coeffs[c][1];
						result[2] += splineProduct * coeffs[c][2];
					}
		}
	}

	/* This function is more-or-less cut-and-pasted from Greg
	 * Jefferis code in Affine.R (GPL-ed) */

	public static FastMatrix parseTypedStreamAffine( File f ) {

		double tx = 0, ty = 0, tz = 0;
		double rx = 0, ry = 0, rz = 0;
		double sx = 0, sy = 0, sz = 0;
		double shx = 0, shy = 0, shz = 0;
		double cx = 0, cy = 0, cz = 0;

		try {
			BufferedReader br = new BufferedReader( new FileReader(f) );

			String n = "([-\\.0-9]+)";
			String space = "[ \\t]+";
			String re = "^\t\t";
			re += "(xlate|rotate|scale|shear|center)";
			re += space + n + space + n + space + n;
			Pattern p = Pattern.compile(re);

			String line = br.readLine();

			while( line != null ) {

				Matcher m = p.matcher(line);
				if( m.find() ) {
					double a = Double.parseDouble( m.group(2) );
					double b = Double.parseDouble( m.group(3) );
					double c = Double.parseDouble( m.group(4) );
					String parameter = m.group(1);
					if( parameter.equals("xlate") ) {
						tx = a; ty = b; tz = c;
					} else if( parameter.equals("rotate") ) {
						rx = a; ry = b; rz = c;
					} else if( parameter.equals("scale") ) {
						sx = a; sy = b; sz = c;
					} else if( parameter.equals("shear") ) {
						shx = a; shy = b; shz = c;
					} else if( parameter.equals("center") ) {
						cx = a; cy = b; cz = c;
					}
				}

				line = br.readLine();
			}
		} catch( IOException e ) {
			IJ.error("IOException in parseTypedStreamAffine: "+e);
			return null;
		}
		double alpha = degToRad( rx );
		double theta = degToRad( ry );
		double phi = degToRad( rz );
		double cos0 = Math.cos( alpha );
		double sin0 = Math.sin( alpha );
		double cos1 = Math.cos( theta );
		double sin1 = Math.sin( theta );
		double cos2 = Math.cos( phi );
		double sin2 = Math.sin( phi );
		double sin0xsin1 = sin0 * sin1;
		double cos0xsin1 = cos0 * sin1;
		double [][] rval = new double[4][4];
		rval[0][0] =  cos1*cos2 * sx;
		rval[1][0] = -cos1*sin2 * sx;
		rval[2][0] = -sin1 * sx;
		rval[3][0] = 0;
		rval[0][1] =  (sin0xsin1*cos2 + cos0*sin2) * sy;
		rval[1][1] = (-sin0xsin1*sin2 + cos0*cos2) * sy;
		rval[2][1] =  sin0*cos1 * sy;
		rval[3][1] = 0;
		rval[0][2] =  (cos0xsin1*cos2 - sin0*sin2) * sz;
		rval[1][2] = (-cos0xsin1*sin2 - sin0*cos2) * sz;
		rval[2][2] =  cos0*cos1 * sz;
		rval[3][2] = 0;
		rval[3][3] = 1;
		double shears[] = new double[3];
		// generate shears
		// make a copy
		//rval2=rval
		shears[0] = shx;
		shears[1] = shy;
		shears[2] = shz;
		for( int i = 2; i >= 0; --i ) {
			for( int j = 0; j < 3; ++j ) {
				rval[j][i] += shears[i] * rval[j][(i+1)%3];
			}
		}

		// transform rotation center
		double [] cM = new double[3];
		cM[0] = cx*rval[0][0] + cy*rval[0][1] + cz*rval[0][2];
		cM[1] = cx*rval[1][0] + cy*rval[1][1] + cz*rval[1][2];
		cM[2] = cx*rval[2][0] + cy*rval[2][1] + cz*rval[2][2];

		// set translations
		rval[0][3] = tx - cM[0] + cx;
		rval[1][3] = ty - cM[1] + cy;
		rval[2][3] = tz - cM[2] + cz;

		if( false )
			for( int i = 0; i < rval.length; ++i ) {
				System.out.println("rval["+i+"]: "+rval[i][0]+", "+rval[i][1]+", "+rval[i][2]+", "+rval[i][3]);
			}

		return new FastMatrix( rval );
	}

	public static CMTK_Transformation parseTypedStreamWarp( File f ) {

		String n = "([\\-.0-9]+)";
		Pattern pSplineSection = Pattern.compile("^[ \\t]+spline_warp \\{");
		Pattern pEndOfSection = Pattern.compile("^[ \\t]*}");
		Pattern pDims = Pattern.compile("^[ \\t]*dims "+n+" "+n+" "+n);
		Pattern pDomain = Pattern.compile("^[ \\t]*domain "+n+" "+n+" "+n);
		Pattern pOrigin = Pattern.compile("^[ \\t]*origin "+n+" "+n+" "+n);
		Pattern pCoefficients = Pattern.compile("^[ \\t]*coefficients "+n+" "+n+" "+n);
		Pattern pThreeNumbers = Pattern.compile("^[ \\t]+"+n+" "+n+" "+n);

		int dimsx = -1, dimsy = -1, dimsz = -1;
		double domainx = Double.MIN_VALUE, domainy = Double.MIN_VALUE, domainz = Double.MIN_VALUE;
		double originx = Double.MIN_VALUE, originy = Double.MIN_VALUE, originz = Double.MIN_VALUE;

		double coeffs[][] = null;

		// We make lots of very strict assumptions about the
		// format of the file, since I don't have a grammar
		// for it, and there's no point in trying to guess how
		// flexible it is...

		try {
			byte[] buf = new byte[2];
			InputStream is = new FileInputStream(f);
                        is.read(buf, 0, 2);
                        is.close();

			boolean compressed = (buf[0] == (byte)0x1f) && (buf[1] == (byte)0x8b);

			if( compressed )
				is = new GZIPInputStream( new BufferedInputStream(new FileInputStream(f)) );
			else
				is = new BufferedInputStream(new FileInputStream(f));

			BufferedReader br = new BufferedReader(new InputStreamReader(is));

			String line;
			// Skip over everything up to "\tspline_warp"
			while( true ) {
				line = br.readLine();
				if( line == null ) {
					IJ.error("Couldn't find "+pSplineSection);
					return null;
				}
				Matcher m = pSplineSection.matcher(line);
				if( m.find() )
					break;
			}
			// Next we expect the an affine_xform section
			// so skip over that
			while( true ) {
				line = br.readLine();
				if( line == null ) {
					IJ.error("Couldn't find "+pEndOfSection);
					return null;
				}
				Matcher m = pEndOfSection.matcher(line);
				if( m.find() )
					break;
			}
			while( true ) {
				line = br.readLine();
				if( line == null )
					break;
				Matcher m;
				m = pDomain.matcher(line);
				if( m.find() ) {
					domainx = Double.parseDouble( m.group(1) );
					domainy = Double.parseDouble( m.group(2) );
					domainz = Double.parseDouble( m.group(3) );
					continue;
				}
				m = pDims.matcher(line);
				if( m.find() ) {
					dimsx = Integer.parseInt( m.group(1) );
					dimsy = Integer.parseInt( m.group(2) );
					dimsz = Integer.parseInt( m.group(3) );
					continue;
				}
				m = pOrigin.matcher(line);
				if( m.find() ) {
					originx = Double.parseDouble( m.group(1) );
					originy = Double.parseDouble( m.group(2) );
					originz = Double.parseDouble( m.group(3) );
					continue;
				}
				m = pCoefficients.matcher(line);
				if( m.find() ) {
					if( dimsx < 0 ) {
						IJ.error("Failed: got 'coefficients' before 'dims'");
						return null;
					}
					coeffs = new double[dimsx*dimsy*dimsz][3];
					coeffs[0][0] = Double.parseDouble( m.group(1) );
					coeffs[0][1] = Double.parseDouble( m.group(2) );
					coeffs[0][2] = Double.parseDouble( m.group(3) );
					int added = 1;
					while( true ) {
						line = br.readLine();
						m = pThreeNumbers.matcher(line);
						if( ! m.find() )
							break;
						coeffs[added][0] = Double.parseDouble( m.group(1) );
						coeffs[added][1] = Double.parseDouble( m.group(2) );
						coeffs[added][2] = Double.parseDouble( m.group(3) );
						++ added;
					}
					int pointsExpected = dimsx * dimsy * dimsz;
					if( pointsExpected != added ) {
						IJ.error("Number of coefficients ("+added+") didn't match expected number ("+pointsExpected+")");
						return null;
					}
					break;
				}
			}

		} catch( IOException e ) {
			IJ.error("IOException in parseTypedStreamWarp: "+e);
			return null;
		}

		if( domainx == Double.MIN_VALUE ) {
			IJ.error("Failed to find 'domain' line");
			return null;
		}

		if( originx == Double.MIN_VALUE ) {
			IJ.error("Failed to find 'origin' line");
			return null;
		}

		if( dimsx < 0 ) {
			IJ.error("Failed to find 'dims' line");
			return null;
		}

		if( coeffs == null ) {
			IJ.error("Failed to find 'coefficients' line");
			return null;
		}

		CMTK_Transformation result = new CMTK_Transformation(
			dimsx, dimsy, dimsz,
			domainx, domainy, domainz,
			originx, originy, originz,
			coeffs );
		result.originalFile = f;
		return result;
	}

	/* The CMTK creates a mapping from the template to the model.
	   In order to go back, we need to create an inverse
	   transformation.  We do this with a nearest neighbour
	   approach - this could be a lot better, but probably good
	   enough for my purposes, and it's fairly fast to implement.

	   We do this by mapping every point in the template to the
	   model space; then we mark the template co-ordinates and
	   distance to each point where that seems to be the nearest
	   for points in some neighbourhood around the transformed
	   point.  (Configured with 'pointsEitherSide'.)

	   This is a bad approach for a number of reasons: (a) off the
	   edges of the image, the nearest point is not very helpful
	   (b) the 'pointsEitherSide' business isn't very robust.

	   TODO: change this so that what we do is to is: for each
	   point in the template, map that point and all the adjacent
	   ones to the model.  Then look at the points contained
	   within each of the tetrahedra made by the original point
	   and three of the adjacent points - then we have more
	   interpolation options, e.g. inverse distance weighting,
	   nearest neighbour with the problems (a) or (b), or possibly
	   natural neighbour with rather more thought...

	*/

	public Inverse inverse( ImagePlus template, ImagePlus model ) {

		if( inverse != null )
			return inverse;

		if( originalFile == null )
			throw new RuntimeException( "Can't use CMTK_Transformation.inverse without originalFile being set." );

		File directoryOfOriginalFile = originalFile.getParentFile();
		File headerFile = new File( directoryOfOriginalFile, "inverse.nhrd" );
		File xFile = new File( directoryOfOriginalFile, "inverse_x.gz" );
		File yFile = new File( directoryOfOriginalFile, "inverse_y.gz" );
		File zFile = new File( directoryOfOriginalFile, "inverse_z.gz" );
		if( headerFile.exists() && xFile.exists() && yFile.exists() && zFile.exists() ) {
			inverse = Inverse.load( headerFile, xFile, yFile, zFile,
						template, model );
			return inverse;
		}

		int modelWidth = model.getWidth();
		int modelHeight = model.getHeight();
		int modelDepth = model.getStackSize();

		int templateWidth = template.getWidth();
		int templateHeight = template.getHeight();
		int templateDepth = template.getStackSize();

		double templatePixelWidth = 1;
		double templatePixelHeight = 1;
		double templatePixelDepth = 1;

		Calibration templateCalibration = template.getCalibration();
		if( templateCalibration != null ) {
			templatePixelWidth = templateCalibration.pixelWidth;
			templatePixelHeight = templateCalibration.pixelHeight;
			templatePixelDepth = templateCalibration.pixelDepth;
		}

		double modelPixelWidth = 1;
		double modelPixelHeight = 1;
		double modelPixelDepth = 1;

		Calibration modelCalibration = model.getCalibration();
		if( modelCalibration != null ) {
			modelPixelWidth = modelCalibration.pixelWidth;
			modelPixelHeight = modelCalibration.pixelHeight;
			modelPixelDepth = modelCalibration.pixelDepth;
		}

		template.close();
		model.close();

		int pointsEitherSide = 3;

		short [][] templateX = new short[modelDepth][modelWidth*modelHeight];
		short [][] templateY = new short[modelDepth][modelWidth*modelHeight];
		short [][] templateZ = new short[modelDepth][modelWidth*modelHeight];

		float [][] distanceSquared = new float[modelDepth][modelWidth*modelHeight];

		double [] transformed = new double[3];

		for( int z = 0; z < modelDepth; ++z ) {
			for( int p = 0; p < (modelWidth * modelHeight); ++p ) {
				distanceSquared[z][p] = Float.MAX_VALUE;
				templateX[z][p] = Short.MIN_VALUE;
				templateY[z][p] = Short.MIN_VALUE;
				templateZ[z][p] = Short.MIN_VALUE;
			}
		}

		for( int tiz = 0; tiz < templateDepth; ++tiz ) {
			System.out.println("New template z: "+tiz);
			for( int tiy = 0; tiy < templateHeight; ++tiy ) {
				// System.out.println("New template y: "+tiy);
				for( int tix = 0; tix < templateWidth; ++tix ) {
/*
					if( true ) {
						int indexOfMaximum = 0;
						int pi = (tiy * templateWidth + tix) % (modelWidth * modelHeight);
						int pz = tiz % modelDepth;
						distanceSquared[indexOfMaximum][pz][pi] = 3.4f;
						templateX[indexOfMaximum][pz][pi] = (short)(templateWidth - tix);
						templateY[indexOfMaximum][pz][pi] = (short)(templateHeight - tiy);
						templateZ[indexOfMaximum][pz][pi] = (short)(templateDepth - tiz);
						continue;
					}
*/
					double tx = tix * templatePixelWidth;
					double ty = tiy * templatePixelHeight;
					double tz = tiz * templatePixelDepth;
					transformPoint( tx, ty, tz, transformed );
					double mx = transformed[0];
					double my = transformed[1];
					double mz = transformed[2];
					int mix = (int)Math.round( mx / modelPixelWidth );
					int miy = (int)Math.round( my / modelPixelHeight );
					int miz = (int)Math.round( mz / modelPixelDepth );
					for( int nearmiz = miz - pointsEitherSide;
					     nearmiz <= miz + pointsEitherSide;
					     ++nearmiz )
						for( int nearmiy = miy - pointsEitherSide;
						     nearmiy <= miy + pointsEitherSide;
						     ++nearmiy )
							for( int nearmix = mix - pointsEitherSide;
							     nearmix <= mix + pointsEitherSide;
							     ++nearmix ) {
								if( nearmix < 0 || nearmiy < 0 || nearmiz < 0 ||
								    nearmix >= modelWidth ||
								    nearmiy >= modelHeight ||
								    nearmiz >= modelDepth )
									continue;
								double nearmx = nearmix * modelPixelWidth;
								double nearmy = nearmiy * modelPixelHeight;
								double nearmz = nearmiz * modelPixelDepth;
								double xdiff = nearmx - mx;
								double ydiff = nearmy - my;
								double zdiff = nearmz - mz;
								double doubleDistanceSquared =
									xdiff * xdiff + ydiff * ydiff + zdiff * zdiff;
								float ds = (float)doubleDistanceSquared;
								int pi = nearmiy * modelWidth + nearmix;
								if( ds < distanceSquared[nearmiz][pi] ) {
									distanceSquared[nearmiz][pi] = ds;	
									templateX[nearmiz][pi] = (short)tix;
									templateY[nearmiz][pi] = (short)tiy;
									templateZ[nearmiz][pi] = (short)tiz;
								}
							}
				}
			}
		}

		/* I'm so short of memory on my desktop machine that I
		   have to write this straight out to disk and then
		   read it in again.  It'd so tedious to calculate
		   that this probably isn't a bad thing anyway... */

		try {
			System.out.println("Writing to "+headerFile.getAbsolutePath());

			// Write the header file first:
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(headerFile),"UTF-8"));
			pw.println("NRRD0005");
			pw.println("type: short");
			pw.println("dimension: 4");
			pw.println("sizes: "+modelWidth+" "+modelHeight+" "+modelDepth+" 3");
			pw.println("encoding: gz");
			pw.println("data file: "+xFile.getName());
			pw.println("data file: "+yFile.getName());
			pw.println("data file: "+zFile.getName());
			// FIXME: how do we output the model calibration in NRRD?  Or not bother?
			pw.close();

			System.out.println("  Writing to "+xFile.getAbsolutePath());
			System.out.println("  Writing to "+yFile.getAbsolutePath());
			System.out.println("  Writing to "+zFile.getAbsolutePath());

			DataOutputStream dosX = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(xFile)));
			DataOutputStream dosY = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(yFile)));
			DataOutputStream dosZ = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(zFile)));
		
			for( int miz = 0; miz < modelDepth; ++miz )
				for( int miy = 0; miy < modelHeight; ++miy )
					for( int mix = 0; mix < modelDepth; ++mix ) {
						int pi = ((int)miy) * modelWidth + (int)mix;
						if( distanceSquared[miz][pi] < Float.MAX_VALUE ) {
							dosX.writeFloat( templateX[miz][pi] );
							dosY.writeFloat( templateY[miz][pi] );
							dosZ.writeFloat( templateZ[miz][pi] );
						} else {
							dosX.writeFloat( Float.NaN );
							dosY.writeFloat( Float.NaN );
							dosZ.writeFloat( Float.NaN );							
						}
					}

			dosX.close();
			dosY.close();
			dosZ.close();

		} catch( IOException e ) {
			IJ.error( "Writing the inverse to disk failed: "+e);
			e.printStackTrace();
		}

		templateX = null;
		templateY = null;
		templateZ = null;
		distanceSquared = null;

		// Shouldn't make a difference, but maybe it does on some old or broken VMs...
		System.gc();

		System.out.println("Loading back in now:");

		Inverse result = Inverse.load( headerFile, xFile, yFile, zFile, template, model );

		return result;
	}

	static public class Inverse {

		int modelWidth, modelHeight, modelDepth;
		int templateWidth, templateHeight, templateDepth;

		short templateX [][];
		short templateY [][];
		short templateZ [][];

		Calibration templateCalibration;
		Calibration modelCalibration;

		double modelPixelWidth = 1, modelPixelHeight = 1, modelPixelDepth = 1;
		double templatePixelWidth = 1, templatePixelHeight = 1, templatePixelDepth = 1;

		public Inverse( ImagePlus template, ImagePlus model ) {
			modelWidth = model.getWidth();
			modelHeight = model.getHeight();
			modelDepth = model.getStackSize();
			modelCalibration = model.getCalibration();
			if( modelCalibration != null ) {
				modelPixelWidth = modelCalibration.pixelWidth;
				modelPixelHeight = modelCalibration.pixelHeight;
				modelPixelDepth = modelCalibration.pixelDepth;
			}
			templateWidth = template.getWidth();
			templateHeight = template.getHeight();
			templateDepth = template.getStackSize();
			templateCalibration = template.getCalibration();
			if( templateCalibration != null ) {
				templatePixelWidth = templateCalibration.pixelWidth;
				templatePixelHeight = templateCalibration.pixelHeight;
				templatePixelDepth = templateCalibration.pixelDepth;
			}
			templateX = new short[modelDepth][modelWidth*modelHeight];
			templateY = new short[modelDepth][modelWidth*modelHeight];
			templateZ = new short[modelDepth][modelWidth*modelHeight];
		}

		public static Inverse load( File headerFile, File xFile, File yFile, File zFile, ImagePlus template, ImagePlus model ) {
			// FIXME: implement
			return null;
		}

		public void transformPoint( double modelX, double modelY, double modelZ, double [] transformed ) {
			int mix = (int)Math.round( modelX / modelPixelWidth );
			int miy = (int)Math.round( modelY / modelPixelHeight );
			int miz = (int)Math.round( modelZ / modelPixelDepth );
			if( mix < 0 || miy < 0 || miz < 0 ||
			    mix >= modelWidth || miy >= modelHeight || miz >= modelDepth ) {
				transformed[0] = Double.NaN;
				transformed[1] = Double.NaN;
				transformed[2] = Double.NaN;
			} else {
				transformed[0] = templateX[miz][ miy * modelWidth + mix ] * templatePixelWidth;
				transformed[1] = templateY[miz][ miy * modelWidth + mix ] * templatePixelHeight;
				transformed[2] = templateZ[miz][ miy * modelWidth + mix ] * templatePixelDepth;
			}
		}

		public void transformPoint( double modelX, double modelY, double modelZ, int [] transformed ) {
			int mix = (int)Math.round( modelX / modelPixelWidth );
			int miy = (int)Math.round( modelY / modelPixelHeight );
			int miz = (int)Math.round( modelZ / modelPixelDepth );
			if( mix < 0 || miy < 0 || miz < 0 ||
			    mix >= modelWidth || miy >= modelHeight || miz >= modelDepth ) {
				transformed[0] = Integer.MIN_VALUE;
				transformed[1] = Integer.MIN_VALUE;
				transformed[2] = Integer.MIN_VALUE;
			} else {
				transformed[0] = (int) templateX[miz][ miy * modelWidth + mix ];
				transformed[1] = (int) templateY[miz][ miy * modelWidth + mix ];
				transformed[2] = (int) templateZ[miz][ miy * modelWidth + mix ];
			}
		}

		public void transformPoint( int modelX, int modelY, int modelZ, int [] transformed ) {
			int mix = modelX;
			int miy = modelY;
			int miz = modelZ;
			if( mix < 0 || miy < 0 || miz < 0 ||
			    mix >= modelWidth || miy >= modelHeight || miz >= modelDepth ) {
				transformed[0] = Integer.MIN_VALUE;
				transformed[1] = Integer.MIN_VALUE;
				transformed[2] = Integer.MIN_VALUE;
			} else {
				transformed[0] = (int) templateX[miz][ miy * modelWidth + mix ];
				transformed[1] = (int) templateY[miz][ miy * modelWidth + mix ];
				transformed[2] = (int) templateZ[miz][ miy * modelWidth + mix ];
			}
		}

		public void transformPoint( int modelX, int modelY, int modelZ, double [] transformed ) {
			int mix = modelX;
			int miy = modelY;
			int miz = modelZ;
			if( mix < 0 || miy < 0 || miz < 0 ||
			    mix >= modelWidth || miy >= modelHeight || miz >= modelDepth ) {
				transformed[0] = Double.NaN;
				transformed[1] = Double.NaN;
				transformed[2] = Double.NaN;
			} else {
				transformed[0] = templateX[miz][ miy * modelWidth + mix ] * templatePixelWidth;
				transformed[1] = templateY[miz][ miy * modelWidth + mix ] * templatePixelHeight;
				transformed[2] = templateZ[miz][ miy * modelWidth + mix ] * templatePixelDepth;
			}
		}
	}
}
