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

import com.bit.pixelopolis_car.services.carvision.ObjectDetector;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

public class PixelCameraOverlayDrawer {
    private List<ObjectDetector.DetectedObject> detectedObjects;

    public void setDetectedObjects(List<ObjectDetector.DetectedObject> detectedObjects) {
        this.detectedObjects = detectedObjects;
    }


    public void drawOn(Mat inOutMat){
        if(detectedObjects !=null ) {
            float scaleX = (float)inOutMat.size().width;
            float scaleY = (float)inOutMat.size().height;
            for(ObjectDetector.DetectedObject detectedObject : detectedObjects){
                Point topLeft = new Point(detectedObject.getLocation().left * scaleX, detectedObject.getLocation().top * scaleY);
                Point bottomRight = new Point(detectedObject.getLocation().right * scaleX, detectedObject.getLocation().bottom * scaleY);
                Point bottomLeft = new Point(detectedObject.getLocation().left * scaleX, detectedObject.getLocation().bottom * scaleY);
                Imgproc.rectangle(inOutMat, topLeft, bottomRight, new Scalar(0, 0, 255),5);
                String topText =  String.format("%s: %.3f",detectedObject.getTitle() ,detectedObject.getConfidence(),detectedObject.getLocation().width() * detectedObject.getLocation().height());
                Size topTextSize = Imgproc.getTextSize(topText, 1, 2, 2,null);
                Imgproc.rectangle(inOutMat,new Point(topLeft.x - 3 , topLeft.y - topTextSize.height - 5) , new Point(topLeft.x + topTextSize.width + 3 , topLeft.y + 3) , new Scalar(255, 255,255),-1);
                Imgproc.putText(inOutMat,topText ,topLeft , 1, 2, new Scalar(0, 0, 0), 2,8,false);

                String bottomText =  String.format("w=%.3f,h=%.3f,a=%.3f",detectedObject.getLocation().width() , detectedObject.getLocation().height(),detectedObject.getLocation().width() * detectedObject.getLocation().height());
                Size bottomTextSize = Imgproc.getTextSize(bottomText, 1, 2, 2,null);
                Imgproc.rectangle(inOutMat,new Point(bottomLeft.x - 3 , bottomLeft.y - bottomTextSize.height - 5) , new Point(bottomLeft.x + bottomTextSize.width + 3 , bottomLeft.y + 3) , new Scalar(255, 255,255),-1);
                Imgproc.putText(inOutMat,bottomText ,bottomLeft , 1, 2, new Scalar(0, 0, 0), 2,8,false);

            }
        }
    }
}
