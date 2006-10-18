package leica;

import ij.macro.Interpreter;
import ij.*;
import ij.io.*;
import ij.measure.Calibration;
import ij.plugin.*;
import java.io.*;
import java.util.*;

/**
 * Opens multi-image 8-bits tiff files created by Leica confocal microscope
 * systems using each channels own LUT.  Modified by Nico Stuurman June 2000
 * Modified to set the real dimensions by J. Schindelin 2006
 */
public class Leica_SP_Reader extends ImagePlus implements PlugIn {
	
	private ImagePlus[] images;
	private int nr_channels = 1; 
	
	public void run(String arg) {
		if (IJ.versionLessThan("1.18h"))
			return;
		boolean showIt = (arg == null || arg.trim().equals("")) &&  
						 !Interpreter.isBatchMode();
		String dir = "";
		String file = "";
		if(arg==null || arg.equals("")) {
			OpenDialog od = new OpenDialog("Leica Tiff", null);
			dir = od.getDirectory();
			file = od.getFileName();
		} else {
			File f = new File(arg.trim());
			dir = f.getParent() + File.separator;
			file = f.getName();
		}
		if(arg==null)
			return;
		
		try {
			FileInfo[] fi =  getFileInfo(dir, file);
			images = new ImagePlus[nr_channels];
			for(int channel = 0; channel < nr_channels; channel++) {
				ImageStack stack = openStack(fi, channel);
				if (stack != null){
					int l = channel + 1;
					fi[0].fileName = arg;
					fi[0].directory = dir;				
					Calibration cal = new Calibration();
					cal.pixelWidth = fi[0].pixelWidth;
					cal.pixelHeight = fi[0].pixelHeight;
					cal.pixelDepth = fi[0].pixelDepth;
					if(channel == 0) {
						this.setStack(file + "(channel1)", stack);
						this.setCalibration(cal);
						this.setFileInfo(fi[0]);
					}
					images[channel] = new ImagePlus(
							file + " (channel" + l + ")", stack);
					images[channel].setCalibration(cal);
					images[channel].setFileInfo(fi[0]);
					if(showIt)
						images[channel].show();
				}
			}
		} catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null || msg.equals(""))
				msg = ""+e;
			IJ.showMessage("Leica SP Reader", msg);
		}
	}

	public int getNumberOfChannels() {
		return nr_channels;
	}

	public ImagePlus getImage(int i) {
		return images[i];
	}

	FileInfo[] getFileInfo(String directory, String name) throws IOException {
		LeicaTiffDecoder td = new LeicaTiffDecoder(directory, name);
		if (IJ.debugMode) td.enableDebugging();
		FileInfo[] info = td.getTiffInfo();
		nr_channels = td.nr_channels;
		if (info==null)
			throw new IOException("This file does not appear to be in " + 
					"TIFF format.");
		if (IJ.debugMode) // dump tiff tags
			IJ.write(info[0].info);
		return info;
	}
	
	ImageStack openStack(FileInfo[] fi, int channel) throws IOException {
		if (fi[0].fileType!=FileInfo.COLOR8)
			throw new IOException("This does not appear to be  a " + 
					"stack of 8-bit color images.");
		if(channel < 0 || channel >= nr_channels)
			throw new IOException("Image does not contain channel " + channel);
		int maxStacks = nr_channels;
		int width = fi[0].width;
		int height = fi[0].height;
		String name = fi[0].fileName;
		int length_per_channel = fi.length/nr_channels;

    	ImageStack stack = new ImageStack(width, height);
		for (int i=0; i<length_per_channel; i++) {
			int k = i + channel * length_per_channel;
			if (fi[k].width!=width || fi[k].height!=height)
				break;
			FileOpener fo = new FileOpener(fi[k]);
			ImagePlus imp = fo.open(false);
			if (imp!=null)
				stack.addSlice("", imp.getProcessor());
			IJ.showProgress(i + 1, length_per_channel);
		}
		return stack;
	}


	/*
	 * This class inherits ImageJ's TiffDecoder and overrides the 
	 * decodeImageDescription method.
	 * The Leica SP files start their image description with "[GLOBAL]".  The 
	 * number of channels in a Leica SP TIFF file is given within the image 
	 * description as "NumOfVisualisations"=x.
	 */ 
	static class LeicaTiffDecoder extends TiffDecoder {

		public int nr_channels = 1;

		public  LeicaTiffDecoder(String directory, String name) {
			super(directory, name);
		}

		public void saveImageDescription(byte[] description, FileInfo fi) {	
			decodeImageDescription(description,fi);
		}
		public void decodeImageDescription(byte[] description, FileInfo fi) {	
			if (new String (description,0,8).equals("[GLOBAL]")) {
				if (debugMode) IJ.write ("Leica file detected..." + "\n");
				String file_specs = new String (description);
				fi.info = file_specs;
				if (debugMode) IJ.write(file_specs);
				StringTokenizer st = new StringTokenizer(file_specs, "\n= ");
				while (st.hasMoreTokens()) {
					String s = st.nextToken();
					if (s.equals ("NumOfVisualisations")) {
						nr_channels = getInt(st);
						if (debugMode)
							IJ.write(nr_channels + " channels detected\n");
					} else if (s.equals ("VoxelSizeX"))
						fi.pixelWidth = getDouble(st);
					else if (s.equals ("VoxelSizeY"))
						fi.pixelHeight = getDouble(st);
					else if (s.equals ("VoxelSizeZ"))
						fi.pixelDepth = getDouble(st);
				}         
			}
		}

		public int getInt(StringTokenizer st) {
			String temp = st.nextToken().trim();
			try {
				return Integer.parseInt(temp);
			} catch (NumberFormatException e) {
				throw new RuntimeException("invalid number: "
						+ temp);
			}
		}

		public double getDouble(StringTokenizer st) {
			String temp = st.nextToken().trim();
			try {
				return Double.parseDouble(temp);
			} catch (NumberFormatException e) {
				throw new RuntimeException("invalid number: "
						+ temp);
			}
		}
	}
}
