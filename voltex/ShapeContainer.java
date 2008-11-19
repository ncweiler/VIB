package voltex;

import ij.ImagePlus;
import javax.media.j3d.Appearance;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.OrderedGroup;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Switch;

public class ShapeContainer implements VolRendConstants {
	
	final int xdim, ydim, zdim;
	final double pw, ph, pd;
	final int CUBE_SIZE = VolumeOctree.SIZE;

//	private static final int[][] axisIndex = new int[3][2];

	final Switch axisSwitch;

	public ShapeContainer(int xdim, int ydim, int zdim,
			double pw, double ph, double pd) {
		this.xdim = xdim;
		this.ydim = ydim;
		this.zdim = zdim;

		this.pw = pw;
		this.ph = ph;
		this.pd = pd;

		System.out.println("xdim = " + xdim);
		System.out.println("ydim = " + ydim);
		System.out.println("zdim = " + zdim);


//		axisIndex[X_AXIS][FRONT] = 0;
//		axisIndex[X_AXIS][BACK]  = 1;
//		axisIndex[Y_AXIS][FRONT] = 2;
//		axisIndex[Y_AXIS][BACK]  = 3;
//		axisIndex[Z_AXIS][FRONT] = 4;
//		axisIndex[Z_AXIS][BACK]  = 5;

		axisSwitch = new Switch();
		axisSwitch.setCapability(Switch.ALLOW_SWITCH_READ);
		axisSwitch.setCapability(Switch.ALLOW_SWITCH_WRITE);
		axisSwitch.setCapability(Group.ALLOW_CHILDREN_READ);
		axisSwitch.setCapability(Group.ALLOW_CHILDREN_WRITE);

		axisSwitch.addChild(getOrderedGroup());
		axisSwitch.addChild(getOrderedGroup());

		axisSwitch.addChild(getOrderedGroup());
		axisSwitch.addChild(getOrderedGroup());

		axisSwitch.addChild(getOrderedGroup());
		axisSwitch.addChild(getOrderedGroup());
//		axisSwitch.setWhichChild(axisIndex[Z_AXIS][FRONT]);
	}

//	public void setAxis(int axis, int dir){
//		this.curAxis = axis;
//		this.curDir = dir;
//	}

//	private int curAxis = Z_AXIS;
//	private int curDir = FRONT;

	public int countShapeGroups() {
		int sum = 0;
		for(int i = 0; i < 6; i++) {
			OrderedGroup og = (OrderedGroup)axisSwitch.getChild(i);
			sum += og.numChildren();
		}
		return sum;
	}


