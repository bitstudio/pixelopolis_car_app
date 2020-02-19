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

import android.util.Log;
import com.bit.pixelopolis_car.enums.CMD;
import com.bit.pixelopolis_car.services.carvision.CarVision;
import com.bit.pixelopolis_car.services.config.CommandTime;
import com.bit.pixelopolis_car.services.config.Config;


public class WheelController {
    private static final String TAG = "WheelController";
    private static final int DEFAULT_CAR_TIME = 800;
    private static int UPDATE_INTERVAL = 40;
    private static final int BREAK_SPEED = 0;

    // Dynamixels Parameters
    private static final float XL430_RPM_PER_UNIT = 0.229f; //RPM per unit

    // Car Parameters
    private static final float LENGTH_BTW_WHEEL = 0.12518f; //Meters
    private static final float WHEEL_RADIUS = 0.0315f; //Meters

    Thread wheelControllerThread;
    CarVision carVision = null;
    WheelControllerListener listener;
    CommandTime commandTime;

    boolean isFinished = false;
    boolean isPause = false;
    boolean isDriving = false;
    boolean isLaneKeeping = true;

    int defaultWheelSpeed = 195;
    int defaultWheelSlowSpeed = 65;

    CMD scriptMode = CMD.DO_NOTHING;

    public enum WheelDirection{
        FORWARD("f"),
        BACKWARD("b");

        private final String command;
        WheelDirection(String command) {this.command = command;}
        public String toString() {return this.command;}
    }

    public interface WheelControllerListener {
        void sendSerialMessage(int leftSpeed, int rightSpeed);
    }

    public WheelController(CarVision carVision, WheelControllerListener listener)
    {
        wheelControllerThread = new Thread(new WheelController.WheelControllerThread());
        wheelControllerThread.start();

        this.carVision = carVision;
        this.listener = listener;

        commandTime = Config.getInstance().getCommandTime();
        defaultWheelSpeed = Config.getInstance().getDefaultWheelSpeed();
        defaultWheelSlowSpeed = Config.getInstance().getSlowWheelSpeed();

    }

    public void destroy() {
        isFinished = true;
        Log.e(TAG, "wheel controller thread is destroyed");
    }

    public void driveInLaneKeepingMode() {
        isDriving = true;
        isLaneKeeping = true;
    }

    public void driveInScriptMode(CMD mode){
        isDriving = true;
        isLaneKeeping = false;
        scriptMode = mode;
    }

    public void park() {
        isDriving = false;
    }

    public int getScriptDuration(CMD mode){
        int duration = 0;
        switch (mode) {
            case DO_NOTHING:
                break;
            case GO_BACKWARD:
                duration = commandTime.forward;
                break;
            case GO_FORWARD:
                duration = commandTime.forward;
                break;
            case TURN_LEFT:
                duration = commandTime.turning;
                break;
            case TURN_RIGHT:
                duration = commandTime.turning;
                break;
            case SHARP_TURN_LEFT:
                duration = (int)calculateTurnInPlaceDuration(35);
                break;
            case SHARP_TURN_RIGHT:
                duration = (int)calculateTurnInPlaceDuration(35);
                break;
            case FIX_HITTING_WALL_TURN_LEFT:
                duration = (int)calculateTurnInPlaceDuration(Config.getInstance().getFixHittingWallInfo().getTurnAngle());
                break;
        }
        return duration;
    }

    private float calculateTurnInPlaceDuration(int degree){
        return (DEG2RAD(degree) * LENGTH_BTW_WHEEL) / (2 * WHEEL_RADIUS * RPM2RPS(XL430_RPM_PER_UNIT * defaultWheelSlowSpeed)) * 1000.0f;
    }

    float RPM2RPS(float rpm){
        return(float)((rpm * Math.PI) / 30.0f);
    }


    float RPS2RPM(float rad){
        return(float)((rad * 30.0f) / Math.PI);
    }


    float DEG2RAD(float deg){
        return(float)((deg * Math.PI) / 180.0f);
    }

    public void pause() {
        isPause = true;
    }

    public void resume() {
        isPause = false;
    }



    public class WheelControllerThread implements Runnable{

        boolean hasScriptStarted = false;

