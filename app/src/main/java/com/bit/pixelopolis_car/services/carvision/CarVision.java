/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bit.pixelopolis_car.services.carvision;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.bit.pixelopolis_car.services.camera.PixelCamera;
import com.bit.pixelopolis_car.services.camera.PixelCameraOverlayDrawer;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class CarVision {
    private static String TAG = "CarVision";
    private PixelCamera camera;
    private PixelCameraOverlayDrawer cameraOverlayDrawer;
    private float steeringAngle = 0.0f;
    private AssetManager assetManager;
    private Mat detectedFrame;
    private List<ObjectDetector.DetectedObject> detectedObjects;
    private Context context;

    private boolean isFinished = false;
    private boolean isPause = false;

    public CarVision(PixelCamera camera, AssetManager assetManager, Context context)
    {
        this.camera = camera;
        this.cameraOverlayDrawer = camera.getCameraOverlayDrawer();
        this.assetManager = assetManager;
        this.context = context;
        if (!OpenCVLoader.initDebug())
            Log.e("CarVision", "Unable to load OpenCV");
        else
            Log.d("CarVision", "OpenCV loaded");

        new Thread(new MyRunnable()).start();
    }

    public float getSteeringAngle() {
        return steeringAngle;
    }

    public List<ObjectDetector.DetectedObject> getObjectFound()
    {
        return detectedObjects;
    }


    public class MyRunnable implements Runnable {
        private LaneDetector laneDetector;
        private ObjectDetector objectDetector;

        public void run(){
            try {
                laneDetector = new LaneDetector(assetManager);
            } catch (IOException e) {
                Log.e("CarVision", "Failed to initialize LaneDetector.", e);
            }

            try {
                objectDetector = TFLiteObjectDetectionAPIModel.create(assetManager,"object_detector.tflite",
                        "file:///android_asset/labels_piority.json",300,225,true);
            } catch (IOException e) {
                Log.e("CarVision", "Failed to initialize LaneDetector.", e);
            }

            while (!isFinished){
                if(!isPause) {
                    Mat cameraMat = camera.getCameraFrame();
                    steeringAngle = getCarSteering(cameraMat);
                    detectedObjects = getDetectedObjects(cameraMat).stream().filter(e -> e.getConfidence() > 0.7f).collect(Collectors.toList());
                    cameraOverlayDrawer.setDetectedObjects(detectedObjects);

                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                }
            }
        }

        private float getCarSteering(Mat frame) {
            if(frame == null || laneDetector ==null || frame.empty()) return 0;
            // assume lower half of the frame is road, then crop
            Rect roi = new Rect(0, 3*frame.size(0)/4, frame.size(1), frame.size(0)/4);
            Mat cropped = new Mat(frame, roi);
            // feed
            return laneDetector.classifyMat(cropped);
        }

        private List<ObjectDetector.DetectedObject> getDetectedObjects(Mat frame)
        {
            return objectDetector.recognizeImage(frame);
        }
    }

    public void destroy(){
        isFinished = true;
    }

    public void pause(){
        isPause = true;
    }

    public void resume(){
        isPause = false;
    }
}