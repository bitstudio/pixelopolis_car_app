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

package com.bit.pixelopolis_car.services.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.view.SurfaceView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class PixelCamera implements CameraBridgeViewBase.CvCameraViewListener2{

    public interface StreamFrameCallback {
        void onStreamFrame(Mat frame, PixelCamera camera);
    }

    public interface CameraStateCallback {
        void onCameraStarted(int width,int height);
        void onCameraStopped();
    }

    private CameraBridgeViewBase cameraBridgeViewBase;
    private BaseLoaderCallback baseLoaderCallback;
    private Mat src;
    private Mat dst;
    private Mat streamDst;
    private Mat temp3c;
    private Context context;
    private PixelCameraOverlayDrawer cameraOverlayDrawer;
    private StreamFrameCallback streamCallback = null;
    private CameraStateCallback cameraStateCallback;
    private boolean isStarted = false;
    public PixelCamera(JavaCameraView javaCameraView, Context context, CameraStateCallback cameraStateCallback){
        this.cameraBridgeViewBase = javaCameraView;
        this.context = context;
        this.cameraStateCallback = cameraStateCallback;
        cameraBridgeViewBase.setCameraIndex(1);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setScaleX(0.5625f * 1.35f);
        cameraBridgeViewBase.setScaleY(1.0f * 1.35f);

        cameraBridgeViewBase.setMaxFrameSize(800,600);
        cameraBridgeViewBase.setCvCameraViewListener(this);
        cameraBridgeViewBase.setKeepScreenOn(true);
        cameraBridgeViewBase.getHolder().lockCanvas();

        baseLoaderCallback = new BaseLoaderCallback(context) {
            @Override
            public void onManagerConnected(int status) {
                super.onManagerConnected(status);

                switch(status){

                    case BaseLoaderCallback.SUCCESS:
                        cameraBridgeViewBase.enableView();
                        Log.d("PixelCamera","enableView");
                        break;
                    default:
                        super.onManagerConnected(status);
                        Log.d("PixelCamera","status: "+status);
                        break;
                }
            }
        };
        if (!OpenCVLoader.initDebug())
            Log.e("PixelCamera", "Unable to load OpenCV");
        else {
            Log.d("PixelCamera", "OpenCV loaded");
            cameraBridgeViewBase.enableView();
        }

        cameraOverlayDrawer = new PixelCameraOverlayDrawer();
    }

    public void setStreamFrameCallback(StreamFrameCallback streamCallback){this.streamCallback =  streamCallback; }
    public boolean isStarted(){
        return this.isStarted;
    }
    public Mat getCameraFrame()
    {
        return dst;
    }
    public Mat getStreamingFrame()
    {
        return streamDst;
    }

    public PixelCameraOverlayDrawer getCameraOverlayDrawer(){return cameraOverlayDrawer;}

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        inputFrame.rgba().copyTo(src);
        //src = readImgae("l");

        Imgproc.cvtColor(src,temp3c,Imgproc.COLOR_RGBA2RGB);

        Core.flip(temp3c, temp3c, 1);
        Core.transpose(temp3c,temp3c);

        temp3c.copyTo(dst);
        cameraOverlayDrawer.drawOn(temp3c);
        temp3c.copyTo(streamDst);
        if(streamCallback != null)
            streamCallback.onStreamFrame(streamDst,this);

        Imgproc.resize(temp3c,temp3c, new Size(src.width(),src.height()));

        return temp3c;
    }


    @Override
    public void onCameraViewStarted(int width, int height) {
        src = new Mat(height,width, CvType.CV_8UC4);
        dst = new Mat(width,height, CvType.CV_8UC4);
        streamDst = new Mat(width,height, CvType.CV_8UC3);
        temp3c = new Mat(width,height, CvType.CV_8UC3);
        isStarted = true;
        cameraStateCallback.onCameraStarted(width,height);
        Log.d("PixelCamera","onCameraViewStarted");
    }


    @Override
    public void onCameraViewStopped() {
        cameraStateCallback.onCameraStopped();
        isStarted = false;
    }

    public void destroy() {
        if (cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }
        isStarted = false;
    }

    public void pause() {
        if(cameraBridgeViewBase!=null){
            //cameraBridgeViewBase.disableView();
        }

    }

    public void resume() {

        if (!OpenCVLoader.initDebug()){
            Log.e("PixelCamera", "Unable to load OpenCV");
        }
    }


    public Mat readImgae(String filename){

        InputStream stream = null;
        Uri uri = Uri.parse("android.resource://com.bit.pixelopolis_car/drawable/"+filename);
        try {
            stream = context.getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        BitmapFactory.Options bmpFactoryOptions = new BitmapFactory.Options();
        bmpFactoryOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bmp;
        bmp = BitmapFactory.decodeStream(stream, null, bmpFactoryOptions);
        Mat imageMat = new Mat(800,600, CvType.CV_8UC4);

        Utils.bitmapToMat(bmp, imageMat);
        return imageMat;

    }

}
