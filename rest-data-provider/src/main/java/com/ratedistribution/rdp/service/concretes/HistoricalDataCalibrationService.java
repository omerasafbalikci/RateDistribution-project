package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.model.GarchParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tarihsel veriden GARCH(1,1) parametrelerini
 * (omega, alpha, beta) MLE ile (naif bir yöntemle) tahmin etmeye çalışan servis.
 */
@Service
@RequiredArgsConstructor
public class HistoricalDataCalibrationService {

    private final SimulatorProperties simulatorProperties;
    private static final double TWO_PI = 2.0 * Math.PI;

    /**
     * Bu metot, CSV'den ilgili rateName kolonunu okuyup,
     * log-getiriler listesi çıkardıktan sonra
     * basit bir MLE optimizasyonu (random veya grid) uygular.
     */
    public GarchParams calibrateGarchParams(String rateName) {
        String csvPath = simulatorProperties.getHistoricalDataPath();
        if (csvPath == null || csvPath.isEmpty()) {
            return new GarchParams(0.0000000001, 0.0005, 0.85);
        }

        // 1) CSV'den price serisi oku
        List<Double> priceList = readPriceSeriesFromCsv(csvPath, rateName);
        if (priceList.size() < 2) {
            return new GarchParams(0.000001, 0.05, 0.90);
        }

        // 2) Log-return hesapla
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < priceList.size(); i++) {
            double r = Math.log(priceList.get(i) / priceList.get(i - 1));
            returns.add(r);
        }

        // 3) MLE (naif yaklaşım) - random search
        // Parametre aralıkları:
        //  0 < omega < 1e-3
        //  0 < alpha < 1
        //  0 < beta < 1
        //  alpha + beta < 1 (kararlılık)
        GarchParams bestParams = new GarchParams(1e-6, 0.05, 0.9);
        double bestLL = Double.NEGATIVE_INFINITY;

        Random rnd = new Random();
        int iterations = 500; // Basit örnek, daha fazla arttırabilirsiniz
        for (int i = 0; i < iterations; i++) {
            double omega = rnd.nextDouble() * 1e-3;  // [0, 0.001]
            double alpha = rnd.nextDouble() * 0.99;  // [0, 0.99]
            double beta  = rnd.nextDouble() * 0.99;  // [0, 0.99]
            // Kararlılık: alpha+beta<1
            if (alpha + beta >= 0.999) {
                continue;
            }

            double ll = garchLogLikelihood(returns, omega, alpha, beta);
            if (ll > bestLL) {
                bestLL = ll;
                bestParams = new GarchParams(omega, alpha, beta);
            }
        }


        return bestParams;
    }

    /**
     * Basit bir NxN Markov matrisi kalibrasyonu için placeholder:
     * Gerçekte rejim tespiti (örn. high vol / low vol) ile transition sayılır ve normalize edilir.
     */
    public List<List<Double>> calibrateMarkovMatrix() {
        // Örnek 3x3:
        List<List<Double>> matrix = new ArrayList<>();
        matrix.add(List.of(0.80, 0.15, 0.05));
        matrix.add(List.of(0.10, 0.75, 0.15));
        matrix.add(List.of(0.05, 0.15, 0.80));
        return matrix;
    }

    /**
     * GARCH(1,1) log-likelihood fonksiyonu.
     * returns: r_t dizisi
     */
    private double garchLogLikelihood(List<Double> returns, double omega, double alpha, double beta) {
        double logLikelihood = 0.0;

        // Sigma^2 başlangıç
        double var = 1e-6; // rastgele küçük bir başlangıç

        for (double r : returns) {
            // loglik = -0.5 [ log(2pi) + log(var) + r^2/var ]
            logLikelihood += -0.5 * (Math.log(TWO_PI) + Math.log(var) + (r*r / var));

            // Bir sonraki adıma var update
            double eps2 = r * r;
            var = omega + alpha * eps2 + beta * var;
            if (var < 1e-16) {
                var = 1e-16; // numerical stability
            }
        }

        return logLikelihood;
    }

    /**
     * CSV okuyup "rateName" kolonunun değerlerini döndürür.
     * Basit bir implementasyon.
     * Örnek CSV formatı:
     * date,EURUSD,USDTRY,XAUUSD
     * 2024-01-01,1.0100,18.0,1852
     * 2024-01-02,1.0120,18.01,1850
     * ...
     */
    private List<Double> readPriceSeriesFromCsv(String csvPath, String rateName) {
        List<Double> prices = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine();
            if (header == null) {
                return prices;
            }
            String[] cols = header.split(",");
            int rateColIndex = -1;
            for (int i = 0; i < cols.length; i++) {
                if (cols[i].equalsIgnoreCase(rateName)) {
                    rateColIndex = i;
                    break;
                }
            }
            if (rateColIndex == -1) {
                return prices;
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length <= rateColIndex) continue;
                double val = Double.parseDouble(parts[rateColIndex]);
                prices.add(val);
            }
        } catch (Exception e) {
        }
        return prices;
    }
}
