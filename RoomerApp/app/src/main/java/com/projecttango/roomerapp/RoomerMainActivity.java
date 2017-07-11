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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.atap.tango.ux.TangoUx;
import com.google.atap.tango.ux.TangoUxLayout;
import com.google.atap.tango.ux.UxExceptionEvent;
import com.google.atap.tango.ux.UxExceptionEventListener;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.DataStructure.DestinationPoint;
import com.projecttango.DataStructure.NavigationPoint;
import com.projecttango.DataStructure.Point;
import com.projecttango.DataStructure.RoomerDB;
import com.projecttango.Dijkstra.VectorGraph;
import com.projecttango.Visualisation.Visualize;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.renderables.primitives.Points;

import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main Activity class for the Motion Tracking API Sample. Handles the connection to the Tango
 * service and propagation of Tango pose data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link RoomerRenderer} class.
 */
public class RoomerMainActivity extends Activity {

    private static final String TAG = RoomerMainActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private static final double UPDATE_INTERVAL_MS = 100.0;

    private double mXyIjPreviousTimeStamp;;
    private double mTimeToNextUpdate = UPDATE_INTERVAL_MS;

    private Tango mTango;

    private TangoUx mTangoUx;
    private RoomerRenderer mRenderer;
    private final Object mSharedLock = new Object();
    private boolean mIsRelocalized;

    private boolean mIsPointsLoad;

    private AtomicBoolean mIsConnected = new AtomicBoolean(false);

    private TextView txtLocalized;
    private String uuid;
    private RoomerDB db;

    private static  TangoCoordinateFramePair FRAME_PAIR = new TangoCoordinateFramePair(
            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
            TangoPoseData.COORDINATE_FRAME_DEVICE);

    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    private RajawaliSurfaceView mSurfaceView;
    private TangoCameraIntrinsics mIntrinsics;
    private DeviceExtrinsics mExtrinsics;
    private double mCameraPoseTimestamp = 0;
    private int countRelocationPoints = 0;

    private static final int INVALID_TEXTURE_ID = 0;
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;

