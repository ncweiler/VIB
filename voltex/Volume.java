package voltex;

import java.awt.*;
import java.awt.image.*;
import java.awt.color.ColorSpace;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.*;
import com.sun.j3d.utils.behaviors.mouse.*;
import java.net.*;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.IJ;

public class Volume implements VolRendConstants {

	public static final int INT_DATA = 0;
	public static final int BYTE_DATA = 1;

	private ImagePlus imp;
	private Loader loader;
	private int dataType;
	private boolean[] channels = new boolean[] {true, true, true};

	public int xDim = 0, yDim = 0, zDim = 0;
	public float xSpace = 0, ySpace = 0, zSpace = 0;
	public int xTexSize, yTexSize, zTexSize;
	public float xTexGenScale, yTexGenScale, zTexGenScale;

	Point3d minCoord = new Point3d();
	Point3d maxCoord = new Point3d();
	Point3d volRefPt = new Point3d();

	public Volume(ImagePlus imp) {
		this(imp, new boolean[] {true, true, true});
	}

	public Volume(ImagePlus imp, boolean[] ch) {
		this.channels = ch;
		this.imp = imp;
		init();
		int type = imp.getType();
		int usedCh = 0;
		for(int i = 0; i < 3; i++)
			if(ch[i]) usedCh++;
		switch(type) {
			case ImagePlus.GRAY8:
				loader = new ByteLoader();
				dataType = BYTE_DATA;
				break;
			case ImagePlus.COLOR_RGB:
				if(usedCh == 1) {
					loader = new ByteFromIntLoader(ch);
					dataType = BYTE_DATA;
				} else if(usedCh == 2) {
					loader = new IntFromIntLoader(ch);
					dataType = INT_DATA;
				} else {
					loader = new IntLoader();
					dataType = INT_DATA;
				}
				break;
			default: IJ.error("image format not supported");
		}
	}

	public int getDataType() {
		return dataType;
	}

	/*
	 * Returns true if data has to be reloaded.
	 */
	public boolean setChannels(boolean[] ch) {
		System.out.println("volume.setChannels");
		if(ch[0] == channels[0] && 
			ch[1] == channels[1] && 
			ch[2] == channels[2])
			return false;

		int usedCh = 0;
		for(int i = 0; i < 3; i++)
			if(ch[i]) usedCh++;
		switch(imp.getType()) {
			case ImagePlus.GRAY8:
			case ImagePlus.COLOR_256:
				return false;
			case ImagePlus.COLOR_RGB:
				if(usedCh == 1) {
					loader = new ByteFromIntLoader(ch);
					dataType = BYTE_DATA;
				} else if(usedCh == 2) {
					loader = new IntFromIntLoader(ch);
					dataType = INT_DATA;
				} else {
					loader = new IntLoader();
					dataType = INT_DATA;
				}
				return true;
			default: 
				IJ.error("image format not supported");
				return false;
		}
	}

	private void init() {
		Calibration c = imp.getCalibration();
		ImageStack stack = imp.getStack();
		xDim = stack.getWidth();
		yDim = stack.getHeight();
		zDim = stack.getSize();

		// tex size is next power of two greater than max - min
		// regarding pixels
		xTexSize = powerOfTwo(xDim);
		yTexSize = powerOfTwo(yDim);
		zTexSize = powerOfTwo(zDim);

		xSpace = (float)imp.getCalibration().pixelWidth;
		ySpace = (float)imp.getCalibration().pixelHeight;
		zSpace = (float)imp.getCalibration().pixelDepth;
		
		// real coords
		maxCoord.x = xDim * xSpace;
		maxCoord.y = yDim * ySpace;
		maxCoord.z = zDim * zSpace;

		// xTexSize is the pixel dim of the file in x-dir, e.g. 256
		// xSpace is the normalised length of a pixel
		xTexGenScale = (float)(1.0 / (xSpace * xTexSize));
		yTexGenScale = (float)(1.0 / (ySpace * yTexSize));
		zTexGenScale = (float)(1.0 / (zSpace * zTexSize));

		// the min and max coords are for the usable area of the texture,
		volRefPt.x = (maxCoord.x + minCoord.x) / 2;
		volRefPt.y = (maxCoord.y + minCoord.y) / 2;
		volRefPt.z = (maxCoord.z + minCoord.z) / 2;
	}

	public boolean hasData() {
		return (imp != null);
	}

	private int powerOfTwo(int value) {
		int retval = 16;
		while (retval < value) {
			retval *= 2;
		}
		return retval;
	}

	void loadZ(int z, Object dst) {
		loader.loadZ(z, dst);
	}

	void loadY(int y, Object dst) {
		loader.loadY(y, dst);
	}

	void loadX(int x, Object dst) {
		loader.loadX(x, dst);
	}

