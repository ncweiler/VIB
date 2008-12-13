import com.sun.j3d.utils.pickfast.PickCanvas;
import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Toolbar;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij3d.Image3DUniverse;
import java.awt.Polygon;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import javax.media.j3d.Appearance;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.ImageComponent;
import javax.media.j3d.ImageComponent2D;
import javax.media.j3d.Material;
import javax.media.j3d.PickInfo;
import javax.media.j3d.PolygonAttributes;
import javax.media.j3d.QuadArray;
import javax.media.j3d.RenderingAttributes;
import javax.media.j3d.Shape3D;
import javax.media.j3d.TexCoordGeneration;
import javax.media.j3d.Texture;
import javax.media.j3d.Texture2D;
import javax.media.j3d.TextureAttributes;
import javax.media.j3d.TransparencyAttributes;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Point3f;
import javax.vecmath.Vector4f;

public class Texture_By_Ref implements PlugIn, ImageListener, MouseMotionListener,
					MouseListener {

	private static final int B_IMG_TYPE = BufferedImage.TYPE_BYTE_GRAY;
	private static final int TEX_MODE = Texture.INTENSITY;
	private static final int COMP_TYPE = ImageComponent.FORMAT_CHANNEL8;
	private static final boolean BY_REF = true;
	private static final boolean Y_UP = true;

	private Image3DUniverse univ;

	private BufferedImage bImage;
	private ByteProcessor bProcessor;
	private ImageComponent2D bComp;
	private ImageComponent2D.Updater updater;
	private ImagePlus imp;
	private int w = 256;
	private int h = 256;

	public void run(String s) {
		createImage();
		univ = new Image3DUniverse();
		BranchGroup bg = new BranchGroup();
		bg.addChild(createShape());
		bg.compile();
		univ.getScene().addChild(bg);
		univ.ensureScale(w);
		univ.show();

		univ.getCanvas().addMouseListener(this);
		univ.getCanvas().addMouseMotionListener(this);

		updater = new ImageUpdater();
		ImagePlus.addImageListener(this);
		imp.show();
	}

	private static final int r = 5;
	private boolean doDraw = false;

	public void mouseDragged(MouseEvent e) {
		if(!doDraw)
			return;
		OvalRoi roi = new OvalRoi(e.getX() - r, e.getY() - r, 2 * r, 2 * r);
		Polygon p = roi.getPolygon();
		int n = p.npoints;
		Polygon q = new Polygon(new int[n], new int[n], n);
		
		for(int i = 0; i < n; i++) {
			Point3d picked = getPickPoint(p.xpoints[i], p.ypoints[i]);
			if(picked == null)
				continue;
			q.xpoints[i] = (int)Math.round(picked.x);
			q.ypoints[i] = (int)Math.round(picked.y);
		}
		bProcessor.fillPolygon(q);
		imp.updateAndDraw();
	}

	private final Point3d getPickPoint(int x, int y) {
		PickCanvas pickCanvas = new PickCanvas(univ.getCanvas(), univ.getScene());
		pickCanvas.setMode(PickInfo.PICK_GEOMETRY);
		pickCanvas.setFlags(PickInfo.CLOSEST_INTERSECTION_POINT);
		pickCanvas.setTolerance(3.0f);
		pickCanvas.setShapeLocation(x, y);
		try {
			PickInfo[] result = pickCanvas.pickAllSorted();
			if(result == null || result.length == 0)
				return null;

			for(int i = 0; i < result.length; i++)
				return result[i].getClosestIntersectionPoint();
	
			return null;
		} catch(Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public void mousePressed(MouseEvent e) {
		int id = Toolbar.getToolId();
		doDraw = id == Toolbar.SPARE1 || id == Toolbar.SPARE2 ||
			id == Toolbar.SPARE3 || id == Toolbar.SPARE4 ||
			id == Toolbar.SPARE5 || id == Toolbar.SPARE6 ||
			id == Toolbar.SPARE7 || id == Toolbar.SPARE8 ||
			id == Toolbar.SPARE9;
	}

	public void mouseReleased(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) {}

	public void mouseExited(MouseEvent e) {}

	public void mouseClicked(MouseEvent e) {}

	public void mouseMoved(MouseEvent e) {}

	public void imageOpened(ImagePlus image) {}

	public void imageClosed(ImagePlus image) {}

	public void imageUpdated(ImagePlus image) {
		if(image == imp)
			bComp.updateData(updater, 0, 0, w, h);
	}

	public Appearance createAppearance() {
		Appearance appearance = new Appearance();

		TextureAttributes texAttr = new TextureAttributes();
		texAttr.setTextureMode(TextureAttributes.COMBINE);
		texAttr.setCombineRgbMode(TextureAttributes.COMBINE_MODULATE);
		texAttr.setPerspectiveCorrectionMode(TextureAttributes.NICEST);
		appearance.setTextureAttributes(texAttr);

		TransparencyAttributes transAttr = new TransparencyAttributes();
		transAttr.setTransparency(0.1f);
		transAttr.setCapability(TransparencyAttributes.ALLOW_VALUE_WRITE);
		transAttr.setTransparencyMode(TransparencyAttributes.BLENDED);
		appearance.setTransparencyAttributes(transAttr);

		PolygonAttributes polyAttr = new PolygonAttributes();
		polyAttr.setCullFace(PolygonAttributes.CULL_NONE);
		appearance.setPolygonAttributes(polyAttr);

		Material material = new Material();
		material.setLightingEnable(false);
		appearance.setMaterial(material);

		ColoringAttributes colAttr = new ColoringAttributes();
		colAttr.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);
		colAttr.setShadeModel(ColoringAttributes.NICEST);
		colAttr.setColor(1f, 1f, 1f);
		appearance.setColoringAttributes(colAttr);

			  // Avoid rendering of voxels having an alpha value of zero
		RenderingAttributes rendAttr = new RenderingAttributes();
		rendAttr.setCapability(
			RenderingAttributes.ALLOW_ALPHA_TEST_VALUE_WRITE);
 		rendAttr.setAlphaTestValue(0.1f);
		rendAttr.setAlphaTestFunction(RenderingAttributes.GREATER);
		appearance.setRenderingAttributes(rendAttr);

		appearance.setTexture(getTexture());
		appearance.setTexCoordGeneration(getTg());

		return appearance;
	}

	public Shape3D createShape() {
		Shape3D shape = new Shape3D(
			createGeometry(),
			createAppearance());
		return shape;
	}

	public void createImage() {
		bImage = new BufferedImage(w, h, B_IMG_TYPE);
		byte[] pixels = ((DataBufferByte)bImage.getRaster().getDataBuffer()).getData();
		bProcessor = new ByteProcessor(w, h, pixels, null);
		imp = new ImagePlus("Please draw", bProcessor);
	}

	public Texture getTexture() {

		Texture2D tex = new Texture2D(Texture.BASE_LEVEL, TEX_MODE, w, h);
		bComp = new ImageComponent2D(COMP_TYPE, w, h, BY_REF, Y_UP);
		bComp.setCapability(ImageComponent.ALLOW_IMAGE_WRITE);
		bComp.set(bImage);

		tex.setImage(0, bComp);
		tex.setEnable(true);
		tex.setMinFilter(Texture.BASE_LEVEL_LINEAR);
		tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);

		tex.setBoundaryModeS(Texture.CLAMP);
		tex.setBoundaryModeT(Texture.CLAMP);
		return tex;
	}

	public TexCoordGeneration getTg() {
		float xTexGenScale = (float)(1.0 / w);
		float yTexGenScale = (float)(1.0 / h);
		TexCoordGeneration tg = new TexCoordGeneration();
		tg.setPlaneS(new Vector4f(xTexGenScale, 0f, 0f, 0f));
		tg.setPlaneT(new Vector4f(0f, yTexGenScale, 0f, 0f));
		return tg;
	}

	public GeometryArray createGeometry() {
		QuadArray quadArray = new QuadArray(4, 
					GeometryArray.COORDINATES |
					GeometryArray.COLOR_3);
		Point3f[] coords = new Point3f[4];
		coords[0] = new Point3f(0, 0, 0);
		coords[1] = new Point3f(w, 0, 0);
		coords[2] = new Point3f(w, h, 0);
		coords[3] = new Point3f(0, h, 0);

		Color3f[] colors = new Color3f[4];
		colors[0] = new Color3f(100, 100, 100);
		colors[1] = new Color3f(100, 100, 100);
		colors[2] = new Color3f(100, 100, 100);
		colors[3] = new Color3f(100, 100, 100);

		quadArray.setCoordinates(0, coords);
		quadArray.setColors(0, colors);
		return quadArray;
	}

	private final int nextPow2(int n) {
		int retval = 2;
		while (retval < n) {
			retval = retval << 1;
		}
		return retval;
	}

	private class ImageUpdater implements ImageComponent2D.Updater {

		public void updateData(ImageComponent2D comp, int x, int y, int w, int h) {
		}
	}

//	private class UpdateBehavior extends Behavior {
//		
//		private final WakeupOnElapsedFrames wakeup;
//		
//		public UpdateBehavior() {
//			wakeup = new WakeupOnElapsedFrames(1, true);
//		}
//		
//		public void initialize() {
//			wakeupOn(wakeup);
//		}
//		
//		public synchronized void processStimulus(Enumeration criteria) {
//			System.out.println("processStim");
//			while(criteria.hasMoreElements()) {
//			}
//			wakeupOn(wakeup);
//		}
//	}
}