    // Handles the debug text UI update loop.



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_roomer);
        Intent i = getIntent();
        uuid = i.getStringExtra("uuid");

        mRenderer = setupGLViewAndRenderer();
        mTangoUx = setupTangoUxAndLayout();
        txtLocalized = (TextView) findViewById(R.id.txtLocalized);
        db = new RoomerDB(this,uuid);
        try {
            db.importDB(getBaseContext());


        }catch (Exception e){
            Toast.makeText(getBaseContext(), e.toString(), Toast.LENGTH_LONG)
                    .show();
        }

    }

    /**
     * Sets Rajawalisurface view and its renderer. This is ideally called only once in onCreate.
     */
    private RoomerRenderer setupGLViewAndRenderer() {
        RoomerRenderer renderer = new RoomerRenderer(this);
        mSurfaceView  = (RajawaliSurfaceView) findViewById(R.id.gl_surface_view);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setSurfaceRenderer(renderer);
        return renderer;

    }



    @Override
    protected void onPause() {
        super.onPause();
        if (mIsConnected.compareAndSet(true, false)) {
            mTangoUx.stop();
            mIsRelocalized = false;
            mRenderer.onPause();
            mRenderer.getCurrentScene().clearFrameCallbacks();
            mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
            // We need to invalidate the connected texture ID so that we cause a re-connection
            // in the OpenGL thread after resume
            mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
            try {
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Toast.makeText(getApplicationContext(),
                        R.string.exception_tango_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mIsConnected.compareAndSet(false, true)) {
            // Initialize Tango Service as a normal Android Service, since we call
            // mTango.disconnect() in onPause, this will unbind Tango Service, so
            // everytime when onResume get called, we should create a new Tango object.
            mTangoUx.start(new TangoUx.StartParams());
            mTango = new Tango(RoomerMainActivity.this, new Runnable() {
                // Pass in a Runnable to be called from UI thread when Tango is ready,
                // this Runnable will be running on a new thread.
                // When Tango is ready, we can call Tango functions safely here only
                // when there is no UI thread changes involved.
                @Override
                public void run() {
                    try {
                        connectTango();
                        setupRenderer();
                    } catch (TangoOutOfDateException outDateEx) {
                        if (mTangoUx != null) {
                            mTangoUx.showTangoOutOfDate();
                        }
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (SecurityException e) {
                        Log.e(TAG, getString(R.string.permission_motion_tracking), e);
                    }
                }
            });
        }
    }

    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void setupRenderer() {
        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks had a chance to run and before scene objects are rendered
                // into the scene.

                // Prevent concurrent access to {@code mIsFrameAvailableTangoThread} from the Tango
                // callback thread and service disconnection from an onPause event.
                synchronized (RoomerMainActivity.this) {
                    // Don't execute any tango API actions if we're not connected to the service
                    if (mIsConnected.get()==false) {
                        return;
                    }


                    // Set-up scene camera projection to match RGB camera intrinsics
                    if (!mRenderer.isSceneCameraConfigured()) {
                        mRenderer.setProjectionMatrix(mIntrinsics);
                    }

                    // Connect the camera texture to the OpenGL Texture if necessary
                    // NOTE: When the OpenGL context is recycled, Rajawali may re-generate the
                    // texture with a different ID.
                    if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                        mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                mRenderer.getTextureId());
                        mConnectedTextureIdGlThread = mRenderer.getTextureId();
                        Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                    }

                    // If there is a new RGB camera frame available, update the texture with it
                    if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                        mRgbTimestampGlThread =
                                mTango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                    }

                    // If a new RGB frame has been rendered, update the camera pose to match.
                    if (mRgbTimestampGlThread > mCameraPoseTimestamp) {
                        // Calculate the device pose at the camera frame update time.
                        TangoPoseData lastFramePose = mTango.getPoseAtTime(mRgbTimestampGlThread,
                                FRAME_PAIR);
                        if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                            // Update the camera pose from the renderer
                            mRenderer.updateRenderCameraPose(lastFramePose, mExtrinsics);
                            mCameraPoseTimestamp = lastFramePose.timestamp;
                        } else {
                            Log.w(TAG, "Can't get device pose at time: " + mRgbTimestampGlThread);
                        }
                    }
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }

            @Override
            public boolean callPreFrame() {
                return true;
            }
        });
    }



    /**
     * Set up the callback listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void connectTango() {
        // Create a new Tango Configuration and enable the RoomerMainActivity API
        TangoConfig config;
        config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);

        // Tango service should automatically attempt to recover when it enters an invalid state.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);

        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
           config.putBoolean(
                  TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);



   //Set adf file
            config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION,
                    uuid);

        mTango.connect(config);

        // Set Tango Listeners for Poses Device wrt Start of Service, Device wrt
        // ADF and Start of Service wrt ADF
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzij) {
                final double currentTimeStamp = xyzij.timestamp;
                final double pointCloudFrameDelta = (currentTimeStamp - mXyIjPreviousTimeStamp)
                        * SECS_TO_MILLISECS;
                mXyIjPreviousTimeStamp = currentTimeStamp;


                mTimeToNextUpdate -= pointCloudFrameDelta;

                if (mTimeToNextUpdate < 0.0) {
                    mTimeToNextUpdate = UPDATE_INTERVAL_MS;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                         if(mIsRelocalized){
                             txtLocalized.setVisibility(View.INVISIBLE);
                             //Show Distance
                             int distance = 0;
                             ArrayList<Point> points = Visualize.getPoints();
                             for(int i = 1; i< points.size(); i++){
                                 distance+= Vector3.distanceTo2(
                                         points.get(i-1).getPosition(),
                                         points.get(i).getPosition());
                             }
                             Vector3 cp = new Vector3(
                                     mRenderer.getCurrentCamera().getPosition().x,
                                     mRenderer.getCurrentCamera().getPosition().y - 1,
                                     mRenderer.getCurrentCamera().getPosition().z);
                             if(!points.isEmpty())distance+= Vector3.distanceTo2(
                                     Visualize.getPoints().get(0).getPosition(),
                                     cp);

                             TextView lblDistance = new TextView(getBaseContext());
                             RelativeLayout relInfo = (RelativeLayout)findViewById(R.id.relInfo);
                             relInfo.removeAllViews();
                             relInfo.addView(lblDistance);
                             RelativeLayout.LayoutParams layoutParams =
                                     (RelativeLayout.LayoutParams)relInfo.getLayoutParams();
                             layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
                             layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                             relInfo.setLayoutParams(layoutParams);
                             String s = distance +"m";
                             lblDistance.setText(s);

                         }else{
                             if (countRelocationPoints>4 ) countRelocationPoints=0;
                             String s = ".";
                             for(int i = 0; i <countRelocationPoints;i+=2){
                                 s+=".";
                             }
                             countRelocationPoints++;
                             final String showString = getString(R.string.tryToLocate) + s;
                             txtLocalized.setText(showString);
                         }

                    }
                });
                }
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                if (mTangoUx != null) {
                    mTangoUx.updateTangoEvent(event);
                }
            }

            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                if (mTangoUx != null) {
                    mTangoUx.updatePoseStatus(pose.statusCode);
                }

                // Make sure to have atomic access to Tango Data so that
                // UI loop doesn't interfere while Pose call back is updating
                // the data.
                synchronized (mSharedLock) {
                    // Check for Device wrt ADF pose, Device wrt Start of Service pose,
                    // Start of Service wrt ADF pose (This pose determines if the device
                    // is relocalized or not).
                    if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData
                            .COORDINATE_FRAME_START_OF_SERVICE) {
                        mIsRelocalized = pose.statusCode == TangoPoseData.POSE_VALID;

                        if(mIsRelocalized && !mIsPointsLoad){
                            mIsPointsLoad=true;
                            FRAME_PAIR = new TangoCoordinateFramePair(
                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                    TangoPoseData.COORDINATE_FRAME_DEVICE);
                            ArrayList<Point> points = db.loadPoints();
                            Point dest = null;
                            for(Point p : points){
                                if(p instanceof DestinationPoint){
                                    dest = p;
                                    break;
                                }
                            }
                            mRenderer.setPoints(
                                    VectorGraph.getPath(
                                            mRenderer.getCurrentCamera().getPosition(),
                                            dest,
                                            points)
                            );
                        }

                    }


                }
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Mark a camera frame is available for rendering in the OpenGL thread
                    mIsFrameAvailableTangoThread.set(true);
                    // Trigger an Rajawali render to update the scene with the new RGB data.
                    mSurfaceView.requestRender();
                }
            }

        });
        mExtrinsics = setupExtrinsics(mTango);
        mIntrinsics = mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
    }


    /**
     * Calculates and stores the fixed transformations between the device and
     * the various sensors to be used later for transformations between frames.
     */
    private static DeviceExtrinsics setupExtrinsics(Tango tango) {
        // Create camera to IMU transform.
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        TangoPoseData imuTrgbPose = tango.getPoseAtTime(0.0, framePair);

        // Create device to IMU transform.
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        TangoPoseData imuTdevicePose = tango.getPoseAtTime(0.0, framePair);

        // Create depth camera to IMU transform.
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
        TangoPoseData imuTdepthPose = tango.getPoseAtTime(0.0, framePair);

        return new DeviceExtrinsics(imuTdevicePose, imuTrgbPose, imuTdepthPose);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    /**
     * Sets up TangoUX layout and sets its listener.
     */
    private TangoUx setupTangoUxAndLayout() {
        TangoUxLayout uxLayout = (TangoUxLayout) findViewById(R.id.layout_tango);
        TangoUx tangoUx = new TangoUx(this);
        tangoUx.setLayout(uxLayout);
        tangoUx.setUxExceptionEventListener(mUxExceptionListener);
        return tangoUx;
    }

    private UxExceptionEventListener mUxExceptionListener = new UxExceptionEventListener() {

        @Override
        public void onUxExceptionEvent(UxExceptionEvent uxExceptionEvent) {
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_LYING_ON_SURFACE) {
                Log.i(TAG, "Device lying on surface ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_DEPTH_POINTS) {
                Log.i(TAG, "Very few depth points in mPoint cloud ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_FEATURES) {
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_INCOMPATIBLE_VM) {
                Log.i(TAG, "Device not running on ART");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOTION_TRACK_INVALID) {
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOVING_TOO_FAST) {
                Log.i(TAG, "Invalid poses in MotionTracking ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_OVER_EXPOSED) {
                Log.i(TAG, "Camera Over Exposed");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_TANGO_SERVICE_NOT_RESPONDING) {
                Log.i(TAG, "TangoService is not responding ");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_UNDER_EXPOSED) {
                Log.i(TAG, "Camera Under Exposed ");
            }

        }
    };



}
