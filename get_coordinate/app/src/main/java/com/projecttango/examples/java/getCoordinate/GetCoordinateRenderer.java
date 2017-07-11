/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.java.getCoordinate;

import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.RajawaliRenderer;
import org.rajawali3d.util.ObjectColorPicker;

import com.projecttango.DataStructure.DestinationPoint;
import com.projecttango.DataStructure.NavigationPoint;
import com.projecttango.DataStructure.Point;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.renderables.Grid;

import com.projecttango.tangosupport.TangoSupport;

import java.util.ArrayList;
import java.util.Stack;

/**
 * This class implements the rendering logic for the Motion Tracking application using Rajawali.
 */
public class GetCoordinateRenderer extends RajawaliRenderer {
    private static final String TAG = GetCoordinateRenderer.class.getSimpleName();

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;

    // Latest available device pose;
    private Pose mDevicePose = new Pose(Vector3.ZERO, Quaternion.getIdentity());
    private boolean mPoseUpdated = false;
  
    // The current screen rotation index. The index value follow the Android surface rotation enum:
    // http://developer.android.com/reference/android/view/Surface.html#ROTATION_0
    private int mCurrentScreenRotation = 0;

    private boolean addNavPoint =true;
    private boolean addDestPoint =true;
    private int countDestPoints = 0;
    private int countNavPoints =0;

    public boolean reloadList = false;

    public ArrayList<Point> points = new ArrayList<Point>();
    public boolean reDraw = false;

    public boolean isRelocated = false;
    private Sphere sphere =new Sphere(0.5f,20,20);


    public GetCoordinateRenderer(Context context) {
        super(context);
    }

    public void setCurrentScreenRotation(int currentRotation) {
        mCurrentScreenRotation = currentRotation;
        //Zeigt an ob Tablet in Hoch oder querformat und wie herum
    }
  
    @Override
    protected void initScene() {
        Grid grid = new Grid(100, 1, 1, 0xFFCCCCCC);
        grid.setPosition(0, -1.3f, 0);
        getCurrentScene().addChild(grid);
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        DirectionalLight light2 = new DirectionalLight(-1, 0.2, -1);
        light.setColor(1, 4, 4);
        light.setPower(0.8f);
        light.setPosition(3, 3, 3);
        getCurrentScene().addLight(light2);
        Material mSphereMaterial = new Material();
        mSphereMaterial.enableLighting(true);
        mSphereMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
        mSphereMaterial.setSpecularMethod(new SpecularMethod.Phong());
        sphere.setMaterial(mSphereMaterial);
        getCurrentScene().addChild(sphere);





        getCurrentScene().setBackgroundColor(Color.WHITE);


        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);
    }

    public Bitmap textAsBitmap(String text) {// For later usage
        Paint paint = new Paint();
        paint.setTextSize(16);
        paint.setColor(0x666666);
        paint.setUnderlineText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        int width = (int) (paint.measureText(text) + 0.5f); // round
        float baseline = 10;//(int) (paint.ascent() + 0.5f);
        int height = (int) (baseline + paint.descent() + 0.5f);
        Log.d("DEBUGGER", width +"  " + height + " " + baseline);
        Bitmap image = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawText(text, 0, baseline, paint);
        return image;
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        // Update the scene objects with the latest device position and orientation information.
        // Synchronize to avoid concurrent access from the Tango callback thread below.
        try {
            sphere.setPosition(new Vector3(0,0,0));
            Log.d("DEBUGGER",getCurrentCamera().getProjectionMatrix().getTranslation(sphere.getPosition())+"");
            if(!addNavPoint){
                addNavPoint = true;


                Vector3 p = new Vector3(getCurrentCamera().getPosition().x,getCurrentCamera().getPosition().y-1,getCurrentCamera().getPosition().z);

                Sphere s = new Sphere(0.1f, 20, 20);
                Material mSphereMaterial = new Material();
                mSphereMaterial.enableLighting(true);
                mSphereMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
                mSphereMaterial.setSpecularMethod(new SpecularMethod.Phong());
                s.setMaterial(mSphereMaterial);

                getCurrentScene().addChild(s);
                s.setPosition(p);

                Point point = new NavigationPoint(p,null,"NavPoint" +countNavPoints);

                countNavPoints++;
                points.add(point);
                reloadList=true;
            }
            if(!addDestPoint){
                addDestPoint = true;


                Vector3 p = new Vector3(getCurrentCamera().getPosition().x,getCurrentCamera().getPosition().y-1,getCurrentCamera().getPosition().z);

                Sphere s = new Sphere(0.1f, 20, 20);
                Material mSphereMaterial = new Material();
                mSphereMaterial.enableLighting(true);
                mSphereMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
                mSphereMaterial.setSpecularMethod(new SpecularMethod.Phong());
                mSphereMaterial.setColor(Color.GREEN);
                s.setMaterial(mSphereMaterial);

                getCurrentScene().addChild(s);
                s.setPosition(p);
                Point point = new DestinationPoint(p,null,"DestPoint" +countDestPoints);
                countDestPoints++;
                points.add(point);
                reloadList=true;
            }

            if(reDraw){
                reDraw=false;
                Point p = points.get(points.size()-1);

                for(Point n : p.getNeighbours().keySet()){
                    Stack<Vector3> stack = new Stack<Vector3>();
                    stack.add(p.getPosition());
                    stack.add(n.getPosition());
                    Object3D line = new Line3D(stack,50,Color.RED);
                    Material m = new Material();
                    m.setColor(Color.RED);
                    line.setMaterial(m);
                    getCurrentScene().addChild(line);
                }
            }


            TangoPoseData pose =null;
            try {
               if(isRelocated){
                   pose =
                           TangoSupport.getPoseAtTime(0.0, TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                   TangoPoseData.COORDINATE_FRAME_DEVICE,
                                   TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                   mCurrentScreenRotation);
               }else{
                   pose =
                           TangoSupport.getPoseAtTime(0.0, TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                   TangoPoseData.COORDINATE_FRAME_DEVICE,
                                   TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                   mCurrentScreenRotation);
               }
            }catch (Exception e){
                Log.e("Render_Pose_error", "Keine Pose Daten");
            }
            if(pose !=null) {
                if (pose.statusCode == TangoPoseData.POSE_VALID) {
                    getCurrentCamera().setPosition((float) pose.translation[0],
                            (float) pose.translation[1],
                            (float) pose.translation[2]);


                    Quaternion invOrientation = new Quaternion((float) pose.rotation[3],
                            (float) pose.rotation[0],
                            (float) pose.rotation[1],
                            (float) pose.rotation[2]);

                    // For some reason, rajawalli's orientation is inversed.
                    Quaternion orientation = invOrientation.inverse();
                    getCurrentCamera().setOrientation(orientation);
                }
            }
        } catch (TangoErrorException e) {
            Log.e(TAG, "TangoSupport.getPoseAtTime error", e);
        }

        // Perform the actual OpenGL rendering of the updated objects
        super.onRender(ellapsedRealtime, deltaTime);
    }

    @Override
    public void onOffsetsChanged(float v, float v1, float v2, float v3, int i, int i1) {
      // Unused, but needs to be declared to adhere to the IRajawaliSurfaceRenderer interface.
    }

    @Override
    public void onTouchEvent(MotionEvent motionEvent) {
      // Unused, but needs to be declared to adhere to the IRajawaliSurfaceRenderer interface.
    }

    public  void addNavPoint() {
        addNavPoint =false;

    }
    public  void addDestPoint() {
        addDestPoint =false;

    }

}
