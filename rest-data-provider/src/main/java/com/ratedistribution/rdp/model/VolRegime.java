package com.ratedistribution.rdp.model;

public enum VolRegime {
    LOW_VOL(0),
    MID_VOL(1),
    HIGH_VOL(2);

    private final int index;

    VolRegime(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public static VolRegime fromIndex(int idx) {
        return switch (idx) {
            case 1 -> MID_VOL;
            case 2 -> HIGH_VOL;
            default -> LOW_VOL;
        };
    }
}
