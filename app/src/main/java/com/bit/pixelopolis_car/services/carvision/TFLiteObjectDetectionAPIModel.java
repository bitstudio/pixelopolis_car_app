/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.bit.pixelopolis_car.services.carvision;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Trace;

import com.bit.pixelopolis_car.data.NodeInfo;
import com.bit.pixelopolis_car.services.config.Config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection
 */
public class TFLiteObjectDetectionAPIModel implements ObjectDetector {


    // Only return this many results.
    private static final int NUM_DETECTIONS = 10;
    // Float model
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;
    // Number of threads in the java app
    private static final int NUM_THREADS = 4;
    private boolean isModelQuantized;
    // Config values.
    private int inputSizeH;
    private int inputSizeW;
    // Pre-allocated buffers.
    private Vector<String> labels = new Vector<String>();
    private JSONObject labelsPiority;

    List<NodeInfo> allNodeInfos;
    List<String> enableNodes = new ArrayList<String>();

    private int[] intValues;
    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;

    private Interpreter tfLite;

    private ByteBuffer imgData = null;

    private NnApiDelegate nnApiDelegate = null;

    private byte[] matData;

    private Mat matTemp;
    private static final String TAG = "TFLite";

    private TFLiteObjectDetectionAPIModel() {}

    /** Memory-map the model file in Assets. */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     * @param inputSizeH The size of image input
     * @param inputSizeW The size of image input
     * @param isQuantized Boolean representing model is quantized or not
     */

    public static String loadJSONFromAsset(InputStream labelsInput) {
        String json = null;
        try {

            int size = labelsInput.available();

            byte[] buffer = new byte[size];

            labelsInput.read(buffer);

            //labelsInput.close();

            json = new String(buffer, "UTF-8");


        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;

    }

    public static ObjectDetector create(
            final AssetManager assetManager,
            final String modelFilename,
            final String labelFilename,
            final int inputSizeH,
            final int inputSizeW,

            final boolean isQuantized)
            throws IOException {
        final TFLiteObjectDetectionAPIModel d = new TFLiteObjectDetectionAPIModel();

        InputStream labelsInput = null;
        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        labelsInput = assetManager.open(actualFilename);


        try {
            d.labelsPiority = new JSONObject(loadJSONFromAsset(labelsInput));
        } catch (Exception e) {
            e.printStackTrace();
        }

        d.inputSizeH = inputSizeH;
        d.inputSizeW = inputSizeW;

        try {
            Interpreter.Options tfliteOptions = new Interpreter.Options();
            //GpuDelegate gpuDelegate = new GpuDelegate();
            d.nnApiDelegate = new NnApiDelegate();
            tfliteOptions.addDelegate(d.nnApiDelegate);


            d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename),tfliteOptions);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        d.isModelQuantized = isQuantized;
        // Pre-allocate buffers.
        int numBytesPerChannel;
        if (isQuantized) {
            numBytesPerChannel = 1; // Quantized
        } else {
            numBytesPerChannel = 4; // Floating point
        }
        d.imgData = ByteBuffer.allocateDirect(1 * d.inputSizeH * d.inputSizeW * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        d.matData = new byte[d.inputSizeH * d.inputSizeW * 3];
        d.matTemp = new Mat(d.inputSizeH,d.inputSizeW,org.opencv.core.CvType.CV_8UC3);

        d.tfLite.setNumThreads(NUM_THREADS);
        d.outputLocations = new float[1][NUM_DETECTIONS][4];
        d.outputClasses = new float[1][NUM_DETECTIONS];
        d.outputScores = new float[1][NUM_DETECTIONS];
        d.numDetections = new float[1];

        Config config = Config.getInstance();
        d.allNodeInfos = config.getAllNodeInfos();

        for (int i=0;i < d.allNodeInfos.size();i++){
            for (int j=0;j<d.allNodeInfos.get(i).getObjectList().size();j++) {
                d.enableNodes.add(d.allNodeInfos.get(i).getObjectList().get(j).getTitle());
            }

        }
        d.enableNodes.add("Car");

        return d;
    }

    private void preprocessMatAndUpdateToImgData(Mat mat){
        // resize to network input size
        Imgproc.resize(mat, matTemp, new Size(inputSizeW,inputSizeH));
        matTemp.get(0,0,matData);
        imgData.rewind();
        if(isModelQuantized){
            imgData.put(matData);
        }
        else {
            for (int i = 0; i < inputSizeH; ++i) {
                for (int j = 0; j < inputSizeW; ++j) {
                    int idx = (i * inputSizeW + j)*3;
                    imgData.putFloat(( (matData[idx] & 0xFF)   - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(( (matData[idx+1] & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(( (matData[idx+2] & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }
    }

    @Override
    public List<DetectedObject> recognizeImage(final Mat mat) {
        if( mat == null ) return new ArrayList<>();
        preprocessMatAndUpdateToImgData(mat);

        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");
        Trace.beginSection("preprocessBitmap");
        // Preprocess the image matData from 0-255 int to normalized float based
        // on the provided parameters.
        Trace.endSection(); // preprocessBitmap

        // Copy the input matData into TensorFlow.
        Trace.beginSection("feed");
        outputLocations = new float[1][NUM_DETECTIONS][4];
        outputClasses = new float[1][NUM_DETECTIONS];
        outputScores = new float[1][NUM_DETECTIONS];
        numDetections = new float[1];

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);
        Trace.endSection();

        // Run the inference call.
        long startTime = SystemClock.uptimeMillis();
        Trace.beginSection("run");
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        Trace.endSection();
        long endTime = SystemClock.uptimeMillis();
        //Log.d(TAG, "Timecost " + Long.toString(endTime - startTime));

        // Show the best detections.
        // after scaling them back to the input size.
        final ArrayList<DetectedObject> detectedObjects = new ArrayList<>(NUM_DETECTIONS);
        for (int i = 0; i < NUM_DETECTIONS; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocations[0][i][1],
                            outputLocations[0][i][0],
                            outputLocations[0][i][3],
                            outputLocations[0][i][2]);
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            int labelOffset = 0;
            outputClasses[0][i] = outputClasses[0][i] < 0 ? 0 : outputClasses[0][i];
            try {
                String className = labelsPiority.getJSONObject(String.valueOf((int)outputClasses[0][i])).getString("name");
                if (enableNodes.contains(className)){
                    detectedObjects.add(
                            new DetectedObject(
                                    String.valueOf((int) outputClasses[0][i]),
                                    className,
                                    outputScores[0][i],
                                    detection));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Trace.endSection(); // "recognizeImage"



        // sort array by pre-defined object piority.
        Collections.sort(detectedObjects, new Comparator<DetectedObject>() {
            @Override
            public int compare(DetectedObject one, DetectedObject two) {
                try {

                    if (labelsPiority.getJSONObject(one.getId()).getInt("piority") < labelsPiority.getJSONObject(two.getId()).getInt("piority")){
                        return -1;
                    }else{
                        return 1;
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return 0;
            }});

        return detectedObjects;
    }

    @Override
    public void enableStatLogging(final boolean logStats) {}

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {
        tfLite.close();
        nnApiDelegate.close();

    }

    public void setNumThreads(int num_threads) {
        if (tfLite != null) tfLite.setNumThreads(num_threads);
    }

    @Override
    public void setUseNNAPI(boolean isChecked) {
        if (tfLite != null) tfLite.setUseNNAPI(isChecked);
    }
}