	public int displayCube(Cube c, int whichChild) {
		int curAxis = whichChild / 2;
		int curDir = whichChild % 2;
		System.out.println("display cube " + c);
//		int whichChild = axisIndex[curAxis][curDir];
		System.out.println("displayCube: whichChild = " + whichChild);

		ImagePlus imp = CubeOpener.openCube(c.dir, c.name + ".tif");
		Volume volume = new Volume(imp, Volume.TRANSLUCENT);
		AppearanceCreator appCreator = new AppearanceCreator(volume);
		GeometryCreator geomCreator = new GeometryCreator(volume);

		OrderedGroup og = (OrderedGroup)axisSwitch.getChild(whichChild);
		float pos;
		int dim = volume.zDim;
//		switch(curAxis) {
		switch(whichChild / 2) {
			case Z_AXIS: dim = volume.zDim; break;
			case Y_AXIS: dim = volume.yDim; break;
			case X_AXIS: dim = volume.xDim; break;
		}
		axisSwitch.setWhichChild(whichChild);

		for(int i = 0; i < dim; i++) {
			GeometryArray g = geomCreator.getQuad(curAxis, i);
			Appearance a = appCreator.getAppearance(curAxis, i);
			pos = geomCreator.getPos();
			ShapeGroup sg = new ShapeGroup(new Shape3D(g, a), pos, c.name);
			if(curDir == FRONT)
				insertAscending(og, sg, 0, og.numChildren()-1);
			else
				insertDescending(og, sg, 0, og.numChildren()-1);
//			og.addChild(sg);
		}
		return whichChild;

//		for(int i = 0; i < volume.xDim; i++) {
//			GeometryArray g = geomCreator.getQuad(X_AXIS, i);
//			Appearance a = appCreator.getAppearance(X_AXIS, i);
//			pos = geomCreator.getPos();
//			og = (OrderedGroup)axisSwitch.getChild(axisIndex[X_AXIS][FRONT]);
//			og.addChild(new ShapeGroup(new Shape3D(g, a), pos, c.name));
//			og = (OrderedGroup)axisSwitch.getChild(axisIndex[X_AXIS][BACK]);
//			og.addChild(new ShapeGroup(new Shape3D(g, a), pos, c.name));
//		}
//
//		for(int i = 0; i < volume.yDim; i++) {
//			GeometryArray g = geomCreator.getQuad(Y_AXIS, i);
//			Appearance a = appCreator.getAppearance(Y_AXIS, i);
//			pos = geomCreator.getPos();
//			og = (OrderedGroup)axisSwitch.getChild(axisIndex[Y_AXIS][FRONT]);
//			og.addChild(new ShapeGroup(new Shape3D(g, a), pos, c.name));
//			og = (OrderedGroup)axisSwitch.getChild(axisIndex[Y_AXIS][BACK]);
//			og.addChild(new ShapeGroup(new Shape3D(g, a), pos, c.name));
//		}
//
//		for(int i = 0; i < volume.zDim; i++) {
//			GeometryArray g = geomCreator.getQuad(Z_AXIS, i);
//			Appearance a = appCreator.getAppearance(Z_AXIS, i);
//			pos = geomCreator.getPos();
//			og = (OrderedGroup)axisSwitch.getChild(axisIndex[Z_AXIS][FRONT]);
//			og.addChild(new ShapeGroup(new Shape3D(g, a), pos, c.name));
//			og = (OrderedGroup)axisSwitch.getChild(axisIndex[Z_AXIS][BACK]);
//			og.addChild(new ShapeGroup(new Shape3D(g, a), pos, c.name));
//		}
	}

	public void undisplayCube(Cube c, int axishint) {
		System.out.println("undisplay cube " + c);
//		for(int i = 0; i < 6; i++) {
//			OrderedGroup og = (OrderedGroup)axisSwitch.getChild(i);
		OrderedGroup og = (OrderedGroup)axisSwitch.getChild(axishint);
		int n = og.numChildren();
		for(int k = n-1; k >= 0; k--) {
			ShapeGroup sg = (ShapeGroup)og.getChild(k);
			if(sg.getName().equals(c.name))
				og.removeChild(sg);
//		}
		}
	}

	private final Group getOrderedGroup() {
		OrderedGroup og = new OrderedGroup();
		og.setCapability(Group.ALLOW_CHILDREN_WRITE);
		og.setCapability(Group.ALLOW_CHILDREN_EXTEND);
		og.setCapability(OrderedGroup.ALLOW_CHILD_INDEX_ORDER_WRITE);
		return og;
	}

	private static final void insertDescending(OrderedGroup shapes, ShapeGroup s, int left, int right) {
		if(shapes.numChildren() == 0 || s.pos >= ((ShapeGroup)shapes.getChild(left)).pos)
			shapes.insertChild(s, left);
		else if(s.pos <= ((ShapeGroup)shapes.getChild(right)).pos)
			shapes.insertChild(s, right+1);
		else {
			int piv = (left + right) / 2;
			float pivpos = ((ShapeGroup)shapes.getChild(piv)).pos;
			if(pivpos > s.pos)
				insertDescending(shapes, s, piv+1, right);
			else if(pivpos < s.pos)
				insertDescending(shapes, s, left, piv-1);
			else if(pivpos == s.pos)
				shapes.insertChild(s, piv);
		}
	}

	private static final void insertAscending(OrderedGroup shapes, ShapeGroup s, int left, int right) {
		if(shapes.numChildren() == 0 || s.pos <= ((ShapeGroup)shapes.getChild(left)).pos)
			shapes.insertChild(s, left);
		else if(s.pos >= ((ShapeGroup)shapes.getChild(right)).pos)
			shapes.insertChild(s, right+1);
		else {
			int piv = (left + right) / 2;
			float pivpos = ((ShapeGroup)shapes.getChild(piv)).pos;
			if(pivpos < s.pos)
				insertAscending(shapes, s, piv+1, right);
			else if(pivpos > s.pos)
				insertAscending(shapes, s, left, piv-1);
			else if(pivpos == s.pos)
				shapes.insertChild(s, piv);
		}
	}
}
