package octree;

import ij.IJ;
import javax.media.j3d.View;
import vib.Resample_;

import ij3d.UniverseListener;
import ij3d.Content;

import java.io.File;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;

import ij.measure.Calibration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.Group;
import javax.media.j3d.OrderedGroup;
import javax.media.j3d.Switch;
import javax.media.j3d.Transform3D;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import voltex.VolRendConstants;
import voltex.Renderer;

public class VolumeOctree implements UniverseListener, VolRendConstants {

	public static final int SIZE = 128;

	static final int DETAIL_AXIS = 6;

	private static final int[][] axisIndex = new int[3][2];

	final Switch axisSwitch;

	private String outdir;
	private ImagePlus imp;

	private Cube root;
	private BranchGroup bg;

	private int curAxis = Z_AXIS;
	private int curDir = FRONT;

	private final int xdim, ydim, zdim;
	final float pw, ph, pd;
	private final Point3d refPt;
	private OctreeBehavior behavior;

	private boolean cancelUpdating = false;
	private CubeUpdater updater = new CubeUpdater();

	public VolumeOctree(ImagePlus imp, Canvas3D canvas) {
		this.imp = imp;
		String basename = imp.getTitle();
		if(basename.toLowerCase().endsWith(".tif"))
			basename = basename.substring(0, basename.length() - 4);
		File dir = new File(System.getProperty("java.io.tmpdir"), basename);
		dir.mkdir();
		outdir = dir.getAbsolutePath();

		xdim = nextPow2(imp.getWidth());
		ydim = nextPow2(imp.getHeight());
		zdim = nextPow2(imp.getStackSize());

		Calibration c = imp.getCalibration();
		pw = (float)c.pixelWidth;
		ph = (float)c.pixelHeight;
		pd = (float)c.pixelDepth;

		axisIndex[X_AXIS][FRONT] = 0;
		axisIndex[X_AXIS][BACK]  = 1;
		axisIndex[Y_AXIS][FRONT] = 2;
		axisIndex[Y_AXIS][BACK]  = 3;
		axisIndex[Z_AXIS][FRONT] = 4;
		axisIndex[Z_AXIS][BACK]  = 5;

		refPt = new Point3d(xdim*pw / 2, ydim*ph / 2, zdim*pd / 2);

		axisSwitch = new Switch();
		axisSwitch.setCapability(Switch.ALLOW_SWITCH_WRITE);

		for(int i = 0; i < 7; i++)
			axisSwitch.addChild(getOrderedGroup());

		bg = new BranchGroup();
		bg.addChild(axisSwitch);
		bg.setCapability(BranchGroup.ALLOW_DETACH);
		bg.setCapability(BranchGroup.ALLOW_LOCAL_TO_VWORLD_READ);

		behavior = new OctreeBehavior(canvas, this);
		behavior.setSchedulingBounds(new BoundingSphere());
		behavior.setEnable(true);
		bg.addChild(behavior);
	}

	public void update() {
		behavior.postId(OctreeBehavior.TRIGGER_ID);
	}

	public void cancel() {
		if(!isUpdateFinished()) {
			setCancelUpdating(true);
			updater.cancel();
		}
	}

	private int countInitialShapes() {
		int sum = 0;
		for(int i = 0; i < 6; i++) {
			OrderedGroup og = (OrderedGroup)axisSwitch.getChild(i);
			sum += og.numChildren();
		}
		return sum;
	}

	private int countDetailShapes() {
		return ((Group)axisSwitch.getChild(DETAIL_AXIS)).numChildren();
	}
	private synchronized boolean isCancelUpdating() {
		return cancelUpdating;
	}

	private final Group getOrderedGroup() {
		OrderedGroup og = new OrderedGroup();
		og.setCapability(Group.ALLOW_CHILDREN_WRITE);
		og.setCapability(Group.ALLOW_CHILDREN_EXTEND);
		return og;
	}

	public synchronized void setCancelUpdating(boolean b) {
		cancelUpdating = b;
	}

