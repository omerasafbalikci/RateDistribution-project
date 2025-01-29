package com.ratedistribution.rdp.model;

import java.io.Serializable;

/**
 * ShockState: Redis'e yazılabilir olması için Serializable işaretledik.
 * shockLevel => 1.0 normal, >1.0 şok durumu
 */
public class ShockState implements Serializable {
    private boolean active;
    private int durationLeft;
    private double shockLevel;

    public ShockState() {
    }

    public ShockState(boolean active, int durationLeft, double shockLevel) {
        this.active = active;
        this.durationLeft = durationLeft;
        this.shockLevel = shockLevel;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getDurationLeft() {
        return durationLeft;
    }

    public void setDurationLeft(int durationLeft) {
        this.durationLeft = durationLeft;
    }

    public double getShockLevel() {
        return shockLevel;
    }

    public void setShockLevel(double shockLevel) {
        this.shockLevel = shockLevel;
    }
}
