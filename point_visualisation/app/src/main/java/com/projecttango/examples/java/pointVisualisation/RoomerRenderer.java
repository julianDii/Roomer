package com.projecttango.examples.java.pointVisualisation;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;
import com.projecttango.examples.java.pointVisualisation.DataStructure.DestinationPoint;
import com.projecttango.examples.java.pointVisualisation.DataStructure.NavigationPoint;
import com.projecttango.examples.java.pointVisualisation.DataStructure.Point;
import com.projecttango.examples.java.pointVisualisation.visualisationTools.DefineNav;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.methods.SpecularMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.RajawaliRenderer;

import java.util.ArrayList;

/**
 * Created by Julian Dobrot on 24.05.2016.
 * This Renderer gets points from a array list the first point is the start the last point is the
 * goal. The points between start and goal are the way points. Way points are only shown in front
 * of the user other points will disappear. The farest showen point in front of the user is an
 * arrow in the supposed direction. the Goal is a marker. The Camera is used for the environment
 * Later the Poinst for the list will be calculated by the route.
 */
public class RoomerRenderer extends RajawaliRenderer {

    private static final String TAG = AugmentedRealityRenderer.class.getSimpleName();

    ScreenQuad backgroundQuad;
    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;
    // The current screen rotation index. The index value follow the Android surface rotation enum:
    // http://developer.android.com/reference/android/view/Surface.html#ROTATION_0
    private int mCurrentScreenRotation = 0;

    private boolean draw = false;
    public ArrayList<Point> points = new ArrayList<Point>();



    // Rajawali texture used to render the Tango color camera
    private ATexture mTangoCameraTexture;

    // Keeps track of whether the scene camera has been configured
    private boolean mSceneCameraConfigured;


    public RoomerRenderer(Context context) {
        super(context);

    }

    public void setPoints(ArrayList<Point> points){

        draw = true;
        this.points = points;


    }

    @Override
    protected void initScene() {

        backgroundQuad = new ScreenQuad();
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);

        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);

        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            backgroundQuad.setMaterial(tangoCameraMaterial);
        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }

        getCurrentScene().addChildAt(backgroundQuad,0);

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

        if (draw) {
            pointsFromList();

            draw = false;
        }

        TangoPoseData pose = null;
        try {
            pose =
                    TangoSupport.getPoseAtTime(0.0, TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                            TangoPoseData.COORDINATE_FRAME_DEVICE,
                            TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                            mCurrentScreenRotation);
        }catch (Exception e){
            Log.e("Render_Pose_error", "Keine Pose Daten");
        }
        if (pose !=null) {

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

        super.onRender(ellapsedRealtime, deltaTime);
    }

    private void pointsFromList() {

        getCurrentScene().clearChildren();
        getCurrentScene().addChildAt(backgroundQuad,0);

        for (Point point : points) {

            Sphere s = new Sphere(0.1f, 10, 10);
            Material mSphereMaterial = new Material();
            mSphereMaterial.enableLighting(true);
            mSphereMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
            mSphereMaterial.setSpecularMethod(new SpecularMethod.Phong());
            s.setMaterial(mSphereMaterial);

            getCurrentScene().addChild(s);
            s.setPosition(point.getPosition());
            break;

        }

    }





    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {

    }

    @Override
    public void onTouchEvent(MotionEvent event) {

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

    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }




}
