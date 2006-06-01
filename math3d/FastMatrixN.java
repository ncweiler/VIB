package math3d;

public class FastMatrixN {
	public static void invert(double[][] matrix) {
		int M = matrix.length;

		if (M != matrix[0].length)
			throw new RuntimeException("invert: no square matrix");

		double[][] other = new double[M][M];
		for (int i = 0; i < M; i++)
			other[i][i] = 1;

		// empty lower left triangle
		for (int i = 0; i < M; i++) {
			// find pivot
			int p = i;
			for (int j = i + 1; j < M; j++)
				if (Math.abs(matrix[j][i]) >
						Math.abs(matrix[p][i]))
					p = j;

			if (p != i) {
				double[] d = matrix[p];
				matrix[p] = matrix[i];
				matrix[i] = d;
				d = other[p];
				other[p] = other[i];
				other[i] = d;
			}

			// normalize
			if (matrix[i][i] != 1.0) {
				double f = matrix[i][i];
				for (int j = i; j < M; j++)
					matrix[i][j] /= f;
				for (int j = 0; j < M; j++)
					other[i][j] /= f;
			}

			// empty rest of column
			for (int j = i + 1; j < M; j++) {
				double f = matrix[j][i];
				for (int k = i; k < M; k++)
					matrix[j][k] -= f * matrix[i][k];
				for (int k = 0; k < M; k++)
					other[j][k] -= f * other[i][k];
			}
		}

		// empty upper right triangle
		for (int i = M - 1; i > 0; i--)
			for (int j = i - 1; j >= 0; j--) {
				double f = matrix[j][i] / matrix[i][i];
				for (int k = i; k < M; k++)
					matrix[j][k] -= f * matrix[i][k];
				for (int k = 0; k < M; k++)
					other[j][k] -= f * other[i][k];
			}

		// exchange
		for (int i = 0; i < M; i++)
			matrix[i] = other[i];
	}

	public static double[][] clone(double[][] matrix) {
		int M = matrix.length, N = matrix[0].length;
		double[][] result = new double[M][N];
		for (int i = 0; i < M; i++)
			System.arraycopy(matrix[i], 0, result[i], 0, N);
		return result;
	}

	public static double[][] times(double[][] m1, double[][] m2) {
		int K = m2.length;
		if (m1[0].length != m2.length)
			throw new RuntimeException("rank mismatch");
		int M = m1.length, N = m2[0].length;
		double[][] result = new double[M][N];
		for (int i = 0; i < M; i++)
			for (int j = 0; j < N; j++)
				for (int k = 0; k < K; k++)
					result[i][j] += m1[i][k] * m2[k][j];
		return result;
	}

	static void print(double[][] m) {
		for (int i = 0; i < m.length; i++) {
			for (int j = 0; j < m[i].length; j++)
				System.out.print(" " + m[i][j]);
			System.out.println("");
		}
	}

	public static void main(String[] args) {
		double[][] m = {
			{1, 2, 3, 2},
			{-1, 0, 2, -3},
			{-2, 1, 1, 1},
			{0, -2, 3, 0}};

		double[][] m1 = clone(m);
		invert(m1);

		double[][] m2 = times(m, m1);
		print(m2);
	}
}

