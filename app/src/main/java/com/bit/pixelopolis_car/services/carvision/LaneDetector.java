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

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.SystemClock;
import android.util.Log;
import com.bit.pixelopolis_car.services.config.Config;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class LaneDetector {
    private static final String TAG = "LaneDetector";
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    private static final int  DIM_HEIGHT = 40;
    private static final int DIM_WIDTH = 120;

    protected Interpreter tflite;
    private Interpreter.Options tfliteOptions = new Interpreter.Options();
    private GpuDelegate gpuDelegate = null;
    //protected Mat frame = new Mat(DIM_HEIGHT,DIM_WIDTH, CvType.CV_32FC3);
    float[] frameData = new float[DIM_HEIGHT * DIM_WIDTH * DIM_PIXEL_SIZE];
    protected ByteBuffer imgData = null;
    private float[][] steeringAngle = null;
    protected String modelFile = "lane_detector.tflite";
    private Mat yuvCropped = new Mat();
    private float[][] net_out = new float[1][1];

    //allocate buffer and create interface
    LaneDetector(AssetManager assetManager) throws IOException {
        //gpuDelegate = new GpuDelegate();
        //tfliteOptions.addDelegate(gpuDelegate);
        tflite = new Interpreter(loadModelFile(assetManager),tfliteOptions);
        imgData = ByteBuffer.allocateDirect(DIM_BATCH_SIZE * DIM_HEIGHT * DIM_WIDTH * DIM_PIXEL_SIZE * 4);
        imgData.order(ByteOrder.nativeOrder());
        steeringAngle = new float[1][1];
        Log.d(TAG, " Tensorflow Lite LaneDetector.");
    }

    //load model
    private MappedByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        Log.d(TAG, " Load model completed.");
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private int map(float value, float rangeInMin, float rangeInMax, float rangeOutMin, float rangeOutMax){
        return (int) ((value - rangeInMin) / (rangeInMax - rangeInMin) * (rangeOutMax - rangeOutMin) + rangeOutMin);
    }

    private void preprocessMat(Mat mat){
        /*Input mat cropped RGB 8UC3 H400xW600*/
        // resize to network input size
        Imgproc.resize(mat, mat, new org.opencv.core.Size(DIM_WIDTH,DIM_HEIGHT));
        // convert RGB to YUV
        Imgproc.cvtColor(mat,yuvCropped,Imgproc.COLOR_RGB2YUV);
        // convert 8UC1 to 32FC3
        yuvCropped.convertTo(yuvCropped,CvType.CV_32FC3);
    }

    private void convertMattoTfLiteInput() {
        imgData.rewind();
        yuvCropped.get(0,0,frameData);
        for (int i = 0; i < DIM_HEIGHT; ++i) {
            for (int j = 0; j < DIM_WIDTH; ++j) {
                int idx = (i * DIM_WIDTH + j )* DIM_PIXEL_SIZE;
                imgData.putFloat(frameData[idx ]);
                imgData.putFloat(frameData[idx + 1]);
                imgData.putFloat(frameData[idx + 2]);
            }
        }
    }

    //predict
    private float[][] runInference() {
        if(imgData != null)
            tflite.run(imgData, net_out);
        return net_out;
    }

    //classify mat
    public float classifyMat(Mat mat) {
        long startTime = SystemClock.uptimeMillis();
        preprocessMat(mat);
        convertMattoTfLiteInput();
        net_out = runInference();
        //Log.e(TAG,"net_out: "+net_out[0][0]);
        long endTime = SystemClock.uptimeMillis();
        //Log.d(TAG, endTime-startTime+"");
        int angle = Config.getInstance().getMaxSteeringAngle();
        return map(net_out[0][0],-1.0f,1.0f, -angle,angle);
    }

    //close interface
    public void close() {
        if(tflite!=null)
        {
            tflite.close();
            tflite = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
    }
}