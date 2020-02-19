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

package com.bit.pixelopolis_car.activities;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.bit.pixelopolis_car.data.NodeInfo;
import com.bit.pixelopolis_car.enums.ErrorStatus;
import com.bit.pixelopolis_car.services.BaseListener;
import com.bit.pixelopolis_car.services.api.ApiCommunicator;
import com.bit.pixelopolis_car.utils.Utils;
import com.bit.pixelopolis_car.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.bit.pixelopolis_car.services.CarInformation;
import com.bit.pixelopolis_car.services.config.AreaThreshold;
import com.bit.pixelopolis_car.services.config.CarArea;
import com.bit.pixelopolis_car.services.config.CommandTime;
import com.bit.pixelopolis_car.services.config.Config;
import com.bit.pixelopolis_car.services.config.FixHittingWallInfo;
import com.bit.pixelopolis_car.services.config.SpawnLocation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// Load and save configs (id, server url, ..) , request permission, then connect to server

public class SetupConfigActivity extends BaseActivity {

    private static final int PERMISSION_REQUEST_CODE = 200;
    TextView versionTextView;
    EditText stationIdView;
    EditText serverUrlView;
    CheckBox debugModeCheckBox;
    String deviceId;
    String ipAddress = "";

    BaseListener baseListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_config);
        versionTextView = findViewById(R.id.version_text_view);
        stationIdView = findViewById(R.id.station_id_textbox);
        serverUrlView = findViewById(R.id.server_url_textbox);
        debugModeCheckBox = findViewById(R.id.debug_mode_checkbox);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        ipAddress = Utils.getIPAddress(true);
        setBaseListener(this);
        versionTextView.setText("Car Application " + Config.APP_VERSION);
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("DATA", Context.MODE_PRIVATE);
        String savedStationId = sharedPreferences.getString("STATION_ID",null);
        String savedServerUrl = sharedPreferences.getString("SERVER_URL",null);
        boolean savedIsInDebugMode = sharedPreferences.getBoolean("IS_IN_DEBUG_MODE", false);

        if(savedStationId != null)
            stationIdView.setText(savedStationId);

        if(savedServerUrl != null)
            serverUrlView.setText(savedServerUrl);

        debugModeCheckBox.setChecked(savedIsInDebugMode);

        if(!checkPermission())
            requestPermission();

        updateStateView("WAIT_TO_CONNECT");
        setCarId(savedStationId);
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            return false;
        }
        return true;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void saveConfig(String stationId, String serverUrl, boolean isInDebugMode)
    {
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("DATA", Context.MODE_PRIVATE);
        sharedPreferences.edit().putString("STATION_ID",stationId).apply();
        sharedPreferences.edit().putString("SERVER_URL",serverUrl).apply();
        sharedPreferences.edit().putBoolean("IS_IN_DEBUG_MODE", isInDebugMode).apply();
    }

    public void connectToServer() {
        updateStateView("CONNECT");
        ApiCommunicator apiCommunicator = ApiCommunicator.getInstance();
        apiCommunicator.connectCar(new retrofit2.Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response != null && response.body() != null) {
                    Log.d("API_CALL", response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            waitForConnectStation();
                            updateStateView("WAIT_FOR_STATION_TO_CONNECT");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Log.d("API_CALL", t.getMessage());
                baseListener.hideSpinner();
                showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }

    public void onClickConnectToServer(View view) {
        baseListener.showSpinner();
        getConfigFromServer();

    }

    public void getConfigFromServer(){
        updateStateView("GET_CONFIG_FROM_SERVER");
        ApiCommunicator apiCommunicator = ApiCommunicator.getInstance();

        String stationId = stationIdView.getText().toString();
        String serverUrl = serverUrlView.getText().toString();
        boolean isInDebugMode = debugModeCheckBox.isChecked();
        saveConfig(stationId, serverUrl, isInDebugMode);
        Config.getInstance().setInDebugMode(isInDebugMode);
        CarInformation.getInstance().setCarId(stationId);
        CarInformation.getInstance().setDeviceId(deviceId);
        setCarId(stationId);
        apiCommunicator.initialRetrofit(serverUrl, deviceId, stationId, ipAddress);
        apiCommunicator.getConfig(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response != null && response.body() != null) {
                    Log.d("API_CALL", response.body());

                    try {
                        JSONObject returnObject = new JSONObject(response.body());

                        if(returnObject.getBoolean("success")) {
                            JSONArray configArray = returnObject.getJSONArray("config");

                            if(configArray.length() > 0){
                                JSONObject configObject = configArray.getJSONObject(0);

                                int batteryLowThreshold = 86;
                                int batteryVeryLowThreshold = 82;
                                try{
                                    JSONObject batteryThresholdObj = configObject.getJSONObject("battery_threshold");
                                    batteryLowThreshold = batteryThresholdObj.getInt("low");
                                    batteryVeryLowThreshold = batteryThresholdObj.getInt("very_low");
                                }
                                catch (JSONException e) {


                                    Toast.makeText(getBaseContext(), "Error reading 'command_time' config", Toast.LENGTH_LONG ).show();
                                }

                                CommandTime commandTime = new CommandTime();
                                try {
                                    JSONObject commandTimeObject = configObject.getJSONObject("command_time");
                                    commandTime.setTurning(commandTimeObject.getInt("turning"));
                                    commandTime.setForward(commandTimeObject.getInt("forward"));
                                }
                                catch (JSONException e) {
                                    commandTime.setTurning(3000);
                                    commandTime.setForward(1200);

                                    Toast.makeText(getBaseContext(), "Error reading 'command_time' config", Toast.LENGTH_LONG ).show();
                                }

                                SpawnLocation spawnLocation = new SpawnLocation();

                                try {
                                    JSONObject spawnLocationObject = configObject.getJSONObject("spawn_location");
                                    spawnLocation.setNodeId(spawnLocationObject.getInt("node_id"));
                                    spawnLocation.setPathId(spawnLocationObject.getInt("path_id"));
                                }
                                catch (JSONException e) {
                                    spawnLocation.setNodeId(2);
                                    spawnLocation.setPathId(1);

                                    Toast.makeText(getBaseContext(), "Error reading 'spawn_location' config", Toast.LENGTH_LONG ).show();
                                }

                                int maxTimeOut = 8;
                                try {
                                    maxTimeOut = configObject.getInt("max_time_out");
                                }
                                catch (JSONException e) {
                                    Toast.makeText(getBaseContext(), "Error reading 'max_time_out' config", Toast.LENGTH_LONG ).show();
                                }


                                AreaThreshold areaThreshold = new AreaThreshold();
                                try {
                                    JSONObject areaThresholdObject = configObject.getJSONObject("area_threshold");
                                    areaThreshold.setMin(areaThresholdObject.getDouble("min"));
                                    areaThreshold.setMax(areaThresholdObject.getDouble("max"));
                                }
                                catch (JSONException e) {
                                    areaThreshold.setMin(0.8);
                                    areaThreshold.setMax(1.0);

                                    Toast.makeText(getBaseContext(), "Error reading 'area_threshold' config", Toast.LENGTH_LONG ).show();
                                }

                                double defaultConfidenceThreshold = 0.8;
                                try {
                                    defaultConfidenceThreshold = configObject.getDouble("default_confidence_threshold");
                                }
                                catch (JSONException e) {
                                    Toast.makeText(getBaseContext(), "Error reading 'default_confidence_threshold' config", Toast.LENGTH_LONG).show();
                                }

                                CarArea carArea = new CarArea();
                                try {
                                    JSONObject carBoundObject = configObject.getJSONObject("car_bound");
                                    carArea.setTitle(carBoundObject.getString("class"));

                                    JSONObject carBoundSize = carBoundObject.getJSONObject("bound_size");
                                    carArea.setWidth(carBoundSize.getDouble("width"));
                                    carArea.setHeight(carBoundSize.getDouble("height"));

                                    carArea.setConfidence(carBoundObject.getDouble("confidence"));
                                    carArea.setMin_y(carBoundObject.getDouble("min_y"));
                                }
                                catch (JSONException e) {
                                    carArea.setTitle("Car");
                                    carArea.setWidth(0.45);
                                    carArea.setHeight(0.35);
                                    carArea.setConfidence(0.6);
                                    carArea.setMin_y(0.5);
                                    Toast.makeText(getBaseContext(), "Error reading 'car_bound' config", Toast.LENGTH_LONG ).show();
                                }

                                int maxSteeringAngle = 48;
                                try {
                                    maxSteeringAngle = configObject.getInt("max_steering_angle");
                                }
                                catch (JSONException e) {
                                    Toast.makeText(getBaseContext(), "Error reading 'max_steering_angle' config", Toast.LENGTH_LONG).show();
                                }

                                int defaultWheelSpeed = 205;
                                int slowWheelSpeed = 61;

                                try {
                                    JSONObject carWheelSpeedObj = configObject.getJSONObject("car_wheel_speed");
                                    defaultWheelSpeed = carWheelSpeedObj.getInt("default");
                                    slowWheelSpeed = carWheelSpeedObj.getInt("slow");
                                }
                                catch (JSONException e) {
                                    Toast.makeText(getBaseContext(), "Error reading 'car_wheel_speed' config", Toast.LENGTH_LONG ).show();
                                }


                                int maxFrameHistory = 25;
                                int seenCountTrigger = 5;

                                try {
                                    JSONObject lookIntoPastObj = configObject.getJSONObject("look_into_past");
                                    maxFrameHistory = lookIntoPastObj.getInt("max_frame_history");
                                    seenCountTrigger = lookIntoPastObj.getInt("seen_count_trigger");
                                }
                                catch (JSONException e) {
                                    Toast.makeText(getBaseContext(), "Error reading 'look_into_past' config", Toast.LENGTH_LONG ).show();
                                }

                                ArrayList<Pair<Integer, Integer>> attractionNodeAndPathIds = new ArrayList<Pair<Integer, Integer>>();
                                try {
                                    JSONArray attractionNodeIdsJsonArray = configObject.getJSONArray("attraction_node_ids");
                                    for(int i = 0; i < attractionNodeIdsJsonArray.length(); i++){
                                        JSONObject attractionObject = attractionNodeIdsJsonArray.getJSONObject(i);
                                        int nodeId = attractionObject.getInt("node_id");
                                        int pathId = attractionObject.getInt("path_id");
                                        Pair<Integer, Integer> pair = new Pair<Integer, Integer>(nodeId, pathId);
                                        attractionNodeAndPathIds.add(pair);
                                    }
                                }
                                catch (JSONException e) {
                                    Toast.makeText(getBaseContext(), "Error reading 'attraction_node_ids' config", Toast.LENGTH_LONG ).show();
                                }

                                String appVersion = "1.0";
                                try {
                                    JSONObject appVersionObj = configObject.getJSONObject("app_version");
                                    if(appVersionObj.has("station")){
                                        appVersion = appVersionObj.getString("car");
                                    }
                                }
                                catch (JSONException e) {
                                    Toast.makeText(getBaseContext(), "Error reading 'app_version' config", Toast.LENGTH_LONG ).show();
                                }

                                String appDownloadPath = "";
                                try {
                                    JSONObject appDownloadPathObj = configObject.getJSONObject("app_download_path");
                                    if(appDownloadPathObj.has("station")){
                                        appDownloadPath = appDownloadPathObj.getString("car");
                                    }
                                }
                                catch (JSONException e) {
                                    Toast.makeText(getBaseContext(), "Error reading 'app_download_path' config", Toast.LENGTH_LONG ).show();
                                }

                                FixHittingWallInfo fixHittingWallInfo = new FixHittingWallInfo();
                                try {
                                    JSONObject fixHittingWallObj = configObject.getJSONObject("fix_hitting_wall");
                                    if(fixHittingWallObj.has("sensor_min_threshold")){
                                        fixHittingWallInfo.setSensorMinThreshold(fixHittingWallObj.getInt("sensor_min_threshold"));
                                    }
                                    if(fixHittingWallObj.has("sensor_max_threshold")){
                                        fixHittingWallInfo.setSensorMaxThreshold(fixHittingWallObj.getInt("sensor_max_threshold"));
                                    }
                                    if(fixHittingWallObj.has("frame_counter_threshold")){
                                        fixHittingWallInfo.setFrameCounterThreshold(fixHittingWallObj.getInt("frame_counter_threshold"));
                                    }
                                    if(fixHittingWallObj.has("go_back_duration")){
                                        fixHittingWallInfo.setGoBackDuration(fixHittingWallObj.getInt("go_back_duration"));
                                    }
                                    if(fixHittingWallObj.has("turn_angle")){
                                        fixHittingWallInfo.setTurnAngle(fixHittingWallObj.getInt("turn_angle"));
                                    }
                                    if(fixHittingWallObj.has("enable")){
                                        fixHittingWallInfo.setEnable(false);
                                    }
                                }
                                catch (JSONException e) {
                                    fixHittingWallInfo.setFrameCounterThreshold(18);
                                    fixHittingWallInfo.setSensorMinThreshold(175);
                                    fixHittingWallInfo.setSensorMaxThreshold(420);
                                    fixHittingWallInfo.setGoBackDuration(2000);
                                    fixHittingWallInfo.setTurnAngle(30);
                                    fixHittingWallInfo.setEnable(false);
                                    Toast.makeText(getBaseContext(), "Error reading 'fix_hitting_wall' config", Toast.LENGTH_LONG ).show();
                                }

                                Config config = Config.getInstance();
                                config.setCommandTime(commandTime);
                                config.setSpawnLocation(spawnLocation);
                                config.setMaxTimeOut(maxTimeOut);
                                config.setAreaThreshold(areaThreshold);
                                config.setCarArea(carArea);
                                config.setMaxSteeringAngle(maxSteeringAngle);
                                config.setAttractionNodeAndPathIds(attractionNodeAndPathIds);
                                config.setMaxFrameHistory(maxFrameHistory);
                                config.setSeenCountTrigger(seenCountTrigger);
                                config.setDefaultConfidenceThreshold(defaultConfidenceThreshold);
                                config.setLatestAppVersion(appVersion);
                                config.setAppDownloadPath(appDownloadPath);
                                config.setDefaultWheelSpeed(defaultWheelSpeed);
                                config.setSlowWheelSpeed(slowWheelSpeed);
                                config.setFixHittingWallInfo(fixHittingWallInfo);
                                config.setBatteryLowThreshold(batteryLowThreshold);
                                config.setBatteryVeryLowThreshold(batteryVeryLowThreshold);
                            }
                        }


                    } catch (JSONException e) {
                        e.printStackTrace();
                    } finally {
                        getAllNodeData();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Log.d("API_CALL", t.getMessage());
                baseListener.hideSpinner();
                showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }

    protected void getAllNodeData(){
        ApiCommunicator.getInstance().getAllNodeData(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d("API_CALL", response.body());

                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            JSONArray nodeArray = returnObject.getJSONArray("node_data");

                            List<NodeInfo> nodeInfos = new ArrayList<NodeInfo>();


                            for (int i = 0; i < nodeArray.length(); i++) {
                                NodeInfo nodeInfo = new NodeInfo(nodeArray.getJSONObject(i));
                                nodeInfos.add(nodeInfo);
                            }

                            Config.getInstance().setAllNodeInfos(nodeInfos);
                        }
                    } catch (JSONException e) {
                        Toast.makeText(getBaseContext(), "Error reading 'node_data' config", Toast.LENGTH_LONG ).show();
                        e.printStackTrace();
                        baseListener.hideSpinner();
                        showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
                    } finally {
                        if(Config.APP_VERSION.equals(Config.getInstance().getLatestAppVersion())) {
                            connectToServer();
                        }else{
                             // go to update app activity
                            Intent intent = new Intent(SetupConfigActivity.this, UpdateAppActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                            baseListener.hideSpinner();
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                // do nothing
            }
        });
    }

    protected void waitForConnectStation(){
        ApiCommunicator.getInstance().waitForConnectStation(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d("API_CALL", response.body());

                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            Intent intent = new Intent(SetupConfigActivity.this, NavigateActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                            baseListener.hideSpinner();
                        } else {
                            waitForConnectStation();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                // do nothing
            }
        });
    }

    public void setBaseListener(Context context){
        try {
            baseListener = (BaseListener) context;
        }
        catch (ClassCastException e){
            e.printStackTrace();
        }
    }
}
