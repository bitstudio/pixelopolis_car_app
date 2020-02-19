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

package com.bit.pixelopolis_car.services.api;

import android.util.Log;

import com.bit.pixelopolis_car.enums.ErrorStatus;
import com.bit.pixelopolis_car.enums.WarningStatus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class ApiCommunicator {
    private static final ApiCommunicator ourInstance = new ApiCommunicator();

    public static ApiCommunicator getInstance() {
        return ourInstance;
    }

    Retrofit retrofit;

    String deviceId;
    String carId;
    String serverUrl;
    String ipAddress;

    private final String appType = "car";

    private ApiCommunicator()
    {

    }

    public String getServerUrl()
    {
        return serverUrl;
    }

    public void initialRetrofit(String serverUrl, String deviceId, String carId, String ipAddress)
    {
        this.deviceId = deviceId;
        this.carId = carId;
        this.serverUrl = serverUrl;
        this.ipAddress = ipAddress;

        retrofit = new Retrofit.Builder()
                .baseUrl(serverUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public void connectCar(final Callback<String> callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);
        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            paramObject.put("ip_address", ipAddress);
            Call<String> call = service.connectCar(paramObject.toString());

            Log.d("API_CALL", "connectCar : " + paramObject.toString());
            call.enqueue(callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public void waitForConnectStation(final Callback<String> callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);
        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            Call<String> call = service.waitForConnectStation(paramObject.toString());

            Log.d("API_CALL", "waitForConnectStation : " + paramObject.toString());
            //APIHelper.enqueueWithRetry(call, callback);
            call.enqueue(callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getAllNodeData(final Callback<String> callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);
        Call<String> call = service.getAllNodeData();
        Log.d("API_CALL", "getAllNodeData" );
        call.enqueue(callback);
    }

    public void waitForStart(final Callback<String> callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);
        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            Call<String> call = service.waitForStart(paramObject.toString());

            Log.d("API_CALL", "waitForStart : " + paramObject.toString());
            //APIHelper.enqueueWithRetry(call, callback);
            call.enqueue(callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void alive(String appStatus, int batteryPercentage, WarningStatus warning, ErrorStatus error, Callback<String> callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);
        JSONObject deviceInfoObject = new JSONObject();
        JSONObject paramObject = new JSONObject();
        try {
            deviceInfoObject.put("device_id", deviceId);
            deviceInfoObject.put("app_type", appType);
            deviceInfoObject.put("battery_percentage", batteryPercentage);

            paramObject.put("device_info", deviceInfoObject);
            paramObject.put("car_id", carId);
            paramObject.put("app_status", appStatus);
            paramObject.put("warning", warning.toString());
            paramObject.put("error", error.toString());

            Call<String> call = service.alive(paramObject.toString());

            Log.e("API_CALL", "alive : " + paramObject.toString());
            call.enqueue(callback);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void requestRouteToRandomDestination(int currentCarLocation, List<Integer> obstacleNodeIds, Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            paramObject.put("current_car_location", currentCarLocation);

            JSONArray obstacleArray = new JSONArray();

            if(obstacleNodeIds != null && obstacleNodeIds.size() != 0){
                for(int i = 0; i < obstacleNodeIds.size(); i++){
                    obstacleArray.put(obstacleNodeIds.get(i));
                }
            }

            paramObject.put("obstacle_node_id", obstacleArray);

            Call<String> call = service.requestRouteToRandomDestination(paramObject.toString());

            Log.e("API_CALL", "requestRouteToRandomDestination : " + paramObject.toString());
            //APIHelper.enqueueWithRetry(call, callback);
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void requestRouteToDestination(int currentCarLocation, int destinationNodeId, int destinationPathId, List<Integer> obstacleNodeIds, Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            paramObject.put("current_car_location", currentCarLocation);
            paramObject.put("destination_node_id", destinationNodeId);
            paramObject.put("destination_path_id", destinationPathId);

            JSONArray obstacleArray = new JSONArray();

            if(obstacleNodeIds != null && obstacleNodeIds.size() != 0){
                for(int i = 0; i < obstacleNodeIds.size(); i++){
                    obstacleArray.put(obstacleNodeIds.get(i));
                }
            }

            paramObject.put("obstacle_node_id", obstacleArray);

            Call<String> call = service.requestRouteToDestination(paramObject.toString());

            Log.e("API_CALL", "requestRouteToDestination : " + paramObject.toString());
            call.enqueue(callback);



        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void waitForPlaceSelection(Callback callback) {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);

            Call<String> call = service.waitForPlaceSelection(paramObject.toString());

            Log.d("API_CALL", "waitForPlaceSelection : " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void waitForCancelPlace(Callback callback) {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);

            Call<String> call = service.waitForCancelPlace(paramObject.toString());

            Log.d("API_CALL", "waitForPlaceSelection : " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void arriveAtNode(int nodeId, Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            paramObject.put("node_id", nodeId);

            Call<String> call = service.arriveAtNode(paramObject.toString());

            Log.d("API_CALL", "arriveAtNode: " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void arriveWrongNode(int nodeId, String objectClass, Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            paramObject.put("node_id", nodeId);
            paramObject.put("object_class", objectClass);

            Call<String> call = service.arriveWrongNode(paramObject.toString());

            Log.e("API_CALL", "arriveWrongNode: " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void waitForTraffic(int nodeId, Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            paramObject.put("node_id", nodeId);

            Call<String> call = service.waitForTraffic(paramObject.toString());

            Log.d("API_CALL", "waitForTraffic: " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void finishAutoTurnCommand(int nodeId, Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            paramObject.put("node_id", nodeId);

            Call<String> call = service.finishAutoTurnCommand(paramObject.toString());

            Log.d("API_CALL", "finishAutoTurnCommand: " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void arriveAtDestination(int destinationNodeId, Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);
            paramObject.put("destination_node_id", destinationNodeId);

            Call<String> call = service.arriveAtDestination(paramObject.toString());

            Log.d("API_CALL", "arriveAtDestination : " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void waitForStationDisconnect(Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);

            Call<String> call = service.waitForStationDisconnect(paramObject.toString());

            Log.d("API_CALL", "waitForStationDisconnect : " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void disconnectCar(Callback callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);

            Call<String> call = service.disconnectCar(paramObject.toString());

            Log.d("API_CALL", "disconnectCar : " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getConfig(Callback<String> callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);

        Call<String> call = service.getConfig();
        call.enqueue(callback);
    }

    public void videoStatus(Callback<String> callback)
    {
        CallWebService service = retrofit.create(CallWebService.class);
        JSONObject paramObject = new JSONObject();
        try {
            paramObject.put("app_type", appType);
            paramObject.put("device_id", deviceId);
            paramObject.put("car_id", carId);

            Call<String> call = service.videoStatus(paramObject.toString());

            Log.d("API_CALL", "videoStatus : " + paramObject.toString());
            call.enqueue(callback);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
