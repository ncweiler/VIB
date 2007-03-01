package voltex;

import java.awt.*;
import java.awt.image.*;
import java.awt.color.ColorSpace;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.*;
import com.sun.j3d.utils.behaviors.mouse.*;
import ij.ImagePlus;

public class Axis2DRenderer extends AxisRenderer {

    private Texture2DVolume texVol;

    public Axis2DRenderer(View view,ImagePlus img, Color3f color) {
		super(view, img, color);
		texVol = new Texture2DVolume(volume);
    }

    void update() {
        int	texVolUpdate = texVol.update();
		switch (texVolUpdate) {
		  case Texture2DVolume.RELOAD_NONE:
			fullReloadNeeded = false;
			break;
		  case Texture2DVolume.RELOAD_VOLUME:
			fullReloadNeeded = true;
			break;
		}
		if (fullReloadNeeded) {
			fullReload();
		}
    }

	public void reloadTextureColorTable() {
		System.out.println("applyTextureColorTable");
		if(!volume.is8C)
			return;
		int[][] cmap = new int[4][256];
		IndexColorModel cmodel = volume.cmodel;
		for(int i = 0; i < 256; i++) {
			cmap[0][i] = cmodel.getRed(i);
			cmap[1][i] = cmodel.getGreen(i);
			cmap[2][i] = cmodel.getBlue(i);
			cmap[3][i] = cmodel.getAlpha(i);
		}

		int num = axisSwitch.numChildren();
		for(int i = 0; i < num; i++) {
			((Shape3D)axisSwitch.getChild(i)).getAppearance().
				getTextureAttributes().setTextureColorTable(cmap);
		}
	}

    void fullReload() {
		clearData();
		if (volume.hasData()) {
			loadQuads();
		}
		setWhichChild();
		fullReloadNeeded = false;
    }


    void loadQuads() {
		loadAxis(Z_AXIS);
		loadAxis(Y_AXIS);
		loadAxis(X_AXIS);
    }

    private void loadAxis(int axis) {
		int	rSize = 0;        // number of tex maps to create
		OrderedGroup frontGroup = null;
		OrderedGroup backGroup = null;
		Texture2D[] textures = null;
		TexCoordGeneration tg = null;

		switch (axis) {
		  case Z_AXIS:
			frontGroup = 
			(OrderedGroup)axisSwitch.getChild(axisIndex[Z_AXIS][FRONT]);
			backGroup = 
			(OrderedGroup)axisSwitch.getChild(axisIndex[Z_AXIS][BACK]);
			rSize = volume.zDim;
			textures = texVol.zTextures;
			tg = texVol.zTg;
			setCoordsZ();
			break;
		  case Y_AXIS:
			frontGroup = 
			(OrderedGroup)axisSwitch.getChild(axisIndex[Y_AXIS][FRONT]);
			backGroup = 
			(OrderedGroup)axisSwitch.getChild(axisIndex[Y_AXIS][BACK]);
			rSize = volume.yDim;
			textures = texVol.yTextures;
			tg = texVol.yTg;
			setCoordsY();
			break;
		  case X_AXIS:
			frontGroup = 
			(OrderedGroup)axisSwitch.getChild(axisIndex[X_AXIS][FRONT]);
			backGroup = 
			(OrderedGroup)axisSwitch.getChild(axisIndex[X_AXIS][BACK]);
			rSize = volume.xDim;
			textures = texVol.xTextures;
			tg = texVol.xTg;
			setCoordsX();
			break;
		}

		for (int i=0; i < rSize; i ++) { 

			switch (axis) {
			  case Z_AXIS: setCurCoordZ(i); break;
			  case Y_AXIS: setCurCoordY(i); break;
			  case X_AXIS: setCurCoordX(i); break;
			}

			Texture2D tex = textures[i];

			Appearance a = new Appearance();
			a.setMaterial(m);
			a.setTransparencyAttributes(t);
			a.setTexture(tex);
			a.setTextureAttributes(texAttr);
			a.setTexCoordGeneration(tg);
			a.setPolygonAttributes(p);
//			a.setColoringAttributes(colAttr);

			QuadArray quadArray = new QuadArray(4, GeometryArray.COORDINATES );
			quadArray.setCoordinates(0, quadCoords);

			Shape3D frontShape = new Shape3D(quadArray, a);

			BranchGroup frontShapeGroup = new BranchGroup();
			frontShapeGroup.setCapability(BranchGroup.ALLOW_DETACH);
			frontShapeGroup.addChild(frontShape);
			frontGroup.addChild(frontShapeGroup);

			Shape3D backShape = new Shape3D(quadArray, a);

			BranchGroup backShapeGroup = new BranchGroup();
			backShapeGroup.setCapability(BranchGroup.ALLOW_DETACH);
			backShapeGroup.addChild(backShape);
			backGroup.insertChild(backShapeGroup, 0);

		} 
    } 
}
