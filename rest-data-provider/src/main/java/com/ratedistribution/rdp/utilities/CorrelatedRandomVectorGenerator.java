package com.ratedistribution.rdp.utilities;

import com.ratedistribution.rdp.config.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class CorrelatedRandomVectorGenerator {
    private final RealMatrix L;
    private final int dimension;

    @Autowired
    public CorrelatedRandomVectorGenerator(SimulatorProperties simulatorProperties) {
        List<List<Double>> listOfLists = simulatorProperties.getCorrelationMatrix();
        if (listOfLists == null || listOfLists.isEmpty()) {
            throw new IllegalArgumentException("correlationMatrix is empty or not configured!");
        }
        double[][] corrMatrix = convertTo2DArray(listOfLists);

        this.dimension = corrMatrix.length;
        RealMatrix corr = MatrixUtils.createRealMatrix(corrMatrix);
        CholeskyDecomposition decomposition = new CholeskyDecomposition(corr, 1.0e-12, 1.0e-12);
        this.L = decomposition.getL();
    }

    private double[][] convertTo2DArray(List<List<Double>> listOfLists) {
        int n = listOfLists.size();
        double[][] array = new double[n][n];
        for (int i = 0; i < n; i++) {
            List<Double> row = listOfLists.get(i);
            for (int j = 0; j < n; j++) {
                array[i][j] = row.get(j);
            }
        }
        return array;
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