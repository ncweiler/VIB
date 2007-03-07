package vib;

import Quick3dApplet.*;

import amira.AmiraParameters;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.gui.ImageWindow;
import ij.gui.StackWindow;
import ij.io.OpenDialog;
import java.io.DataInputStream;
import java.io.FileInputStream;

public class ShowAmiraNeuronTree_ implements PlugIn {
	public final int WIDTH = 400, HEIGHT = 400, BGCOLOR = 0x000000;

	public void run(String arg) {
		OpenDialog od = new OpenDialog("AmiraFile", null);
		String dir=od.getDirectory();
		arg=od.getFileName();

		Image3dCanvas ic = new Image3dCanvas(arg, WIDTH, HEIGHT);
		ic.r.setViewOffset(new Vec(300, 300, -2500));
		ic.r.setLightDir(new Vec(0, 0, +1));
		ic.r.setBackgroundCol(BGCOLOR);

		try {
			getNeuronTree(dir + arg, ic);
		} catch(Exception e) {
			e.printStackTrace();
			return;
		}

		ic.fitToWindow();
		ic.repaint();
	}

	public static void getNeuronTree(String fileName, Image3dCanvas ic)
			throws java.io.IOException {
		FileInputStream f = new FileInputStream(fileName);
		DataInputStream input = new DataInputStream(f);

		if (!input.readLine().startsWith("# AmiraMesh 3D BINARY 2.0"))
			throw new RuntimeException("No Amira NeuronTree");

		String line;
		while((line = input.readLine()).equals("")
				|| line.startsWith("#"))
			; // read next

		if (!line.startsWith("define Lines "))
			throw new RuntimeException("No Amira NeuronTree");
		int lineCount = Integer.parseInt(line.substring(13));

		if (!(line = input.readLine()).startsWith("nVertices "))
			throw new RuntimeException("No Amira NeuronTree");
		int vertexCount = Integer.parseInt(line.substring(10));

		String header = "";
		while ((line = input.readLine()) != null &&
				!line.startsWith("Lines "))
			header += line + "\n";
		AmiraParameters params = new AmiraParameters(header);
		if (!input.readLine().startsWith("Vertices "))
			throw new RuntimeException("No Amira NeuronTree");
		while (!(line = input.readLine()).startsWith("@1"))
			System.err.println("got line: " + line); // do nothing

		int[] segments = new int[lineCount];
		for (int i = 0; i < lineCount; i++) {
			segments[i] = input.readInt();
		}

		while (!(line = input.readLine()).startsWith("@2"))
			System.err.println("got line2: " + line); // do nothing
		Vertex[] vertices = new Vertex[vertexCount];
		for (int i = 0; i < vertexCount; i++) {
			float x = input.readFloat();
			float y = input.readFloat();
			float z = input.readFloat();
			vertices[i] = new Vertex(new Vec(x, y, z));
		}

		RenderObject ro = new RenderObject();
		int color = 0xffffff;
		for (int i = 1; i < lineCount; i++) {
			if (segments[i] == -1 || segments[i - 1] == -1)
				continue;
			Vertex v1 = vertices[segments[i - 1]];
			Vertex v2 = vertices[segments[i]];
			ro.addTri(new ColorLine(v1, v2, color));
		}
		ro.optimise();
		ic.objects.addElement(ro);
	}
}


