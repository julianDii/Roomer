package com.projecttango.examples.java.pointVisualisation.visualisationTools;

import com.projecttango.examples.java.pointVisualisation.DataStructure.Point;
import com.projecttango.examples.java.pointVisualisation.DataStructure.PositionPoint;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.renderer.RajawaliRenderer;
import java.util.ArrayList;

/**
 * Created by Julian Dobrot on 25.05.2016.
 * This Class provides helper methods to visualize the navigation with a given Point List.
 *
 */
public class DefineNav {

    private final RajawaliRenderer renderer;

    private ArrayList<Point> points = new ArrayList<Point>();

    public DefineNav(ArrayList<Point> points,RajawaliRenderer pose){


        this.points = points;
        this.renderer = pose;
    }

    /**
     * This method defines the current Endpoint of the current navigation.
     * @param points
     * @return
     */
    private Point defineEndpoint(ArrayList points){

        Point lastPoint = (Point) points.get(points.size()-1);
        return lastPoint;
    }

    /**
     * This method creates a new Position Point with from the device position
     * @return a new Position Point
     */
    public final PositionPoint setPositionPoint(){

        Vector3 position = getPositionVector();

        return new PositionPoint(position,null,"PositionPoint");
    }

    /**
     * This method defines a new List of points which are close to the user. So it filters the
     * points in ratio to the device position
     * @param amount The amount of points to put in the new List
     * @param points All points
     * @return
     */
    public final ArrayList<Point> getClosestPoints (double amount, ArrayList<Point> points){

        int size = points.size();
        PositionPoint positionPoint = setPositionPoint();
        positionPoint.getNeighbours();

        return null;
    }


    /**
     * This Method calculates the vector of the camera position.
     * @return Vector3 where the camera is located
     */
    public final Vector3 getPositionVector(){
        return new Vector3(renderer.getCurrentCamera().getPosition().x,renderer.getCurrentCamera().getPosition().y-1,renderer.getCurrentCamera().getPosition().z);
    }

    /**
     * This method transforms the vector of the current camera by the passed parameters x,y,z.
     * @param x
     * @param y
     * @param z
     * @return
     */
    public final Vector3 manipulateCameraPositionVector(int x,int y,int z)throws IllegalArgumentException{

       return new Vector3(renderer.getCurrentCamera().getPosition().x + (x),renderer.getCurrentCamera().getPosition().y + (y),renderer.getCurrentCamera().getPosition().z + (z));
    }

}