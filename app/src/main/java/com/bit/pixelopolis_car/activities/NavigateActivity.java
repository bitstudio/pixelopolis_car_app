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

import androidx.appcompat.app.AlertDialog;
import android.animation.Animator;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bit.pixelopolis_car.enums.ErrorStatus;
import com.bit.pixelopolis_car.services.CarController;
import com.bit.pixelopolis_car.services.camera.PixelCamera;
import com.bit.pixelopolis_car.R;
import com.bit.pixelopolis_car.services.CarInformation;
import com.bit.pixelopolis_car.services.config.Config;
import com.bit.pixelopolis_car.services.serial.SerialCommunicator;
import com.bit.pixelopolis_car.services.carvision.CarVision;

import org.opencv.android.JavaCameraView;

import com.bit.pixelopolis_car.services.streaming.CameraStreamer;
import com.airbnb.lottie.LottieAnimationView;

import java.util.ArrayList;

public class NavigateActivity extends BaseActivity implements CarController.CarControllerListener, SerialCommunicator.SerialCommunicatorListener, PixelCamera.CameraStateCallback {

    private static final String FILE_PATH_SELECT_DESTINATION = "select_destination_bg.zip";
    private static final String FILE_PATH_JOURNEY_STARTS = "journey_start_bg.zip";
    private static final String FILE_PATH_STANDBY_VIDEO = "standby_bg.zip";

    private static final String[] arriveDestinationPaths = {"car_arrival_portrait_bg.zip", "car_arrival_super_zoom_bg.zip", "car_arrival_night_sight_bg.zip", "car_arrival_google_len_bg.zip","car_arrival_dual_ev_bg.zip"};
    private static final String[] backgroundPaths = {"portrait_bg.zip", "super_zoom_bg.zip", "night_sight_bg.zip", "google_len_bg.zip","dual_ev_bg.zip"};
    private static ArrayList<Pair<Integer, Integer>> attractionPlaceIds;

    CarVision carVision;
    PixelCamera camera;
    CarController carController;
    SerialCommunicator serialCommunicator;
    TextView serialMonitorView;
    TextView readMonitorView;
    ErrorStatus currentError = ErrorStatus.NONE;
    LottieAnimationView animationView;
    LinearLayout debugLayout;

    private static String TAG = "NavigateActivity";
    JavaCameraView javaCameraView;
    CameraStreamer cameraStreamer;
    View root;
    String currentAnimationPath = "";
    int animationIndex = 0;
    Integer destinationNodeId;

    private static int DISCONNECT_BUTTON_CLICK_NUMBER = 5;
    int disconnectButtonClickCounter = 0;

    private static int REFRESH_SERIAL_BUTTON_CLICK_NUMBER = 5;
    int refreshSerialButtonClickCounter = 0;

