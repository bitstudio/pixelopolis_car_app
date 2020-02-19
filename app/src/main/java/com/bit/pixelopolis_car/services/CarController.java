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

package com.bit.pixelopolis_car.services;

import android.content.Context;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.util.Log;

import com.bit.pixelopolis_car.services.api.ApiCommunicator;
import com.bit.pixelopolis_car.R;
import com.bit.pixelopolis_car.data.BatteryInformation;
import com.bit.pixelopolis_car.data.NavigationCommand;
import com.bit.pixelopolis_car.data.NodeInfo;
import com.bit.pixelopolis_car.enums.AppStatus;
import com.bit.pixelopolis_car.enums.CMD;
import com.bit.pixelopolis_car.enums.ErrorStatus;
import com.bit.pixelopolis_car.enums.WarningStatus;
import com.bit.pixelopolis_car.services.carvision.CarVision;
import com.bit.pixelopolis_car.services.carvision.ObjectDetector;
import com.bit.pixelopolis_car.services.config.AreaThreshold;
import com.bit.pixelopolis_car.services.config.CarArea;
import com.bit.pixelopolis_car.services.config.Config;
import com.bit.pixelopolis_car.services.config.FixHittingWallInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.util.Log.d;

public class CarController implements WheelController.WheelControllerListener {

    private static int UPDATE_INTERVAL = 10;
    private static int SEND_ALIVE_INTERVAL = 1000;
    private static final String TAG = "CarController";

    Thread carControllerThread;

    boolean isFinished = false;
    boolean isPause = false;
    boolean isWaitingForPlaceSelection = true;
    boolean isWaitingForManualTurnThenCancelNavigationCommand = false;
    boolean isArriveWrongNode = false;

    private Context activityContext = null;

    AppStatus appStatus;
    ErrorStatus errorStatus = ErrorStatus.NONE;
    WarningStatus warningStatus = WarningStatus.NONE;

    ReadWriteLock lock = new ReentrantReadWriteLock();

    WheelController wheelController;

    List<NodeInfo> allNodeInfos;
    public NodeInfo currentDestinationNodeInfo;
    int currentDestinationPathId;
    NavigationCommand currentNavigationCommand;
    List<NavigationCommand> routeToDestination;
    CarVision carVision;
    BatteryInformation batteryInformation;
    ObjectDetector.DetectedObject otherCarMarker;
    ObjectDetector.DetectedObject prevNodeObject = null;
    AppStatus stateBeforeHittingWall;
    boolean shouldPlayDebugSound = false;

    long previousFrameTimeStamp = 0;
    long scriptTurnElapsedTime = 0;
    long arriveAtDestinationTimeStamp = 0;
    int sendAliveTimer = 0;
    int motorBatteryPercentage = 999;
    int irSensorValue = 300;
    long hittingWallScriptTurnElapsedTime = 0;
    long hittingWallPreviousFrameTimeStamp = 0;
    long hittingWallGoBackwardPreviousFrameTimeStamp = 0;
    long hittingWallGoBackwardElapsedTime = 0;
    FixHittingWallInfo fixHittingWallInfo;
    int hittingWallCounter;
    AreaThreshold areaThreshold;
    CarArea carArea;
    BaseListener baseListener;

    public List<List<ObjectDetector.DetectedObject>> objectsHistory = new ArrayList<>();

    public interface CarControllerListener{
        void displayStandby();
        void displayWaitJourney();
        void goToSetupActivity();
        void sendToSerial(int leftSpeed, int rightSpeed);

        void placeSelected();
        void arriveAtDestination(Integer destinationNodeId);

    }

    CarControllerListener listener = null;

    public CarController(CarVision carVision, Context activity_context)
    {
        // initiate everything here
        activityContext = activity_context;
        setAppStatus(AppStatus.IDLE);
        carControllerThread = new Thread(new CarController.CarControllerThread());


        this.carVision = carVision;
        batteryInformation = new BatteryInformation(activity_context);

        try {
            listener = (CarControllerListener) activity_context;
        }
        catch (ClassCastException e){
            e.printStackTrace();
        }

        initiateWheelController(carVision);

        Config config = Config.getInstance();

        shouldPlayDebugSound = config.isInDebugMode();

        areaThreshold = config.getAreaThreshold();
        allNodeInfos = config.getAllNodeInfos();

        fixHittingWallInfo = config.getFixHittingWallInfo();

        currentNavigationCommand = new NavigationCommand();
        currentNavigationCommand.command = CMD.DO_NOTHING;
        int spawnNodeId = config.getSpawnLocation().getNodeId();
        for(int i = 0; i < allNodeInfos.size(); i++){
            NodeInfo nif = allNodeInfos.get(i);
            if(Integer.parseInt(nif.getNodeId()) == spawnNodeId){
                currentNavigationCommand.nodeInfo = nif;
                break;
            }
        }

        carArea = config.getCarArea();
        String title = carArea.getTitle();
        float width = (float)carArea.getWidth();
        float height = (float)carArea.getHeight();
        RectF bound = new RectF(0.0f, 0.0f, width, height);
        float confidence = (float) carArea.getConfidence();
        otherCarMarker = new ObjectDetector.DetectedObject("1", title, confidence, bound);

        carControllerThread.start();
    }

