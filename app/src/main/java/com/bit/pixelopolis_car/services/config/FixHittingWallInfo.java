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

package com.bit.pixelopolis_car.services.config;

public class FixHittingWallInfo {
    int sensorMinThreshold;
    int sensorMaxThreshold;
    int frameCounterThreshold;
    int goBackDuration;
    int turnAngle;
    boolean isEnable;

    public int getSensorMinThreshold() {
        return sensorMinThreshold;
    }

    public void setSensorMinThreshold(int sensorMinThreshold) {
        this.sensorMinThreshold = sensorMinThreshold;
    }

    public int getSensorMaxThreshold() {
        return sensorMaxThreshold;
    }

    public void setSensorMaxThreshold(int sensorMaxThreshold) {
        this.sensorMaxThreshold = sensorMaxThreshold;
    }

    public int getFrameCounterThreshold() {
        return frameCounterThreshold;
    }

    public void setFrameCounterThreshold(int frameCounterThreshold) {
        this.frameCounterThreshold = frameCounterThreshold;
    }

    public int getGoBackDuration() {
        return goBackDuration;
    }

    public void setGoBackDuration(int goBackDuration) {
        this.goBackDuration = goBackDuration;
    }

    public boolean isSensorValueInRange(int value){
        if(value >= sensorMinThreshold && value <= sensorMaxThreshold)
            return true;
        else
            return false;
    }

    public int getTurnAngle() {
        return turnAngle;
    }

    public void setTurnAngle(int turnAngle) {
        this.turnAngle = turnAngle;
    }

    public boolean isEnable() {
        return isEnable;
    }

    public void setEnable(boolean enable) {
        isEnable = enable;
    }
}
