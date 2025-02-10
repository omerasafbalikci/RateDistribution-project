package com.ratedistribution.rdp.utilities;

import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.concurrent.ThreadLocalRandom;

public class CorrelatedRandomVectorGenerator {
    private final RealMatrix L;
    private final int dimension;

    public CorrelatedRandomVectorGenerator(double[][] corrMatrix) {
        this.dimension = corrMatrix.length;
        RealMatrix corr = MatrixUtils.createRealMatrix(corrMatrix);
        CholeskyDecomposition decomposition = new CholeskyDecomposition(corr, 1.0e-12, 1.0e-12);
        this.L = decomposition.getL();
    }

    public double[] sample() {
        double[] y = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            y[i] = ThreadLocalRandom.current().nextGaussian();
        }
        RealMatrix vecY = MatrixUtils.createColumnRealMatrix(y);
        RealMatrix correlated = L.multiply(vecY);
        return correlated.getColumn(0);
    }
}