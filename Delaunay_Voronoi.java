import delaunay.DelaunayTriangulation;
import delaunay.Pnt;
import delaunay.Simplex;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/** Draw Delaunay triangulation or Voronoi Diagram as an overlay. */
public class Delaunay_Voronoi implements PlugIn {

	public final int DELAUNAY = 1;
	public final int VORONOI = 2;
	int mode = DELAUNAY;

	boolean showMeanDistance = false;

	final boolean drawZoom = IJ.getVersion().compareTo("1.37n") >= 0;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp == null)
			return;

		GenericDialog gd = new GenericDialog("Delaunay/Voronoi parameters");
		gd.addChoice("mode", new String[] { "Delaunay", "Voronoi"},
				"Delaunay");
		gd.addCheckbox("interactive", !Interpreter.isBatchMode());
		gd.addCheckbox("showMeanDistance", false);
		ResultsTable results = Analyzer.getResultsTable();
		gd.addCheckbox("inferSelectionFromParticles",
				imp.getRoi() == null && results != null
				&& results.getColumnIndex("XM")
				!= ResultsTable.COLUMN_NOT_FOUND);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
	
		mode = gd.getNextChoiceIndex() + 1;
		boolean interactive = gd.getNextBoolean();
		showMeanDistance = gd.getNextBoolean();
		boolean fromParticles = gd.getNextBoolean();

		if (fromParticles) {
			Calibration calib = imp.getCalibration();
			int xCol = results.getColumnIndex("XM");
			int yCol = results.getColumnIndex("YM");
			if (xCol < 0 || yCol < 0) {
				IJ.error("You did not select Center of Mass in"
					+ " Analyze>Set Measurements...\n"
					+ "Select it and try again.");
				return;
			}
			float[] x = results.getColumn(xCol);
			float[] y = results.getColumn(yCol);
			int[] xInt = new int[x.length];
			int[] yInt = new int[x.length];
			for (int i = 0; i < x.length; i++) {
				xInt[i] = (int)Math.round((x[i] /
							calib.pixelWidth) -
							calib.xOrigin);
				yInt[i] = (int)Math.round((y[i] /
							calib.pixelHeight) -
							calib.yOrigin);
			}
			imp.setRoi(new PointRoi(xInt, yInt, x.length));
		}

		CustomCanvas cc = new CustomCanvas(imp);

		if (!interactive) {
			cc.drawOverlay(null);
			imp.updateAndDraw();
			return;
		}

		if (imp.getStackSize()>1)
			new StackWindow(imp, cc).addKeyListener(cc);
		else
			new ImageWindow(imp, cc).addKeyListener(cc);
		Roi roi = imp.getRoi();
		if (roi != null)
			// implicitely set the new image canvas
			roi.setImage(imp);
	}

	class CustomCanvas extends ImageCanvas implements KeyListener {
		DelaunayTriangulation delaunay;
		final double inf;

		CustomCanvas(ImagePlus imp) {
			super(imp);
			inf = imp.getWidth() + imp.getHeight();
			initDelaunay();
			addKeyListener(this);
		}

		public void paint(Graphics g) {
			super.paint(g);
			drawOverlay(g);
		}

		void drawOverlay(Graphics g) {
			if (delaunay == null)
				return;

			for (Iterator iter = delaunay.iterator();
					iter.hasNext(); ) {
				Simplex triangle = (Simplex)iter.next();

				if (mode == DELAUNAY) {
					Iterator iter2 = triangle.iterator();
					Pnt a = (Pnt)iter2.next();
					Pnt b = (Pnt)iter2.next();
					Pnt c = (Pnt)iter2.next();
					draw(g, a, b);
					draw(g, a, c);
					draw(g, b, c);
				} else {
					Iterator iter2 = delaunay
						.neighbors(triangle).iterator();
					while (iter2.hasNext())
						draw(g, triangle,
							(Simplex)iter2.next());
				}
			}
		}

		void draw(Graphics g, Pnt a, Pnt b) {
			if (mode != VORONOI && (Math.abs(a.coord(0)) >= inf ||
						Math.abs(b.coord(0)) >= inf))
				return;

			if (g == null) {
				ImageProcessor ip = imp.getProcessor();
				ip.drawLine((int)a.coord(0),
						(int)a.coord(1),
						(int)b.coord(0),
						(int)b.coord(1));
				return;
			}

			double m = magnification;
			double x0 = (a.coord(0) - srcRect.x) * m;
			double y0 = (a.coord(1) - srcRect.y) * m;
			double x1 = (b.coord(0) - srcRect.x) * m;
			double y1 = (b.coord(1) - srcRect.y) * m;
			g.setColor(imp.getRoi().getColor());
			g.drawLine((int)x0, (int)y0, (int)x1, (int)y1);
			if (drawZoom && srcRect.width != imageWidth
					&& g != null) {
				int xOffset = 10, yOffset = 10;
				int w = 64, h = 64;
				if (imageHeight > imageWidth) {
					m = 64.0 / imageHeight;
					w = (int)(imageWidth * m);
				} else {
					m = 64.0 / imageWidth;
					h = (int)(imageHeight * m);
				}
				x0 = a.coord(0) * m + xOffset;
				y0 = a.coord(1) * m + yOffset;
				x1 = b.coord(0) * m + xOffset;
				y1 = b.coord(1) * m + yOffset;
				Shape clip = g.getClip();
				g.setColor(new Color(128, 128, 255));
				g.clipRect(xOffset, yOffset, w, h);
				g.drawLine((int)x0, (int)y0,
						(int)x1, (int)y1);
				g.setClip(clip);
			}
		}

		void draw(Graphics g, Simplex a, Simplex b) {
			draw(g, Pnt.circumcenter((Pnt[])a.toArray(new Pnt[0])),
				Pnt.circumcenter((Pnt[])b.toArray(new Pnt[0])));
		}

		public void mouseReleased(MouseEvent e) {
			super.mouseReleased(e);
			initDelaunay();
			repaint();
		}

		public void keyTyped(KeyEvent e) {}
		public void keyPressed(KeyEvent e) {
			if (e.getKeyCode() == KeyEvent.VK_SPACE) {
				mode = mode == DELAUNAY ? VORONOI : DELAUNAY;
				repaint();
			}
		}
		public void keyReleased(KeyEvent e) {}

		public void initDelaunay() {
			delaunay = null;

			Roi roi = imp.getRoi();
			if (roi == null || !(roi instanceof PointRoi))
				return;

			PointRoi r = (PointRoi)roi;
			Rectangle rect = r.getBounds();
			int n = r.getNCoordinates();
			int[] x = r.getXCoordinates();
			int[] y = r.getYCoordinates();

			Simplex initial = new Simplex(new Pnt[] {
					new Pnt(-inf, -inf),
					new Pnt(-inf, 5 * inf),
					new Pnt(5 * inf, -inf)});
			delaunay = new DelaunayTriangulation(initial);
			for (int i = 0; i < n; i++)
				delaunay.delaunayPlace(new Pnt(x[i] + rect.x,
							y[i] + rect.y));

			if (showMeanDistance && mode == DELAUNAY)
				showMeanAndVariance();
		}

		double pixelWidth, pixelHeight;
		double mean, variance;
		int total;
		
		private void addToMean(Pnt a, Pnt b) {
			if (Math.abs(a.coord(0)) >= inf ||
					Math.abs(b.coord(0)) >= inf)
				return;
			double x = (b.coord(0) - a.coord(0)) * pixelWidth;
			double y = (b.coord(1) - a.coord(1)) * pixelHeight;
			double d2 = x * x + y * y;
			mean += Math.sqrt(d2);
			variance += d2;
			total++;
		}

		public void showMeanAndVariance() {
			Calibration calib = imp.getCalibration();
			pixelWidth = calib.pixelWidth;
			pixelHeight = calib.pixelHeight;

			mean = variance = total = 0;

			for (Iterator iter = delaunay.iterator();
					iter.hasNext(); ) {
				Simplex triangle = (Simplex)iter.next();
				Iterator iter2 = triangle.iterator();
				Pnt a = (Pnt)iter2.next();
				Pnt b = (Pnt)iter2.next();
				Pnt c = (Pnt)iter2.next();
				addToMean(a, b);
				addToMean(b, c);
				addToMean(c, a);
			}

			if (total > 0) {
				mean /= total;
				variance /= total;
				variance -= mean * mean;
				IJ.write("mean distance: " + mean +
						", variance: " + variance);
			}
		}
	}
}