	private abstract class Loader {
		abstract void loadZ(int z, Object dst);
		abstract void loadY(int y, Object dst);
		abstract void loadX(int x, Object dst);
	}

	private final class ByteLoader extends Loader {
		byte[][] fData;

		ByteLoader() {
			ImageStack stack = imp.getStack();
			int d = imp.getStackSize();
			fData = new byte[d][];
			for (int z = 0; z < d; z++)
				fData[z] = (byte[])stack.getPixels(z+1);
		}

		void loadZ(int zValue, Object arr) {
			byte[] dst = (byte[])arr;
			byte[] src = fData[zValue];
			for (int y=0; y < yDim; y++){
				int offsSrc = y * xDim;
				int offsDst = y * xTexSize;
				System.arraycopy(
					src, offsSrc, dst, offsDst, xDim);
			}
		}

		/* 
		 * this routine loads values for constant yValue, the 
		 * texture map is stored in x,z format (x changes fastest)
		 */
		void loadY(int yValue, Object arr)  {
			byte[] dst = (byte[])arr;
			for (int z=0; z < zDim; z++){
				byte[] src = fData[z];
				int offsSrc = yValue * xDim;
				int offsDst = z * xTexSize;
				System.arraycopy(
					src, offsSrc, dst, offsDst, xDim);
			}
		}

		/* 
		 * this routine loads values for constant xValue, into 
		 * byteData in y,z order (y changes fastest)
		 */
		void loadX(int xValue, Object arr)  {
			byte[] dst = (byte[])arr;
			for (int z=0; z < zDim; z++){
				byte[] src = fData[z];
				int offsDst = z * yTexSize;
				for (int y=0; y < yDim; y++){
					int offsSrc = y * xDim + xValue;
					dst[offsDst + y] = fData[z][offsSrc];
				}
			}
		}
	}

	private final class IntLoader extends Loader {
		int[][] fData;

		IntLoader() {
			ImageStack stack = imp.getStack();
			int d = imp.getStackSize();
			fData = new int[d][];
			for (int z = 0; z < d; z++)
				fData[z] = (int[])stack.getPixels(z+1);
			adjustAlphaChannel();
		}

		void adjustAlphaChannel() {
			for(int z = 0; z < fData.length; z++) {
				for(int i = 0; i < fData[z].length; i++) {
					int v = fData[z][i];
					int r = (v&0xff0000)>>16;
					int g = (v&0xff00)>>8;
					int b = (v&0xff);
					int a = ((r + g + b) / 3) << 24;
					fData[z][i] = (v & 0xffffff) + a;
				}
			}
		}

		void loadZ(int zValue, Object arr) {
			int[] dst = (int[])arr;
			int[] src = fData[zValue];
			for (int y=0; y < yDim; y++){
				int offsSrc = y * xDim;
				int offsDst = y * xTexSize;
				System.arraycopy(src,offsSrc,dst,offsDst,xDim);
			}
		}

		/* 
		 * this routine loads values for constant yValue, the 
		 * texture map is stored in x,z format (x changes fastest)
		 */
		void loadY(int yValue, Object arr)  {
			int[] dst = (int[])arr;
			for (int z=0; z < zDim; z++){
				int[] src = fData[z];
				int offsSrc = yValue * xDim;
				int offsDst = z * xTexSize;
				System.arraycopy(src,offsSrc,dst,offsDst,xDim);
			}
		}

		/* 
		 * this routine loads values for constant xValue, into 
		 * byteData in y,z order (y changes fastest)
		 */
		void loadX(int xValue, Object arr)  {
			int[] dst = (int[])arr;
			for (int z=0; z < zDim; z++){
				int[] src = fData[z];
				int offsDst = z * yTexSize;
				for (int y=0; y < yDim; y++){
					int offsSrc = y * xDim + xValue;
					dst[offsDst + y] = fData[z][offsSrc];
				}
			}
		}
	}

	/*
	 * Loads the specified channels from int data
	 * This class should only be used if not all channels are
	 * used. Otherwise, it's faster to use the IntLoader.
	 */
	private final class IntFromIntLoader extends Loader {
		int[][] fData;
		int mask = 0xffffff;
		boolean[] ch = new boolean[] {true, true, true};
		int usedCh = 3;

		IntFromIntLoader(boolean[] channels) {
			ImageStack stack = imp.getStack();
			int d = imp.getStackSize();
			fData = new int[d][];
			for (int z = 0; z < d; z++)
				fData[z] = (int[])stack.getPixels(z+1);

			ch = channels;
			usedCh = 0;
			mask = 0xff000000;
			if(ch[0]) { usedCh++; mask |= 0xff0000; }
			if(ch[1]) { usedCh++; mask |= 0xff00; }
			if(ch[2]) { usedCh++; mask |= 0xff; }
			adjustAlphaChannel();
		}

