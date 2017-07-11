package com.projecttango.examples.java.pointVisualisation.DataStructure;

import org.rajawali3d.math.vector.Vector3;

import java.util.HashMap;

/**
 * Created by marcu_000 on 23.05.2016.
 */
public class DestinationPoint extends Point {
    public DestinationPoint(Vector3 position, HashMap<Point, Double> neighbours, String tag) {
        super(position, neighbours, tag);
    }
}
