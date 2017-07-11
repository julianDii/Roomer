package com.projecttango.Visualisation;

import android.graphics.Color;
import android.util.Log;

import com.projecttango.DataStructure.NavigationPoint;
import com.projecttango.DataStructure.Point;

import org.rajawali3d.curves.CatmullRomCurve3D;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.scene.RajawaliScene;

import java.util.ArrayList;

/**
 * Created by Marcus Bätz on 26.05.2016.
 * This Class Visualize the navigation path
 */
public class Visualize {

    private static ArrayList<Point> points = new ArrayList<Point>();
    private final static Material material2 = new Material();
    private final static Material material1 = new Material();

    static {
        material2.setDiffuseMethod(new DiffuseMethod.Lambert());
        material2.setColor(Color.GREEN);
        material2.enableLighting(true);

        material1.setDiffuseMethod(new DiffuseMethod.Lambert());
        material1.setSpecularMethod(new SpecularMethod.Phong());
        material1.setColor(Color.YELLOW);
        material1.enableLighting(true);
    }

    /**
     * Sets the Points of a calculated NavPath
     * @param points An sorted ArrayList of Points that represents the NavPath. At 0 is start
     *               at point.size()-1 ist destination
     */
    public static void setPoints(ArrayList<Point> points) {
        Visualize.points = points;

    }

    public static ArrayList<Point> getPoints() {
        return points;

    }

    /**
     * This Methode actualize a given scene with a visualization of the Nav path from user
     * position to destination if the NavPath is set
     * @param scene The Rajawali scene where the visualization has to be added
     */
    public static void draw(RajawaliScene scene) {
        //Save the Backscreenquad
        ScreenQuad sq = (ScreenQuad) scene.getChildrenCopy().get(0);
        //Clear all Elements from Scene
        scene.clearChildren();
        //Add the Backscreenquad back again
        scene.addChildAt(sq, 0);
        //generate a new Point for the actual position of the user
        Vector3 cp = new Vector3(
                scene.getCamera().getPosition().x,
                scene.getCamera().getPosition().y - 1,
                scene.getCamera().getPosition().z);

        //remove waypoints that the camera has crossed
        if (cp.distanceTo(points.get(0).getPosition()) < 1) {
            points.remove(0);
        }

        if (!points.isEmpty()) {
            //Generate an approximated curve over all points an the users position
            //First an last have to be doubled
            CatmullRomCurve3D n = new CatmullRomCurve3D();
            n.addPoint(cp);
            n.addPoint(cp);
            for (Point p : points) {
                n.addPoint(p.getPosition());
            }
            n.addPoint(points.get(points.size() - 1).getPosition());

            //equalize the elements for the same distance between each cube
            n.reparametrizeForUniformDistribution(n.getNumPoints() * 4);
            //Generate list ov NavigationPoints
            ArrayList<Vector3> vP = new ArrayList<Vector3>();
            int count = ((int) n.getLength(10)) * 4;
            for (int i = 0; i < count; i++) {
                Vector3 result = new Vector3();
                double t = (i * 1.0) / (count * 1.0);
                n.calculatePoint(result, t);
                vP.add(result);
            }
            // generate for each navigation point a small square except the last,
            // this one gets a big square
            for (int i = 0; i < vP.size(); i++) {
                Cube s;
                if (i == vP.size() - 1) {
                    s = new Cube(0.2f);
                    s.setMaterial(material1);
                } else {
                    s = new Cube(0.05f);
                    s.setMaterial(material2);
                }
                s.setPosition(vP.get(i));
                scene.addChild(s);
            }

        }

    }


    public static void main(String[] args) {
        //Test Method
        setPoints(new ArrayList<Point>());
        draw(null);
    }


}
