package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ShockState: Redis'e yazılabilir olması için Serializable işaretledik.
 * shockLevel => 1.0 normal, >1.0 şok durumu
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShockState implements Serializable {
    private boolean active;
    private int durationLeft;
    private double shockLevel;
}