	public void displayInitial() {
		OrderedGroup fg, bg;
		int[] axis = new int[] {X_AXIS, Y_AXIS, Z_AXIS};

		for(int ai = 0; ai < 3; ai++) {
			CubeData cdata = new CubeData(root);
			cdata.prepareForAxis(axis[ai]);
			try {
				cdata.createData();
			} catch(Exception e) {
				e.printStackTrace();
			}
			Arrays.sort(cdata.shapes);
			fg = (OrderedGroup)axisSwitch.getChild(axisIndex[axis[ai]][FRONT]);
			bg = (OrderedGroup)axisSwitch.getChild(axisIndex[axis[ai]][BACK]);
			for(int i = 0; i < SIZE; i++) {
				fg.addChild(new ShapeGroup(cdata.shapes[i]));
				bg.insertChild(cdata.shapes[i], 0);
			}
		}
		axisSwitch.setWhichChild(axisIndex[curAxis][curDir]);
		System.out.println("# shapes: " + countInitialShapes());
	}
	
	private Transform3D volumeToImagePlate = new Transform3D();
	private Transform3D tmp = new Transform3D();
	/*
	 * This method should only be called from OctreeBehavior
	 */
	private boolean updateFinished = true;
	void display(Canvas3D canvas) {
		setUpdateFinished(false);

		// calculate the current view parameters
		canvas.getImagePlateToVworld(volumeToImagePlate);
		volumeToImagePlate.invert();
		bg.getLocalToVworld(tmp);
		volumeToImagePlate.mul(tmp);

		// collect the cubes which should be displayed
		List<Cube> cubes = new ArrayList<Cube>();
		root.collectCubesToDisplay(cubes, canvas, volumeToImagePlate, curAxis, curDir);
		System.out.println("Found " + cubes.size() + " cubes to display");
		ShapeGroup[] shapes = new ShapeGroup[cubes.size() * SIZE];
		System.out.println("This gives " + shapes.length + " shapes");
		int i = 0;
		for(Cube c : cubes) {
			CubeData cdata = c.getCubeData();
			for(ShapeGroup sg : cdata.shapes)
				shapes[i++] = sg;
		}

		// sort according to z-order
		Arrays.sort(shapes);
		final OrderedGroup og = (OrderedGroup)axisSwitch.getChild(DETAIL_AXIS);
		og.removeAllChildren();
		updater.updateCubes(cubes);

		// add the ShapeGroups of the collected cubes to the scenegraph
		displayShapes(og, shapes, curDir);
		CubeDataRecycler.instance().clearAll();
		setUpdateFinished(true);
		if(isCancelUpdating()) {
			System.out.println("Aborted");
			root.cleanup();
			setCancelUpdating(false);
		} else {
			System.out.println("# shapes: " + countDetailShapes());
		}
	}

	void displayShapes(OrderedGroup og, ShapeGroup[] shapes, int dir) {
		if(dir == FRONT) {
			for(int i = 0; i < shapes.length; i++) {
				if(isCancelUpdating())
					break;
				og.addChild(shapes[i]);
				if(i % 50 == 0)
					IJ.showProgress(i, shapes.length);
			}
		} else {
			for(int i = shapes.length - 1; i >= 0; i--) {
				if(isCancelUpdating())
					break;
				og.addChild(shapes[i]);
				if(i % 50 == 0)
					IJ.showProgress(shapes.length - i, shapes.length);
			}
		}
		IJ.showProgress(1);
	}

	public synchronized boolean isUpdateFinished() {
		return updateFinished;
	}

	public synchronized void setUpdateFinished(boolean b) {
		this.updateFinished = b;
	}

	public BranchGroup getRootBranchGroup() {
		return bg;
	}

	public Cube getRootCube() {
		return root;
	}

	public void create() {
		// For now, skip the creation of files, since I'm testing
		// with the same file over and over again.
//		int l = createFiles();
		int l = 8;
		imp.close();
		root = new Cube(this, outdir, 0, 0, 0, l);
		root.createChildren();
	}

	public int createFiles() {
		System.out.println("create Files");
		makePowerOfTwo();
		int w = imp.getWidth(), h = imp.getHeight();
		int d = imp.getStackSize();
		int l = 1;

		while(true) {
			for(int z = 0; z < d; z += SIZE) {
				for(int y = 0; y < h; y += SIZE) {
					for(int x = 0; x < w; x += SIZE) {
						String path = outdir + "/" + (x*l) + "_" + (y*l) + "_" + (z*l) + "_" + l + ".tif";
						if(new File(path).exists())
							continue;
						ImagePlus im = createSubvolume(x, y, z);
						saveCube(im, path);
					}
				}
			}
			int factorX = w > SIZE ? 2 : 1;
			int factorY = h > SIZE ? 2 : 1;
			int factorZ = d > SIZE ? 2 : 1;
			if(factorX == 1 && factorY == 1 && factorZ == 1)
				break;
			imp = Resample_.resample(imp, factorX, factorY, factorZ);
			w = imp.getWidth();
			h = imp.getHeight();
			d = imp.getStackSize();
			l = l << 1;
		}
		System.out.println("Finished create files");
		return l;
	}

