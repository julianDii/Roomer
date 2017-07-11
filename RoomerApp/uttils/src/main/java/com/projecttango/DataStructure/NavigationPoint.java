package com.projecttango.DataStructure;

import org.rajawali3d.math.vector.Vector3;

import java.util.HashMap;

/**
 * Created by marcu_000 on 23.05.2016.
 */
public class NavigationPoint extends Point {
    public NavigationPoint(Vector3 position, HashMap<Point, Double> neighbours, String tag) {
        super(position, neighbours, tag);
    }
}
