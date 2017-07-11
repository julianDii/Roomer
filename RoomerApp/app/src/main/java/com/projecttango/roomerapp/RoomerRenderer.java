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

package com.projecttango.roomerapp;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.DataStructure.DestinationPoint;
import com.projecttango.DataStructure.NavigationPoint;
import com.projecttango.DataStructure.Point;
import com.projecttango.DataStructure.RoomerDB;
import com.projecttango.Visualisation.Visualize;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.renderables.Grid;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Line3D;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.RajawaliRenderer;

import java.util.ArrayList;
import java.util.Stack;

import javax.microedition.khronos.opengles.GL10;

/**
 * This class implements the rendering logic for the Motion Tracking application using Rajawali.
 */
public class RoomerRenderer extends RajawaliRenderer {
    private static final String TAG = RoomerRenderer.class.getSimpleName();

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
    private boolean reDraw = false;

    public RoomerRenderer(Context context) {
        super(context);
    }

    // Rajawali texture used to render the Tango color camera
    private ATexture mTangoCameraTexture;

    // Keeps track of whether the scene camera has been configured
    private boolean mSceneCameraConfigured;
  
    @Override
    protected void initScene() {

        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        ScreenQuad backgroundQuad = new ScreenQuad();
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            backgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(backgroundQuad, 0);
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





        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        try{
            if(reDraw)Visualize.draw(getCurrentScene());
        }catch (Exception e){

        }

        // Perform the actual OpenGL rendering of the updated objects
        super.onRender(ellapsedRealtime, deltaTime);
    }

    @Override
    public void onOffsetsChanged(float v, float v1, float v2, float v3, int i, int i1) {
      // Unused, but needs to be declared to adhere to the IRajawaliSurfaceRenderer interface.
    }

    /**
     * We need to override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
        mSceneCameraConfigured = false;
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scen camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(TangoCameraIntrinsics intrinsics) {
        Matrix4 projectionMatrix = ScenePoseCalculator.calculateProjectionMatrix(
                intrinsics.width, intrinsics.height,
                intrinsics.fx, intrinsics.fy, intrinsics.cx, intrinsics.cy);
        getCurrentCamera().setProjectionMatrix(projectionMatrix);
    }
    @Override
    public void onTouchEvent(MotionEvent motionEvent) {
      // Unused, but needs to be declared to adhere to the IRajawaliSurfaceRenderer interface.
    }
    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The device pose should match the pose of the device at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData devicePose, DeviceExtrinsics extrinsics) {
        Pose cameraPose = ScenePoseCalculator.toOpenGlCameraPose(devicePose, extrinsics);
        getCurrentCamera().setRotation(cameraPose.getOrientation());
        getCurrentCamera().setPosition(cameraPose.getPosition());
    }
    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }


    public void setPoints(ArrayList<Point> points) {
        this.points = points;
        Visualize.setPoints(points);
        reDraw =true;
    }
}
