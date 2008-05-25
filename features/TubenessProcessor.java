/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package features;

public class TubenessProcessor extends HessianEvalueProcessor {

	public double measureFromEvalues2D( double [] evalues ) {

		/* If either of the two principle eigenvalues is
		   positive then the curvature is in the wrong
		   direction - towards higher instensities rather than
		   lower. */

		if ((evalues[1] >= 0) || (evalues[0] >= 0))
			return 0;
		else
			return Math.abs(evalues[1]);
	}

	public double measureFromEvalues3D( double [] evalues ) {

		/* If either of the two principle eigenvalues is
		   positive then the curvature is in the wrong
		   direction - towards higher instensities rather than
		   lower. */

		if ((evalues[1] >= 0) || (evalues[2] >= 0))
			return 0;
		else
			return Math.sqrt(evalues[2] * evalues[1]);
	}
}
