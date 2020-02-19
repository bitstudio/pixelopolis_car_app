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

package com.bit.pixelopolis_car.data;

import android.graphics.RectF;
import com.bit.pixelopolis_car.services.carvision.ObjectDetector;
import com.bit.pixelopolis_car.services.config.Config;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NodeInfo {
    String nodeId;
    List<ObjectDetector.DetectedObject> objects;

    public NodeInfo()
    {
        objects = new ArrayList();
    }

    public NodeInfo(JSONObject jsonObject) {
        objects = new ArrayList();
        try{
            //setId(jsonObject.getString("node_id"));
            setNodeId(Integer.toString(jsonObject.getInt("node_id")));
            JSONArray objArray = jsonObject.getJSONArray("objects");
            if(objArray != null){
                for(int i = 0; i < objArray.length(); i++){
                    JSONObject obji = objArray.getJSONObject(i);
                    String title = obji.getString("class");

                    float confidence = (float) Config.getInstance().getDefaultConfidenceThreshold();
                    if(obji.has("confidence")) {
                        confidence = (float) obji.getDouble("confidence");
                    }
                    JSONObject boundobj = obji.getJSONObject("bound_size");
                    float width = (float)boundobj.getDouble("width");
                    float height = (float)boundobj.getDouble("height");
                    RectF bound = new RectF(0,0, width, height);
                    ObjectDetector.DetectedObject detectedObject = new ObjectDetector.DetectedObject("", title, confidence, bound);
                    addObject(detectedObject);
                }
            }
        }catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setNodeId(String id){
        nodeId = id;
    }

    public String getNodeId() {return  nodeId;}

    public void addObject(ObjectDetector.DetectedObject obj)
    {
        objects.add(obj);
    }

    public List<ObjectDetector.DetectedObject> getObjectList()
    {
        return this.objects;
    }

}