	private void saveCube(ImagePlus image, String path) {
		byte[][] data = new byte[SIZE][];
		ImageStack stack = image.getStack();
		Calibration cal = image.getCalibration();
		for(int z = 0; z < SIZE; z++)
			data[z] = (byte[])stack.getPixels(z+1);
		try {
			CubeData.writeZData(path, data, 
				(float)cal.pixelWidth,
				(float)cal.pixelHeight,
				(float)cal.pixelDepth);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	private ImagePlus createSubvolume(int x, int y, int z) {
		System.out.println("create Subvolume("+x+","+y+","+z+")");
		int w = imp.getWidth(), h = imp.getHeight();
		int d = imp.getStackSize();

		if(x == w-1 || y == h-1 || z == d-1)
			return null;
		ImageStack newStack = new ImageStack(SIZE, SIZE);
		ImageStack oldStack = imp.getStack();

		for(int zi = 0; zi < SIZE; zi++) {
			byte[] p_old = (byte[])oldStack.getPixels(z + zi + 1);
			byte[] p_new = new byte[SIZE * SIZE];
			for(int yi = 0; yi < SIZE; yi++) {
				System.arraycopy(p_old, (yi + y) * w + x, 
					  p_new, yi * SIZE, SIZE);
			}
			newStack.addSlice("", p_new);
		}
		ImagePlus ret = new ImagePlus("", newStack);
		Calibration cal = imp.getCalibration().copy();
		cal.xOrigin = x;
		cal.yOrigin = y;
		cal.zOrigin = z;
		ret.setCalibration(cal);
		return ret;
	}
		

	private void makePowerOfTwo() {
		int w = imp.getWidth(), h = imp.getHeight();
		int d = imp.getStackSize();

		int wn = nextPow2(w);
		int hn = nextPow2(h);
		int dn = nextPow2(d);

		ImageStack newStack = new ImageStack(w, h);
		ImageStack oldStack = imp.getStack();

		int z = 0;
		for(z = 0; z < d; z++) {
			byte[] p_old = (byte[])oldStack.getPixels(z+1);
			byte[] p_new = new byte[wn * hn];
			for(int y = 0; y < h; y++) {
				System.arraycopy(p_old, y * w, p_new, y * wn, w);
			}
			newStack.addSlice("", p_new);
		}

		// add empty slices
		for(; z < dn; z++) {
			newStack.addSlice("", new ByteProcessor(wn, hn));
		}
		imp.setStack(null,newStack);
	}

	private final int nextPow2(int n) {
		int retval = 2;
		while (retval < n) {
			retval = retval << 1;
		}
		return retval;
	}

	private Vector3d eyeVec = new Vector3d();
	public void transformationUpdated(View view){
		Point3d eyePt = Renderer.getViewPosInLocal(view, bg);
		if (eyePt == null)
			return;
		eyeVec.sub(eyePt, refPt);

		// select the axis with the greatest magnitude
		int axis = X_AXIS;
		double value = eyeVec.x;
		double max = Math.abs(eyeVec.x);
		if (Math.abs(eyeVec.y) > max) {
			axis = Y_AXIS;
			value = eyeVec.y;
			max = Math.abs(eyeVec.y);
		}
		if (Math.abs(eyeVec.z) > max) {
			axis = Z_AXIS;
			value = eyeVec.z;
			max = Math.abs(eyeVec.z);
		}

		// select the direction based on the sign of the magnitude
		int dir = value > 0.0 ? FRONT : BACK;
		
		if ((axis != curAxis) || (dir != curDir)) {
			curAxis = axis;
			curDir = dir;
			axisSwitch.setWhichChild(axisIndex[curAxis][curDir]);
		}
	}

	public void transformationStarted(View view){
		axisSwitch.setWhichChild(axisIndex[curAxis][curDir]);
		cancel();
	}
	public void transformationFinished(View view){
		update();
		axisSwitch.setWhichChild(DETAIL_AXIS);
	}
	public void contentAdded(Content c){}
	public void contentRemoved(Content c){}
	public void contentChanged(Content c){}
	public void contentSelected(Content c){}
	public void canvasResized(){}
	public void universeClosed(){}
}

