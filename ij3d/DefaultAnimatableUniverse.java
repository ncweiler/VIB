package ij3d;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.ImageStack;
import ij.IJ;

import ij3d.ImageWindow3D;
import ij3d.ImageCanvas3D;

import isosurface.Triangulator;
import isosurface.MeshGroup;

import voltex.VoltexGroup;

import marchingcubes.MCTriangulator;

import java.awt.Panel;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.*;
import java.awt.GraphicsConfiguration;

import java.util.List;
import java.util.Hashtable;
import java.util.Iterator;

import com.sun.j3d.utils.picking.behaviors.PickingCallback;
import com.sun.j3d.utils.behaviors.keyboard.*;
import com.sun.j3d.utils.applet.MainFrame; 
import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.geometry.ColorCube;
import com.sun.j3d.utils.geometry.Sphere;
import javax.media.j3d.*;
import javax.media.j3d.Alpha;
import javax.media.j3d.RotationInterpolator;
import javax.vecmath.*;

import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickResult;

public abstract class DefaultAnimatableUniverse extends DefaultUniverse {

	private Alpha animation;

	public DefaultAnimatableUniverse(int width, int height) {
		super(width, height);

		// Animation
		animation = new Alpha(-1, 4000);
		animation.pause();
		animation.setStartTime(
			java.util.Calendar.getInstance().getTimeInMillis());
		RotationInterpolator rotpol = 
			new RotationInterpolator(animation, rotationsTG) {
			public void processStimulus(java.util.Enumeration e) {
				super.processStimulus(e);
				if(animated) {
					TransformGroup tg = null;
					transformChanged(0, tg);
				}
			}
		};
		rotpol.setSchedulingBounds(bounds);
		rotationsTG.addChild(rotpol);

		root.compile();
		addBranchGraph(root);
	}

	private ImageStack stack;
	private boolean freeRecording = false;
	private boolean animatedRecording = false;
	private boolean animated = false;

	public void transformChanged(int type, TransformGroup tg) {
		super.transformChanged(type, tg);
		if(animatedRecording) {
			return;
		} else if(freeRecording) {
			win.updateImagePlus();
			ImageProcessor ip = win.getImagePlus().getProcessor();
			int w = ip.getWidth(), h = ip.getHeight();
			if(stack == null) 
				stack = new ImageStack(w, h);
			stack.addSlice("", ip);
		}
	}

	private Transform3D rotate = new Transform3D();
	public void record360() {
		rotationsTG.getTransform(rotate);
		double angle = 0;
		final double pi2 = Math.PI*2;
		getCanvas().getView().stopView();
		while(angle < pi2) {
			rotate.rotY(angle);
			rotationsTG.setTransform(rotate);
			transformChanged(-1, rotationsTG);
			getCanvas().getView().renderOnce();
			try {
				Thread.currentThread().sleep(100);
			} catch (Exception e) {e.printStackTrace();}
			win.updateImagePlus();
			ImageProcessor ip = win.getImagePlus().getProcessor();
			int w = ip.getWidth(), h = ip.getHeight();
			if(stack == null) 
				stack = new ImageStack(w, h);
			stack.addSlice("", ip);
			angle += 0.04;
		}
		getCanvas().getView().startView();
		stopRecording().show();
	}

	public void startRecording() {
		ImageProcessor ip = win.getImagePlus().getProcessor();
		stack = new ImageStack(ip.getWidth(), ip.getHeight());
		if(animated) {
			pauseAnimation();
			freeRecording = false;
			animatedRecording = true;
			record360();
			startAnimation();
		} else {
			animatedRecording = false;
			freeRecording = true;
		}
	}

	public ImagePlus stopRecording() {
		animatedRecording = false;
		freeRecording = false;
		if(stack == null)
			return null;
		ImagePlus imp = new ImagePlus("Movie", stack);
		stack = null;
		return imp;
	}

	public void startAnimation() {
		animation.resume();
		animated = true;
		if(freeRecording) {
			freeRecording = false;
			animatedRecording = true;
		}
		fireTransformationStarted();
	}

	public void pauseAnimation() {
		animation.pause();
		animated = false;
		if(animatedRecording) {
			animatedRecording = false;
			freeRecording = true;
		}
		mouseBehavior.correctGlobalAndLocalTransformations();
		TransformGroup tg = null;
		transformChanged(0, tg);
		fireTransformationFinished();
	}
} 