		void adjustAlphaChannel() {
			for(int z = 0; z < fData.length; z++) {
				for(int i = 0; i < fData[z].length; i++) {
					int v = fData[z][i];
					int n = 0;
					if(ch[0]) n += (v & 0xff0000) >> 16;
					if(ch[1]) n += (v & 0xff00) >> 8;
					if(ch[2]) n += (v & 0xff);
					int a = (n / usedCh) << 24;
					fData[z][i] = (v & 0xffffff) + a;
				}
			}
		}

		void loadZ(int zValue, Object arr) {
			int[] dst = (int[])arr;
			int[] src = fData[zValue];
			for (int y=0; y < yDim; y++){
				int offsSrc = y * xDim;
				int offsDst = y * xTexSize;
				System.arraycopy(src,offsSrc,dst,offsDst,xDim);
			}
			for(int i = 0; i < dst.length; i++)
				dst[i] &= mask;
		}

		/* 
		 * this routine loads values for constant yValue, the 
		 * texture map is stored in x,z format (x changes fastest)
		 */
		void loadY(int yValue, Object arr)  {
			int[] dst = (int[])arr;
			for (int z=0; z < zDim; z++){
				int[] src = fData[z];
				int offsSrc = yValue * xDim;
				int offsDst = z * xTexSize;
				System.arraycopy(src,offsSrc,dst,offsDst,xDim);
			}
			for(int i = 0; i < dst.length; i++)
				dst[i] &= mask;
		}

		/* 
		 * this routine loads values for constant xValue, into 
		 * byteData in y,z order (y changes fastest)
		 */
		void loadX(int xValue, Object arr)  {
			int[] dst = (int[])arr;
			for (int z=0; z < zDim; z++){
				int[] src = fData[z];
				int offsDst = z * yTexSize;
				for (int y=0; y < yDim; y++){
					int offsSrc = y * xDim + xValue;
					dst[offsDst + y] = fData[z][offsSrc];
				}
			}
			for(int i = 0; i < dst.length; i++)
				dst[i] &= mask;
		}
	}

	/*
	 * Loads from the specified channels an average byte from int
	 * data.
	 */
	private final class ByteFromIntLoader extends Loader {
		int[][] fdata;
		boolean[] channels = new boolean[] {true, true, true};
		int usedCh = 3;

		ByteFromIntLoader(boolean[] channels) {
			this.channels = channels;
			ImageStack stack = imp.getStack();
			int d = imp.getStackSize();
			fdata = new int[d][];
			for (int z = 0; z < d; z++)
				fdata[z] = (int[])stack.getPixels(z+1);
			usedCh = 0;
			for(int i = 0; i < 3; i++)
				if(channels[i]) usedCh++;
		}

		void loadZ(int zValue, Object arr) {
			byte[] dst = (byte[])arr;
			int[] src = fdata[zValue];
			for (int y=0; y < yDim; y++){
				int offssrc = y * xDim;
				int offsdst = y * xTexSize;
				for(int x = 0; x < xDim; x++) {
					int v = src[offssrc + x];
					int n = 0;
					if(channels[0]) n += (v&0xff0000)>>16;
					if(channels[1]) n += (v&0xff00)>>8;
					if(channels[2]) n += (v&0xff);
					n /= usedCh;
					dst[offsdst + x] = (byte)n;
				}
			}
		}

		/* 
		 * this routine loads values for constant yValue, the 
		 * texture map is stored in x,z format (x changes fastest)
		 */
		void loadY(int yValue, Object arr)  {
			byte[] dst = (byte[])arr;
			for (int z=0; z < zDim; z++){
				int[] src = fdata[z];
				int offssrc = yValue * xDim;
				int offsdst = z * xTexSize;
				for(int x = 0; x < xDim; x++) {
					int v = src[offssrc + x];
					int n = 0;
					if(channels[0]) n += (v&0xff0000)>>16;
					if(channels[1]) n += (v&0xff00)>>8;
					if(channels[2]) n += (v&0xff);
					n /= usedCh;
					dst[offsdst + x] = (byte)n;
				}
			}
		}

		/* 
		 * this routine loads values for constant xValue, into 
		 * bytedata in y,z order (y changes fastest)
		 */
		void loadX(int xValue, Object arr)  {
			byte[] dst = (byte[])arr;
			for (int z=0; z < zDim; z++){
				int[] src = fdata[z];
				int offsdst = z * yTexSize;
				for (int y=0; y < yDim; y++){
					int offssrc = y * xDim + xValue;
					int v = src[offssrc];
					int n = 0;
					if(channels[0]) n += (v&0xff0000)>>16;
					if(channels[1]) n += (v&0xff00)>>8;
					if(channels[2]) n += (v&0xff);
					n /= usedCh;
					dst[offsdst + y] = (byte)n;
				}
			}
		}
	}
}
