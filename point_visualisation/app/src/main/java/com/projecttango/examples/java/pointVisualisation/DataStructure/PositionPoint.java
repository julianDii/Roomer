package com.projecttango.examples.java.pointVisualisation.DataStructure;

import org.rajawali3d.math.vector.Vector3;


import java.util.HashMap;

/**
 * Created by Julian Dobrot on 25.05.2016.
 */
public class PositionPoint extends Point {


    public PositionPoint(Vector3 position, HashMap<Point, Double> neighbours, String tag) {
        super(position, neighbours, tag);

    }
}
