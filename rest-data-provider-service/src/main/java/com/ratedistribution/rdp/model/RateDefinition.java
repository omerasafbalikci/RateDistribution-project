package com.ratedistribution.rdp.model;

public class RateDefinition {
    private String rateName;
    private double basePrice;
    private double drift;
    private double volatility;
    private double baseSpread;
    private double shockProbability;
    private double shockMultiplier;
    private int shockDuration;   // update cycle sayısı
    private double shockDecayRate;
    private double kappa;
    private double theta;

    public String getRateName() {
        return rateName;
    }

    public void setRateName(String rateName) {
        this.rateName = rateName;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public double getDrift() {
        return drift;
    }

    public void setDrift(double drift) {
        this.drift = drift;
    }

    public double getVolatility() {
        return volatility;
    }

    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    public double getBaseSpread() {
        return baseSpread;
    }

    public void setBaseSpread(double baseSpread) {
        this.baseSpread = baseSpread;
    }

    public double getShockProbability() {
        return shockProbability;
    }

    public void setShockProbability(double shockProbability) {
        this.shockProbability = shockProbability;
    }

    public double getShockMultiplier() {
        return shockMultiplier;
    }

    public void setShockMultiplier(double shockMultiplier) {
        this.shockMultiplier = shockMultiplier;
    }

    public int getShockDuration() {
        return shockDuration;
    }

    public void setShockDuration(int shockDuration) {
        this.shockDuration = shockDuration;
    }

    public double getShockDecayRate() {
        return shockDecayRate;
    }

    public void setShockDecayRate(double shockDecayRate) {
        this.shockDecayRate = shockDecayRate;
    }

    public double getKappa() {
        return kappa;
    }

    public void setKappa(double kappa) {
        this.kappa = kappa;
    }

    public double getTheta() {
        return theta;
    }

    public void setTheta(double theta) {
        this.theta = theta;
    }
}