    private static int HOLD_TIME_SECOND = 5;
    CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigate);

        javaCameraView = findViewById(R.id.java_camera_view);
        serialMonitorView = findViewById(R.id.SerialMonitor);
        readMonitorView = findViewById(R.id.readSerial);
        animationView = findViewById(R.id.animation_view);
        debugLayout = findViewById(R.id.debug_layout);

        serialCommunicator = new SerialCommunicator(this,this);
        root = javaCameraView.getRootView();
        initiateCameraRelatedObjects();
        attractionPlaceIds = Config.getInstance().getAttractionNodeAndPathIds();
        disconnectButtonClickCounter = 0;

        setCarId(CarInformation.getInstance().getCarId());

        if(!Config.getInstance().isInDebugMode()){
            debugLayout.setVisibility(View.GONE);
        }

        animationView.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if(currentAnimationPath.equals(FILE_PATH_STANDBY_VIDEO)){
                    playAnimation(FILE_PATH_SELECT_DESTINATION,true);
                }
                else if(currentAnimationPath.equals(FILE_PATH_JOURNEY_STARTS)){
                    runOnUiThread(() -> animationView.setVisibility(View.INVISIBLE));
                }
                else if(currentAnimationPath.equals(arriveDestinationPaths[animationIndex])){
                    playAnimation(backgroundPaths[animationIndex], true);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    private void initiateCameraRelatedObjects()
    {
        if(camera == null)
            camera = new PixelCamera(this.javaCameraView,this,this);
        if(carVision == null)
            carVision = new CarVision(camera, getAssets(),this);
        if(carController == null) {
            carController = new CarController(carVision, this);
            carController.setBaseListener(this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        serialCommunicator.start();
        displayStandby();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(camera != null)
            camera.resume();
        serialCommunicator.resume();
        if(carController != null)
            carController.resume();
    }

    @Override
    protected void onPause() {
        if(camera != null)
            camera.pause();
        serialCommunicator.pause();
        if(carController != null)
            carController.pause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        serialCommunicator.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if(carController != null)
            carController.destroy();
        if(camera != null)
            camera.destroy();
        serialCommunicator.destroy();
        super.onDestroy();
    }

    @Override
    public void placeSelected(){
        playAnimation(FILE_PATH_JOURNEY_STARTS, false);
    }

    @Override
    public void displayStandby() {
        carController.waitForStart();
        playAnimation(FILE_PATH_STANDBY_VIDEO, true);
    }

    @Override
    public void displayWaitJourney() {
        playAnimation(FILE_PATH_SELECT_DESTINATION, true);
    }

    @Override
    public void arriveAtDestination(Integer destinationNodeId){
        this.destinationNodeId = destinationNodeId;

        for(int i = 0; i < attractionPlaceIds.size(); i++){
            if(destinationNodeId == attractionPlaceIds.get(i).first){
                playAnimation(arriveDestinationPaths[i],false);
                Log.d(TAG, ""+destinationNodeId);
                animationIndex = i;
                break;
            }
        }
    }

    public void playAnimation(String path, boolean isLoop){
        if(path.equals("")){
            return;
        }

        if(currentAnimationPath == path)
            return;

        runOnUiThread(() ->animationView.setVisibility(View.VISIBLE));

        currentAnimationPath = path;
        runOnUiThread(() -> animationView.setAnimation(path));
        runOnUiThread(() -> animationView.loop(isLoop));
        runOnUiThread(() -> animationView.playAnimation());
    }

    public void onClickArriveAtNodeButton(View view) {
        //TODO : delete this CHEAT FUNCTION
       if(carController != null)
            carController.CHEAT_arriveAtNode();
    }

    public void onClickResetButton(View view) {
        serialCommunicator.refresh();
    }

    @Override
    public void sendToSerial(int leftSpeed,int rightSpeed){
        try {
            serialCommunicator.send(leftSpeed, rightSpeed);

        }catch (Exception e){
            Log.d("SERIAL MSG", "NO DEVICE");

            if(currentError != ErrorStatus.CANNOT_COMMUNICATE_WITH_CONTROLLER_BOARD){
                currentError = ErrorStatus.CANNOT_COMMUNICATE_WITH_CONTROLLER_BOARD;
                showError(currentError);
            }
        }
    }

    // SERIAL LISTENER //
    @Override
    public void onReceive(String text) {
        runOnUiThread(()->readMonitorView.setText(text));
        carController.receiveSerialMessage(text);
        if(currentError == ErrorStatus.CANNOT_COMMUNICATE_WITH_CONTROLLER_BOARD){
            currentError = ErrorStatus.NONE;
            showError(currentError);
        }
    }

    @Override
    public void onDisconnect() {
        carController.disconnect();
    }

    @Override
    public void onDebugTextUpdate(String text) {
        runOnUiThread(() -> serialMonitorView.setText(text));
    }

    public void onClickRefreshSerialCommButton(View view){
        refreshSerialButtonClickCounter++;

        if(refreshSerialButtonClickCounter >= REFRESH_SERIAL_BUTTON_CLICK_NUMBER) {
            refreshSerialCommunicator();
            refreshSerialButtonClickCounter = 0;
        }
    }

    public void onClickDisconnectButton(View view){
        disconnectButtonClickCounter++;

        if(disconnectButtonClickCounter >= DISCONNECT_BUTTON_CLICK_NUMBER) {
            if (carController != null) {
                disconnect();
                disconnectButtonClickCounter = 0;
            }
        }
    }

    public void refreshSerialCommunicator() {
        serialCommunicator.refresh();
        Toast.makeText(this,"Refresh Serial Communication",Toast.LENGTH_SHORT).show();
    }

    public void disconnect(){
        carController.disconnect();
        Toast.makeText(this,"Disconnect",Toast.LENGTH_SHORT).show();
    }
    // END SERIAL LISTENER //

    @Override
    public void goToSetupActivity() {
        startActivity(new Intent(NavigateActivity.this, SetupConfigActivity.class));
        finish();
    }

    @Override
    public void onCameraStarted(int width, int height) {
        StartStreaming();
    }

    @Override
    public void onCameraStopped() {
        StopStreaming();
    }

    private void StartStreaming() {
        StopStreaming();
        if(camera != null && camera.isStarted()) {
            this.cameraStreamer = new CameraStreamer(8080, 40, camera);
            this.cameraStreamer.start();
        }
    }

    private void StopStreaming() {
        if (this.cameraStreamer != null) {
            this.cameraStreamer.stop();
            this.cameraStreamer = null;
        }
    }
    public void showDialog() {
        CharSequence[] choice = { "Reset Serial", "Disconnect" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Menu")
                .setCancelable(false)
                .setItems(choice, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(choice[which].equals("Reset Serial")) {
                            refreshSerialCommunicator();
                        } else if(choice[which].equals("Disconnect")) {
                            disconnect();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int maskedAction = event.getActionMasked();

        switch (maskedAction) {
            case MotionEvent.ACTION_DOWN:
                startTimer();

                break;
            case MotionEvent.ACTION_UP:
                cancelTimer();
                break;
        }

        return true;
    }

    public void startTimer(){
        countDownTimer = new CountDownTimer(HOLD_TIME_SECOND * 1000,1000) {
            @Override
            public void onTick(long l) {

            }

            @Override
            public void onFinish() {
                showDialog();
            }
        };
        countDownTimer.start();
    }

    public void cancelTimer(){
        if(countDownTimer != null)
            countDownTimer.cancel();
    }
}