    public void initiateWheelController(CarVision carVision)
    {
        if(wheelController == null){
            wheelController = new WheelController(carVision, this);
            wheelController.park();
        }
    }

    public class CarControllerThread implements Runnable{
        public void run(){
            while (!isFinished) {
                if(!isPause) {
                    try {
                        // send alive signal to server
                        if(sendAliveTimer >= SEND_ALIVE_INTERVAL) {
                            sendAlive();
                        }

                        if(listener != null) {
                            printDebugMessage();
                            checkWarnings();
                        }

                        if(isWaitingForPlaceSelection) {
                            waitForPlaceSelection();
                        }
                        else{
                            waitForCancelPlace();
                        }

                        // try fixing crashing wall situation
                        if(fixHittingWallInfo.isEnable()) {
                            if (!fixHittingWallInfo.isSensorValueInRange(irSensorValue)) {
                                setCarStatus(AppStatus.HITTING_WALL);
                            }
                        }

                        update();

                        AppStatus s = getAppStatus();
                        if( s != null && s != AppStatus.PREPARE_TO_DISCONNECT && s!= AppStatus.DISCONNECT)
                            waitForStationDisconnect();

                        sendAliveTimer += UPDATE_INTERVAL;
                        Thread.sleep(UPDATE_INTERVAL);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            disconnect();
        }

        private void printDebugMessage(){
            // PRINT DEBUG
            String stateString = "";
            stateString = isWaitingForPlaceSelection ? "IDLE MODE - " : "GO TO DESTINATION MODE - ";
            stateString += getAppStatus().toString();

            //baseListener.updateStateView(stateString + "\nmotor % : " + Integer.toString(motorBatteryPercentage));

            if(currentNavigationCommand != null && getAppStatus() == AppStatus.WORKING_ON_AUTO_TURN_COMMAND)
                stateString += "\n" + currentNavigationCommand.command.toString();

            if(currentNavigationCommand != null){
                stateString += "\n" + "GOING TO NODE = " + currentNavigationCommand.nodeInfo.getNodeId();
            }

            if(prevNodeObject != null){
                stateString += "   (prev node = " + prevNodeObject.getTitle() + ")";
            }

            if(currentDestinationNodeInfo != null){
                stateString += "\n" + "DESTINATION NODE ID = " + currentDestinationNodeInfo.getNodeId();
            }

            baseListener.updateStateView(stateString + "\nmotor % : " + Integer.toString(motorBatteryPercentage) + " /// ir sensor value : " + irSensorValue);
            //END PRINT DEBUG
        }

        private void checkWarnings() {
            int carBattery = batteryInformation.getBatteryPercentage();

            WarningStatus newWarningStatus;

            if (motorBatteryPercentage <= Config.getInstance().getBatteryVeryLowThreshold()){
                newWarningStatus = WarningStatus.MOTOR_VERY_LOW_BATTERY;
            }
            else if(motorBatteryPercentage <= Config.getInstance().getBatteryLowThreshold()){
                newWarningStatus = WarningStatus.MOTOR_LOW_BATTERY;
            }
            else if(carBattery <= 10){
                newWarningStatus = WarningStatus.CAR_PHONE_VERY_LOW_BATTERY;
            }
            else if(carBattery <= 20){
                newWarningStatus = WarningStatus.CAR_PHONE_LOW_BATTERY;
            }
            else if(appStatus == AppStatus.LOST){
                newWarningStatus = WarningStatus.LOST;
            }
            else{
                newWarningStatus = WarningStatus.NONE;
            }

            if (warningStatus != newWarningStatus) {
                warningStatus = newWarningStatus;
                baseListener.showWarning(warningStatus);
            }
        }

        private void update() {
            if(wheelController == null)
                return;

            switch (appStatus){
                case WAIT_TO_CONNECT:
                    break;
//                case CONNECT:
//                    break;

//                case WAIT_FOR_PLACE_SELECTION:
//                    waitForPlaceSelection();
//                    break;
                case IDLE:
                    if(isWaitingForPlaceSelection)
                        setCarStatus(AppStatus.REQUEST_RANDOM_ROUTE);
                    break;
                case REQUEST_RANDOM_ROUTE:

                    break;
                case REQUEST_ROUTE_TO_DESTINATION:
                    break;
                case RECEIVED_ROUTE_TO_DESTINATION:
                    setCarStatus(AppStatus.GET_NEXT_NAVIGATION_COMMAND);
                    break;
                case GET_NEXT_NAVIGATION_COMMAND:
                    if(routeToDestination.size() != 0) {
                        currentNavigationCommand = routeToDestination.get(0);
                        routeToDestination.remove(0);
                        setCarStatus(AppStatus.ON_ROUTE_TO_NODE);
                    }
                    else{
                        setCarStatus(AppStatus.ARRIVE_AT_DESTINATION);
                        arriveAtDestinationTimeStamp = Calendar.getInstance().getTimeInMillis();
                    }
                    break;
                case ON_ROUTE_TO_NODE:
                    if(isArriveWrongNode) {
                        setCarStatus(AppStatus.ARRIVE_AT_NODE);
                        isArriveWrongNode = false;
                    }

                    if(detectOtherCar()) {
                        wheelController.park();
                        if(shouldPlayDebugSound) {
                            MediaPlayer mp = MediaPlayer.create(activityContext, R.raw.stop);
                            mp.start();
                        }
                    }
                    else if(objectDetect(currentNavigationCommand.nodeInfo)){
                        //wheelController.park();
                        wheelController.driveInLaneKeepingMode();
                    }else{
                        // don't find anything
                        wheelController.driveInLaneKeepingMode();
                    }
                    break;
                case LOST:
                    break;
                case ARRIVE_AT_NODE:
                    arriveAtNode(Integer.parseInt(currentNavigationCommand.nodeInfo.getNodeId()));
                    break;
                case WAIT_FOR_TRAFFIC:
                    //wheelController.park();
                    waitForTraffic(Integer.parseInt(currentNavigationCommand.nodeInfo.getNodeId()));
                    break;
                case START_AUTO_TURN_COMMAND:
                    wheelController.driveInScriptMode(currentNavigationCommand.command);
                    previousFrameTimeStamp = Calendar.getInstance().getTimeInMillis();
                    scriptTurnElapsedTime = 0;
                    setCarStatus(AppStatus.WORKING_ON_AUTO_TURN_COMMAND);
                    break;
                case WORKING_ON_AUTO_TURN_COMMAND:
                    // prevent crash
                    if(detectOtherCar()) {
                        wheelController.park();
                    }
                    else{
                        // check if script is done
                        long currentTimeStamp = Calendar.getInstance().getTimeInMillis();
                        scriptTurnElapsedTime += (currentTimeStamp - previousFrameTimeStamp);
                        previousFrameTimeStamp = currentTimeStamp;
                        if( scriptTurnElapsedTime >= wheelController.getScriptDuration(currentNavigationCommand.command)){
                            wheelController.driveInLaneKeepingMode();
                            setCarStatus(AppStatus.FINISH_AUTO_TURN_COMMAND);
                        }
                    }
                    break;
                case FINISH_AUTO_TURN_COMMAND:
                    wheelController.driveInLaneKeepingMode();
                    finishAutoTurnCommand(Integer.parseInt(currentNavigationCommand.nodeInfo.getNodeId()));
                    break;
                case NAVIGATION_COMMAND_CANCELLED_DUE_TO_PLACE_SELECTION:
                    wheelController.park();
                    if(routeToDestination != null) {
                        routeToDestination.clear();
                    }
                    setCarStatus(AppStatus.REQUEST_ROUTE_TO_DESTINATION);
                    isWaitingForManualTurnThenCancelNavigationCommand = false;
                    break;
                case ARRIVE_AT_DESTINATION:
                    //wheelController.park();
                    arriveAtDestination(Integer.parseInt(currentDestinationNodeInfo.getNodeId()));
                    prevNodeObject = null;
                    if(!isWaitingForPlaceSelection) {
                        setCarStatus(AppStatus.STAY_AT_DESTINATION);
                        wheelController.park();
                        listener.arriveAtDestination(Integer.parseInt(currentDestinationNodeInfo.getNodeId()));
                    }
                    else{
                        wheelController.driveInLaneKeepingMode();
                        setCarStatus(AppStatus.IDLE);
                    }
                    currentDestinationNodeInfo = null;
                    routeToDestination.clear();
                    break;
                case STAY_AT_DESTINATION:
                      long currentTimeStamp = Calendar.getInstance().getTimeInMillis();
//                    if( currentTimeStamp - arriveAtDestinationTimeStamp > STAY_AT_DESTINATION_DURATION) {
//                        setCarStatus(AppStatus.IDLE);
//                        isWaitingForPlaceSelection = true;
//                    }
                    checkStationVideoStatus();
                    break;
                case CANCEL_PLACE:
                    wheelController.park();
                    if(routeToDestination != null) {
                        routeToDestination.clear();
                    }
                    isWaitingForPlaceSelection = true;
                    isWaitingForManualTurnThenCancelNavigationCommand = false;
                    setCarStatus(AppStatus.IDLE);
                    break;
                case HITTING_WALL:
                    hittingWallCounter =hittingWallCounter+1;
                    if(fixHittingWallInfo.isSensorValueInRange(irSensorValue)){
                        setAppStatus(stateBeforeHittingWall);
                    }
                    else if(hittingWallCounter > fixHittingWallInfo.getFrameCounterThreshold()) {
                        setCarStatus(AppStatus.FIX_HITTING_WALL_GO_BACKWARD);
                        wheelController.driveInScriptMode(CMD.GO_BACKWARD);
                        if(shouldPlayDebugSound) {
                            MediaPlayer mp = MediaPlayer.create(activityContext, R.raw.stop);
                            mp.start();
                        }

                        hittingWallGoBackwardPreviousFrameTimeStamp = Calendar.getInstance().getTimeInMillis();
                        hittingWallGoBackwardElapsedTime = 0;
                    }
                    break;
                case FIX_HITTING_WALL_GO_BACKWARD:
                    long currentHittingWallGoBackwardTimestamp = Calendar.getInstance().getTimeInMillis();
                    hittingWallGoBackwardElapsedTime += currentHittingWallGoBackwardTimestamp - hittingWallGoBackwardPreviousFrameTimeStamp;
                    hittingWallGoBackwardPreviousFrameTimeStamp = currentHittingWallGoBackwardTimestamp;

                    if(hittingWallGoBackwardElapsedTime >= fixHittingWallInfo.getGoBackDuration()){
                        setCarStatus(AppStatus.FIX_HITTING_WALL_SCRIPT_TURN_LEFT);
                        wheelController.driveInScriptMode(CMD.FIX_HITTING_WALL_TURN_LEFT);

                        hittingWallPreviousFrameTimeStamp = Calendar.getInstance().getTimeInMillis();
                        hittingWallScriptTurnElapsedTime = 0;
                        setCarStatus(AppStatus.FIX_HITTING_WALL_SCRIPT_TURN_LEFT);
                    }
                    break;
                case FIX_HITTING_WALL_SCRIPT_TURN_LEFT:
                    if(detectOtherCar()) {
                        wheelController.park();
                    }
                    else {
                        long currentFixHittingWallTimeStamp = Calendar.getInstance().getTimeInMillis();
                        hittingWallScriptTurnElapsedTime += (currentFixHittingWallTimeStamp - hittingWallPreviousFrameTimeStamp);
                        hittingWallPreviousFrameTimeStamp = currentFixHittingWallTimeStamp;

                        if (hittingWallScriptTurnElapsedTime >= wheelController.getScriptDuration(CMD.FIX_HITTING_WALL_TURN_LEFT)) {
                            setCarStatus(AppStatus.FINISH_FIX_HITTING_WALL);
                        }
                    }
                    break;
                case FINISH_FIX_HITTING_WALL:
                    setAppStatus(stateBeforeHittingWall);
                    break;
                case PREPARE_TO_DISCONNECT:
                    wheelController.park();
                    setCarStatus(AppStatus.DISCONNECT);
                    break;
                case DISCONNECT:
                    listener.goToSetupActivity();
                    setCarStatus(AppStatus.WAIT_TO_CONNECT);
                    destroy();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void sendSerialMessage(int leftSpeed, int rightSpeed) {
        listener.sendToSerial(leftSpeed, rightSpeed);
    }

    public void receiveSerialMessage(String message){
        checkBatteryLevel(message);
        checkDistanceFromIRSensor(message);
    }

    private void checkBatteryLevel(String message){
        //check battery level (read from serial)
        String regEx = "^b[0-9]*$";
        Pattern pattern = Pattern.compile(regEx);
        Matcher m = pattern.matcher(message);
        if(m.matches()) {
            int rawBatteryValue = Integer.parseInt(message.substring(1));
            motorBatteryPercentage = rawBatteryValue;
        }
    }

    private void checkDistanceFromIRSensor(String message){
        String regEx = "^r[0-9]*$";
        Pattern pattern = Pattern.compile(regEx);
        Matcher m = pattern.matcher(message);
        if(m.matches()) {
            irSensorValue = Integer.parseInt(message.substring(1));
        }
    }

    private int map(float value, float rangeInMin, float rangeInMax, float rangeOutMin, float rangeOutMax){
        float clampValue = value;
        if(clampValue < rangeInMin)
            clampValue = rangeInMin;
        else if(clampValue > rangeInMax)
            clampValue = rangeInMax;
        return (int) ((clampValue - rangeInMin) / (rangeInMax - rangeInMin) * (rangeOutMax - rangeOutMin) + rangeOutMin);
    }

    public void setCarStatus(AppStatus status) {
        AppStatus previousStatus = getAppStatus();

        switch (status){
            case WAIT_TO_CONNECT:
                setAppStatus(AppStatus.WAIT_TO_CONNECT);
                break;
//            case CONNECT:
//                if(previousStatus == AppStatus.WAIT_TO_CONNECT) {
//                    setAppStatus(AppStatus.CONNECT);
//                }
//                break;

//            case WAIT_FOR_PLACE_SELECTION:
//                if(previousStatus == AppStatus.ARRIVE_AT_DESTINATION) { //previousStatus == AppStatus.CONNECT ||
//                    setAppStatus(AppStatus.WAIT_FOR_PLACE_SELECTION);
//                }
//                break;
            case IDLE:
                if(previousStatus == AppStatus.ARRIVE_AT_DESTINATION || previousStatus == AppStatus.STAY_AT_DESTINATION || previousStatus == AppStatus.CANCEL_PLACE) {
                    setAppStatus(AppStatus.IDLE);
                }
                break;
            case REQUEST_RANDOM_ROUTE:
                if(previousStatus == AppStatus.IDLE || previousStatus == AppStatus.LOST) {
                    setAppStatus(AppStatus.REQUEST_RANDOM_ROUTE);
                    requestRouteToRandomDestination(Integer.parseInt(currentNavigationCommand.nodeInfo.getNodeId()), null);
                }
                break;
            case REQUEST_ROUTE_TO_DESTINATION:
                if(previousStatus == AppStatus.IDLE || previousStatus == AppStatus.LOST || previousStatus == AppStatus.NAVIGATION_COMMAND_CANCELLED_DUE_TO_PLACE_SELECTION) {
                    setAppStatus(AppStatus.REQUEST_ROUTE_TO_DESTINATION);
                    requestRouteToDestination(Integer.parseInt(currentNavigationCommand.nodeInfo.getNodeId()), Integer.parseInt(currentDestinationNodeInfo.getNodeId()), currentDestinationPathId, null);
                }
                break;
            case RECEIVED_ROUTE_TO_DESTINATION:
                if(previousStatus == AppStatus.REQUEST_RANDOM_ROUTE || previousStatus == AppStatus.REQUEST_ROUTE_TO_DESTINATION) {
                    setAppStatus(AppStatus.RECEIVED_ROUTE_TO_DESTINATION);
                }
                break;
            case GET_NEXT_NAVIGATION_COMMAND:
                if(previousStatus == AppStatus.FINISH_AUTO_TURN_COMMAND || previousStatus == AppStatus.RECEIVED_ROUTE_TO_DESTINATION){
                    setAppStatus(AppStatus.GET_NEXT_NAVIGATION_COMMAND);
                }
                break;
            case ON_ROUTE_TO_NODE:
                if(previousStatus == AppStatus.GET_NEXT_NAVIGATION_COMMAND) {
                    setAppStatus(AppStatus.ON_ROUTE_TO_NODE);
                }
                break;
            case LOST:
                if(previousStatus == AppStatus.ON_ROUTE_TO_NODE) {
                    setAppStatus(AppStatus.LOST);
                }
                break;
            case ARRIVE_AT_NODE:
                if(previousStatus == AppStatus.ON_ROUTE_TO_NODE){
                    setAppStatus(AppStatus.ARRIVE_AT_NODE);
                }
                break;
            case WAIT_FOR_TRAFFIC:
                if(previousStatus == AppStatus.ARRIVE_AT_NODE){
                    setAppStatus(AppStatus.WAIT_FOR_TRAFFIC);
                }
                break;
            case START_AUTO_TURN_COMMAND:
                if(previousStatus == AppStatus.WAIT_FOR_TRAFFIC){
                    setAppStatus(AppStatus.START_AUTO_TURN_COMMAND);
                }
                break;
            case WORKING_ON_AUTO_TURN_COMMAND:
                if(previousStatus == AppStatus.START_AUTO_TURN_COMMAND){
                    setAppStatus(AppStatus.WORKING_ON_AUTO_TURN_COMMAND);
                }
                break;
            case FINISH_AUTO_TURN_COMMAND:
                if(previousStatus == AppStatus.WORKING_ON_AUTO_TURN_COMMAND){
                    setAppStatus(AppStatus.FINISH_AUTO_TURN_COMMAND);
                }
                break;
            case NAVIGATION_COMMAND_CANCELLED_DUE_TO_PLACE_SELECTION:
                setAppStatus(AppStatus.NAVIGATION_COMMAND_CANCELLED_DUE_TO_PLACE_SELECTION);
                break;
            case ARRIVE_AT_DESTINATION:
                if(previousStatus == AppStatus.GET_NEXT_NAVIGATION_COMMAND) {
                    setAppStatus(AppStatus.ARRIVE_AT_DESTINATION);
                }
                break;
            case STAY_AT_DESTINATION:
                if(previousStatus == AppStatus.ARRIVE_AT_DESTINATION) {
                    setAppStatus(AppStatus.STAY_AT_DESTINATION);
                }
                break;
            case CANCEL_PLACE:
                setAppStatus(AppStatus.CANCEL_PLACE);
                break;
            case HITTING_WALL:
                if(previousStatus != AppStatus.WAIT_TO_CONNECT && previousStatus != appStatus.PREPARE_TO_DISCONNECT && previousStatus != appStatus.DISCONNECT
                        && previousStatus != AppStatus.HITTING_WALL && previousStatus != AppStatus.FIX_HITTING_WALL_GO_BACKWARD && previousStatus != AppStatus.FIX_HITTING_WALL_SCRIPT_TURN_LEFT && previousStatus != AppStatus.FINISH_FIX_HITTING_WALL){
                    stateBeforeHittingWall = appStatus;
                    setAppStatus(AppStatus.HITTING_WALL);
                    hittingWallCounter = 0;
                }
                break;
            case FIX_HITTING_WALL_GO_BACKWARD:
                if(previousStatus == AppStatus.HITTING_WALL) {
                    setAppStatus(AppStatus.FIX_HITTING_WALL_GO_BACKWARD);
                }
                break;
            case FIX_HITTING_WALL_SCRIPT_TURN_LEFT:
                if(previousStatus == AppStatus.FIX_HITTING_WALL_GO_BACKWARD) {
                    setAppStatus(AppStatus.FIX_HITTING_WALL_SCRIPT_TURN_LEFT);
                }
                break;
            case FINISH_FIX_HITTING_WALL:
                if(previousStatus == AppStatus.FIX_HITTING_WALL_SCRIPT_TURN_LEFT) {
                    setAppStatus(AppStatus.FINISH_FIX_HITTING_WALL);
                }
                break;
            case PREPARE_TO_DISCONNECT:
                setAppStatus(AppStatus.PREPARE_TO_DISCONNECT);
                break;
            case DISCONNECT:
                if(previousStatus == AppStatus.PREPARE_TO_DISCONNECT) {
                    setAppStatus(AppStatus.DISCONNECT);
                }
            default:
                break;
        }
        sendAlive();
    }

    // flow //
    public void destroy() {
        isFinished = true;
        wheelController.park();
        wheelController.destroy();
        carVision.destroy();
        Log.e(TAG, "car controller thread is destroyed");
    }

    public void pause() {
        isPause = true;
        if(wheelController != null){
            wheelController.pause();
        }
        if(batteryInformation != null)
            batteryInformation.pause();

        if(carVision != null){
            carVision.pause();
        }
    }

    public void resume() {
        isPause = false;
        if(wheelController != null){
            wheelController.resume();
        }
        if(batteryInformation != null)
            batteryInformation.resume();

        if(carVision != null){
            carVision.resume();
        }
    }

    protected void setAppStatus(AppStatus status) {
        lock.writeLock().lock();
        appStatus = status;
        lock.writeLock().unlock();
    }

    public AppStatus getAppStatus() {
        AppStatus currentStatus;
        lock.readLock().lock();
        currentStatus = appStatus;
        lock.readLock().unlock();
        return currentStatus;
    }

    protected boolean compareDetectedObject(ObjectDetector.DetectedObject detectedObj, ObjectDetector.DetectedObject targetObj){
        String targetTitle = targetObj.getTitle();
        String detectedTitle = detectedObj.getTitle();
        if(detectedObj.getConfidence() >= targetObj.getConfidence()) {
            if (targetTitle.equals(detectedTitle)) {
                RectF targetBound = targetObj.getLocation();
                float targetArea = targetBound.width() * targetBound.height();
                RectF detectedBound = detectedObj.getLocation();
                float detectedArea = detectedBound.width() * detectedBound.height();

                Log.d(TAG, "width = " + Float.toString(detectedBound.width()) + " // height = " + Float.toString(detectedBound.height()));
                if ((detectedArea >= targetArea * areaThreshold.getMin()) && (detectedArea <= targetArea * areaThreshold.getMax())) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean compareDetectedCarObject(ObjectDetector.DetectedObject detectedObj, ObjectDetector.DetectedObject targetObj){
        String targetTitle = targetObj.getTitle();
        String detectedTitle = detectedObj.getTitle();
        if(detectedObj.getConfidence() >= targetObj.getConfidence()) {
            if (targetTitle.equals(detectedTitle)) {
                RectF targetBound = targetObj.getLocation();
                float targetArea = targetBound.width() * targetBound.height();
                RectF detectedBound = detectedObj.getLocation();
                float detectedArea = detectedBound.width() * detectedBound.height();

                Log.d(TAG, "width = " + Float.toString(detectedBound.width()) + " // height = " + Float.toString(detectedBound.height()));
                //if (detectedArea >= targetArea * areaThreshold.getMin()) {
                //if(detectedBound.width() >= targetBound.width() * areaThreshold.getMin() || detectedBound.height() >= targetBound.height() * areaThreshold.getMin()){

                if(detectedBound.bottom >= carArea.getMin_y()){
                    if(Config.getInstance().isInDebugMode())
                        baseListener.updateStateView("detected car / bottom = " + detectedBound.bottom);
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean detectOtherCar(){
        List<ObjectDetector.DetectedObject> detectedObjects = carVision.getObjectFound();
        if(detectedObjects == null){
            return false;
        }

        for(int iDetected = 0; iDetected < detectedObjects.size(); iDetected++) {
            ObjectDetector.DetectedObject detectedObj = detectedObjects.get(iDetected);
            if(compareDetectedCarObject(detectedObj, otherCarMarker)){
                return true;
            }
        }

        return false;
    }

    protected boolean fetchHistory(List<List<ObjectDetector.DetectedObject>> objectsHistory, ObjectDetector.DetectedObject detectedObj) {
        int seenCount = 0;
        for (int frame = objectsHistory.size() - 1; frame >= 0 ; frame--) {
            for (int index = 0; index < objectsHistory.get(frame).size(); index++) {

                if (objectsHistory.get(frame).get(index).getTitle().equals(detectedObj.getTitle()) ) {
                    seenCount++;
                }
            }
        }

        return seenCount >= Config.getInstance().getSeenCountTrigger();
    }

    protected boolean shouldIgnoreWeirdNodes(ObjectDetector.DetectedObject detectedObject, ObjectDetector.DetectedObject prevNodeObject){
        String detectedTitle = detectedObject.getTitle();
        String previousTitle = prevNodeObject.getTitle();

        if((previousTitle.equals("Traffic_light") && detectedTitle.equals("Korean")) || (previousTitle.equals("Planetarium") && detectedTitle.equals("Left"))
                || (previousTitle.equals("No_parking") && detectedTitle.equals("Tokyo_tower")) || previousTitle.equals("Left_or_through") && detectedTitle.equals("Museum")
                || previousTitle.equals("Left_or_through") && detectedTitle.equals("No_parking") || previousTitle.equals("Left") && detectedTitle.equals("Tokyo_tower")) {
            return true;
        }

        return false;
    }

    protected boolean objectDetect(NodeInfo pif){
        List<ObjectDetector.DetectedObject> detectedObjects = carVision.getObjectFound();

        if(detectedObjects == null || detectedObjects.size() == 0 || allNodeInfos == null || allNodeInfos.size() == 0){
            return false;
        }

        boolean foundObject = false;

        //add found objects in history
        while (objectsHistory.size() >= Config.getInstance().getMaxFrameHistory()){
            objectsHistory.remove(0);
        }
        objectsHistory.add(detectedObjects);

        for(int iDetected = 0; iDetected < detectedObjects.size(); iDetected++) {
            ObjectDetector.DetectedObject detectedObj = detectedObjects.get(iDetected);

            // check detected objects with every node object
            //if (detectedObj.getConfidence() >= confidenceThreshold) {
                for (int iNode = 0; iNode < allNodeInfos.size(); iNode++) {
                    List<ObjectDetector.DetectedObject> nodeObjects = allNodeInfos.get(iNode).getObjectList();
                    for (int iObj = 0; iObj < nodeObjects.size(); iObj++) {
                        ObjectDetector.DetectedObject nodeObj = nodeObjects.get(iObj);
                        if(prevNodeObject != null && detectedObj.getTitle().equals(prevNodeObject.getTitle())) {
                            foundObject = true;
                            continue;
                        }

                        if (compareDetectedObject(detectedObj, nodeObj)) {
                            // found a nodes
                            // re-check in the history, if found many times,
                            if(fetchHistory(objectsHistory, detectedObj)){
                                //wheelController.park();
                                if(prevNodeObject != null && shouldIgnoreWeirdNodes(detectedObj, prevNodeObject)){
                                    return false;
                                }

                                prevNodeObject = nodeObj;
                                //objectsHistory.clear();
                                //check if the found object is our target object or not
                                if(detectedObj.getTitle().equals(pif.getObjectList().get(0).getTitle())){
                                    setCarStatus(AppStatus.ARRIVE_AT_NODE);
                                }
                                else{
                                    setCarStatus(AppStatus.LOST);
                                    arriveWrongNode(Integer.parseInt(allNodeInfos.get(iNode).getNodeId()), nodeObj.getTitle());
                                }
                                return true;
                            }
                        }
                    }
                }
            //}
        }
        return foundObject;
    }





    ////////////////////////////////////////////////////////////API/////////////////////////////////////////////////////////////////////
    public void waitForStart() {
        ApiCommunicator.getInstance().waitForStart(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            listener.displayWaitJourney();
                        } else {
                            waitForStart();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {

            }
        });
    }

    protected void sendAlive(){
        AppStatus currentStatus = getAppStatus();
        if(currentStatus != null && currentStatus != AppStatus.DISCONNECT) {
            sendAliveAPI(currentStatus.toString());
        }
        sendAliveTimer = 0;
    }

    // apis //
    protected void sendAliveAPI(String appStatus){
        if(batteryInformation == null)
            return;
        ApiCommunicator.getInstance().alive(appStatus, batteryInformation.getBatteryPercentage(), warningStatus, errorStatus, new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                // do nothing
                if (response != null && response.body() != null) {

                    Log.d("", response.body().toString());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            // do nothing
                        }
                    } catch(JSONException e){
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

    public void CHEAT_arriveAtNode(){
        wheelController.park();
        setCarStatus(AppStatus.ARRIVE_AT_NODE);
    }

    protected void requestRouteToRandomDestination(int currentCarNodeId, List<Integer> obstacleNodeIds) {
        ApiCommunicator.getInstance().requestRouteToRandomDestination(currentCarNodeId, obstacleNodeIds, new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {

                            JSONArray nodeArray = returnObject.getJSONArray("route_path");

                            if(routeToDestination == null) {
                                routeToDestination = new ArrayList<NavigationCommand>();
                            }
                            else {
                                routeToDestination.clear();
                            }

                            for (int i = 0; i < nodeArray.length(); i++) {
                                JSONObject navObj = nodeArray.getJSONObject(i);
                                NavigationCommand nav = new NavigationCommand();
                                String navCommandString = navObj.getString("command");
                                nav.command = CMD.valueOf(navCommandString);
                                nav.nodeInfo = new NodeInfo(navObj);
                                routeToDestination.add(nav);
                            }

                            if(routeToDestination.size() != 0) {
                                currentDestinationNodeInfo = routeToDestination.get(routeToDestination.size() - 1).nodeInfo;
                                receivedRouteToDestination();
                            }
                            else{
                                setAppStatus(AppStatus.IDLE);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d(TAG, t.getMessage());
                baseListener.showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }

    protected void requestRouteToDestination(int currentCarNodeId, int destinationNodeId, int destinationPathId, List<Integer> obstacleNodeIds) {
        ApiCommunicator.getInstance().requestRouteToDestination(currentCarNodeId, destinationNodeId, destinationPathId, obstacleNodeIds, new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {

                            JSONArray nodeArray = returnObject.getJSONArray("route_path");

                            if(routeToDestination == null) {
                                routeToDestination = new ArrayList<NavigationCommand>();
                            }
                            else {
                                routeToDestination.clear();
                            }

                            for (int i = 0; i < nodeArray.length(); i++) {
                                JSONObject navObj = nodeArray.getJSONObject(i);
                                NavigationCommand nav = new NavigationCommand();
                                if(navObj.has("command")) {
                                    String navCommandString = navObj.getString("command");
                                    nav.command = CMD.valueOf(navCommandString);
                                }
                                else{
                                    nav.command = CMD.DO_NOTHING;
                                }
                                nav.nodeInfo = new NodeInfo(navObj);
                                routeToDestination.add(nav);
                            }

                            receivedRouteToDestination();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d(TAG, t.getMessage());
                baseListener.showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }

    protected void receivedRouteToDestination()
    {
        setCarStatus(AppStatus.RECEIVED_ROUTE_TO_DESTINATION);
    }


    protected void selectPlace(NodeInfo destinationNodeInfo, int destinationPathId)
    {
        currentDestinationNodeInfo = destinationNodeInfo;
        currentDestinationPathId = destinationPathId;
        listener.placeSelected();
        if(routeToDestination != null){
            routeToDestination.clear();
        }
        isWaitingForPlaceSelection = false;
        if(appStatus != AppStatus.START_AUTO_TURN_COMMAND &&
        appStatus != AppStatus.WORKING_ON_AUTO_TURN_COMMAND &&
        appStatus != AppStatus.FINISH_AUTO_TURN_COMMAND){
            setCarStatus(AppStatus.NAVIGATION_COMMAND_CANCELLED_DUE_TO_PLACE_SELECTION);
        }
        else{
            setCarStatus(AppStatus.REQUEST_ROUTE_TO_DESTINATION);
            isWaitingForManualTurnThenCancelNavigationCommand = true;
        }
    }

    protected void waitForPlaceSelection() {
        ApiCommunicator.getInstance().waitForPlaceSelection( new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            if(returnObject.has("destination_place_info")) {

                                JSONObject destinationObject = returnObject.getJSONObject("destination_place_info");
                                NodeInfo info = new NodeInfo(destinationObject);

                                int destinationPathId = returnObject.getInt("destination_path_id");
                                selectPlace(info, destinationPathId);
                            }

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d("1", t.getMessage());
            }
        });
    }

    protected void waitForCancelPlace() {
        ApiCommunicator.getInstance().waitForCancelPlace( new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            String serverTrigger = returnObject.getString("server_trigger");
                            if (serverTrigger.equals("CANCEL_PLACE")) {
                                setCarStatus(AppStatus.CANCEL_PLACE);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d("1", t.getMessage());
            }
        });
    }

    protected void arriveAtNode(int nodeId) {
        ApiCommunicator.getInstance().arriveAtNode(nodeId, new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            setCarStatus(AppStatus.WAIT_FOR_TRAFFIC);
                            if(shouldPlayDebugSound) {
                                MediaPlayer mp = MediaPlayer.create(activityContext, R.raw.arrive_at_node);
                                mp.start();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                Log.e("API_RESPONSE : arriveAtNode", response.body().toString());
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d(TAG, t.getMessage());
                baseListener.showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }


    private void requestNewRouteWhenLost() {
        isArriveWrongNode = true;
        if(isWaitingForPlaceSelection)
            setCarStatus(AppStatus.REQUEST_RANDOM_ROUTE);
        else
            setCarStatus(AppStatus.REQUEST_ROUTE_TO_DESTINATION);
    }

    protected void arriveWrongNode(int nodeId, String objectClass) {
        ApiCommunicator.getInstance().arriveWrongNode(nodeId, objectClass, new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {

                if(response != null && response.body() != null) {
                    Log.e("API_RESPONSE : arriveWrongNode", response.body().toString());

                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            requestNewRouteWhenLost();
                            if(shouldPlayDebugSound) {
                                MediaPlayer mp = MediaPlayer.create(activityContext, R.raw.arrive_wrong_node);
                                mp.start();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d(TAG, t.getMessage());
                baseListener.showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }

    protected void waitForTraffic(int nodeId) {
        ApiCommunicator.getInstance().waitForTraffic(nodeId, new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            if(returnObject.has("can_go")) {
                                if(returnObject.getBoolean("can_go"))
                                    setCarStatus(AppStatus.START_AUTO_TURN_COMMAND);
                                else
                                    wheelController.park();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d(TAG, t.getMessage());
            }
        });
    }

    protected void finishAutoTurnCommand(int nodeId) {
        ApiCommunicator.getInstance().finishAutoTurnCommand(nodeId, new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            if(isWaitingForManualTurnThenCancelNavigationCommand){
                                setCarStatus(AppStatus.NAVIGATION_COMMAND_CANCELLED_DUE_TO_PLACE_SELECTION);
                            }
                            else{
                                setCarStatus(AppStatus.GET_NEXT_NAVIGATION_COMMAND);
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d(TAG, t.getMessage());
                baseListener.showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }

    protected void arriveAtDestination(int destinationNodeId) {
        ApiCommunicator.getInstance().arriveAtDestination(destinationNodeId, new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            if(shouldPlayDebugSound) {
                                MediaPlayer mp = MediaPlayer.create(activityContext, R.raw.arrive_at_destination);
                                mp.start();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d(TAG, t.getMessage());
                baseListener.showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }

    protected void waitForStationDisconnect() {
        ApiCommunicator.getInstance().waitForStationDisconnect( new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            setCarStatus(AppStatus.PREPARE_TO_DISCONNECT);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                d(TAG, t.getMessage());
            }
        });
    }

    public void disconnect() {
        ApiCommunicator.getInstance().disconnectCar(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            setCarStatus(AppStatus.PREPARE_TO_DISCONNECT);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Log.d("DISCONNECT", t.getMessage());
                baseListener.showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }


    public void checkStationVideoStatus(){
        ApiCommunicator.getInstance().videoStatus(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d(TAG, response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            String videoStatus = returnObject.getString("video_status");
                            if (videoStatus.equals("NOT_PLAY")) {
                                listener.displayStandby();
                                setCarStatus(AppStatus.IDLE);
                                isWaitingForPlaceSelection = true;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Log.d("VIDEO STATION STATUS", t.getMessage());
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