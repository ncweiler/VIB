package vib.app.module;

import amira.AmiraTable;

import java.util.ArrayList;

import math3d.Point3d;

import vib.app.State;
import vib.app.ImageMetaData;

import vib.FastMatrix;

public class LabelCenterTransformation extends Module {
	public final String name = "LabelCenterTransformation";

	public static void run(State state, int index) {
		// make sure that the template gets statistics, too
		TissueStatistics.runOnOneImage(state, -1);
		TissueStatistics.runOnOneImage(state, index);

		String statisticsPath = state.getStatisticsPath(index);
		String templStatisticsPath = state.getStatisticsPath(-1);
		ImageMetaData stats = new ImageMetaData(statisticsPath);
		if (state.upToDate(templStatisticsPath, statisticsPath) &&
				stats.getMatrix(state.getTransformLabel())
				!= null)
			return;
		ImageMetaData templStats =
			new ImageMetaData(templStatisticsPath);

		ArrayList templCenters = new ArrayList();
		ArrayList centers = new ArrayList();
		// skip i == 0 (it's Exterior)
		for (int i = 1; i < templStats.materials.length; i++) {
			ImageMetaData.Material m1 = templStats.materials[i];
			String name = m1.name;
			ImageMetaData.Material m2 = stats.getMaterial(name);
			if (m2 == null)
				continue;
			Point3d center = new Point3d(m1.centerX,
					m1.centerY, m1.centerZ);
			templCenters.add(center);
			center = new Point3d(m2.centerX,
					m2.centerY, m2.centerZ);
			centers.add(center);
		}
		if (centers.size() == 0)
			return;
		Point3d[] c1 = new Point3d[centers.size()];
		Point3d[] c2 = new Point3d[centers.size()];
		for (int i = 0; i < c1.length; i++) {
			c1[i] = (Point3d)templCenters.get(i);
			c2[i] = (Point3d)centers.get(i);
		}
		FastMatrix matrix = FastMatrix.bestRigid(c1, c2);
		stats.setMatrix(state.getTransformLabel(), matrix);
		stats.saveTo(statisticsPath);
	}
}