        public void run(){
            while (!isFinished) {
                if(!isPause) {
                    update();
                }
            }
            listener.sendSerialMessage(BREAK_SPEED, BREAK_SPEED);
        }

        private void update(){
            if(isDriving){
                if(isLaneKeeping) {
                    laneKeep();
                }
                else{
                    driveByScript();
                }
            }
            else{
                listener.sendSerialMessage(BREAK_SPEED, BREAK_SPEED);//, WheelDirection.FORWARD.toString(), WheelDirection.FORWARD.toString());
                hasScriptStarted = false;
            }


            try {
                Thread.sleep(UPDATE_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void laneKeep() {
            float angle = carVision.getSteeringAngle();
            setCarWheelSpeed(defaultWheelSpeed, (int)angle);
        }

        private void driveByScript(){ // helps when lane is not visible, and when receive command from server
            if(hasScriptStarted)
                return;

            switch (scriptMode) {
                case DO_NOTHING:
                    laneKeep();
                    break;
                case GO_BACKWARD:
                    listener.sendSerialMessage(-defaultWheelSlowSpeed, -defaultWheelSlowSpeed);
                    break;
                case GO_FORWARD:
                    laneKeep();
                    break;
                case TURN_LEFT:
                    listener.sendSerialMessage(defaultWheelSlowSpeed, defaultWheelSpeed);
                    break;
                case TURN_RIGHT:
                    listener.sendSerialMessage(defaultWheelSpeed, defaultWheelSlowSpeed);
                    break;
                case SHARP_TURN_LEFT:
                    listener.sendSerialMessage(-defaultWheelSlowSpeed, defaultWheelSlowSpeed);
                    break;
                case SHARP_TURN_RIGHT:
                    listener.sendSerialMessage(defaultWheelSlowSpeed, -defaultWheelSlowSpeed);
                    break;
                case FIX_HITTING_WALL_TURN_LEFT:
                    listener.sendSerialMessage(-defaultWheelSlowSpeed, defaultWheelSlowSpeed);//, WheelDirection.BACKWARD.toString(), WheelDirection.FORWARD.toString());
                    break;
            }
        }

        private int map(float value, float rangeInMin, float rangeInMax, float rangeOutMin, float rangeOutMax){
            return (int) ((value - rangeInMin) / (rangeInMax - rangeInMin) * (rangeOutMax - rangeOutMin) + rangeOutMin);
        }

        private float calculateTurnVelocity(float alpha,float delta_time){
            float omega = DEG2RAD(alpha) / delta_time * 1000.0f;                                  //radian per second
            float omega_fix = RPM2RPS(XL430_RPM_PER_UNIT * defaultWheelSpeed);               //radian per second
            float constant = LENGTH_BTW_WHEEL / WHEEL_RADIUS;
            float omega_x = omega_fix - (omega * constant);                             //radian per second
            float goal_velocity = RPS2RPM(omega_x) / XL430_RPM_PER_UNIT;                // Velocity Unit


            return(goal_velocity);
        }

        private void setCarWheelSpeed(int speed, int angle)
        {
            int lSpeed = 0, rSpeed = 0, time= DEFAULT_CAR_TIME;

            //break
            if(speed == 0){
                lSpeed = BREAK_SPEED;
                rSpeed = BREAK_SPEED;
            }
            //Forward
            else if(angle == 0){
                lSpeed = defaultWheelSpeed;
                rSpeed = defaultWheelSpeed;
            }
            //turn left
            else if(angle < 0){
                lSpeed = (int) calculateTurnVelocity(Math.abs(angle),time);
                rSpeed = defaultWheelSpeed;
            }
            //turn right
            else if (angle > 0){
                lSpeed = defaultWheelSpeed;
                rSpeed = (int) calculateTurnVelocity(Math.abs(angle),time);
            }

            if (rSpeed == 0 && lSpeed!=0){
                rSpeed = 1;
            }
            if(rSpeed < -288) rSpeed = -288;
            if(rSpeed > 312) rSpeed = 312;
            if(lSpeed < -288) lSpeed = -288;
            if(lSpeed > 312) lSpeed = 312;

            if(listener != null)
                listener.sendSerialMessage(lSpeed, rSpeed);
        }
    }
}
