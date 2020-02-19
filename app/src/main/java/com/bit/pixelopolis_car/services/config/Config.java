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

import android.util.Pair;

import com.bit.pixelopolis_car.data.NodeInfo;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public static String APP_VERSION = "1.8";
    public static int SPINNER_TIME_OUT = 8000;

    private static final Config ourInstance = new Config();

    public static Config getInstance() {
        return ourInstance;
    }

    CommandTime commandTime;
    SpawnLocation spawnLocation;
    int maxTimeOut;
    List<NodeInfo> allNodeInfos;
    AreaThreshold areaThreshold;
    double defaultConfidenceThreshold;
    CarArea carArea;
    int maxSteeringAngle;
    int maxFrameHistory;
    int seenCountTrigger;
    boolean isInDebugMode;
    private ArrayList<Pair<Integer, Integer>> attractionNodeAndPathIds;
    String latestAppVersion;
    String appDownloadPath;
    int defaultWheelSpeed;
    int slowWheelSpeed;
    FixHittingWallInfo fixHittingWallInfo;
    int batteryLowThreshold;
    int batteryVeryLowThreshold;

    public String getLatestAppVersion() {
        return latestAppVersion;
    }

    public void setLatestAppVersion(String latestAppVersion) {
        this.latestAppVersion = latestAppVersion;
    }

    public String getAppDownloadPath() {
        return appDownloadPath;
    }

    public void setAppDownloadPath(String appDownloadPath) {
        this.appDownloadPath = appDownloadPath;
    }

    public ArrayList<Pair<Integer, Integer>> getAttractionNodeAndPathIds() {
        return attractionNodeAndPathIds;
    }

    public void setAttractionNodeAndPathIds(ArrayList<Pair<Integer, Integer>> attractionNodeAndPathIds) {
        this.attractionNodeAndPathIds = attractionNodeAndPathIds;
    }

    public int getMaxSteeringAngle() {
        return maxSteeringAngle;
    }

    public void setMaxSteeringAngle(int maxSteeringAngle) {
        this.maxSteeringAngle = maxSteeringAngle;
    }

    public CommandTime getCommandTime() {
        return commandTime;
    }

    public void setCommandTime(CommandTime commandTime) {
        this.commandTime = commandTime;
    }

    public SpawnLocation getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(SpawnLocation spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public int getMaxTimeOut() {
        return maxTimeOut;
    }

    public void setMaxTimeOut(int maxTimeOut) {
        this.maxTimeOut = maxTimeOut;
    }

    public List<NodeInfo> getAllNodeInfos() {
        return allNodeInfos;
    }

    public void setAllNodeInfos(List<NodeInfo> allNodeInfos) {
        this.allNodeInfos = allNodeInfos;
    }

    public AreaThreshold getAreaThreshold() {
        return areaThreshold;
    }

    public void setAreaThreshold(AreaThreshold areaThreshold) {
        this.areaThreshold = areaThreshold;
    }

    public CarArea getCarArea() {
        return carArea;
    }

    public void setCarArea(CarArea carArea) {
        this.carArea = carArea;
    }

    public boolean isInDebugMode() {
        return isInDebugMode;
    }

    public void setInDebugMode(boolean inDebugMode) {
        isInDebugMode = inDebugMode;
    }

    public int getMaxFrameHistory() {
        return maxFrameHistory;
    }

    public void setMaxFrameHistory(int maxFrameHistory) {
        this.maxFrameHistory = maxFrameHistory;
    }

    public int getSeenCountTrigger() {
        return seenCountTrigger;
    }

    public void setSeenCountTrigger(int seenCountTrigger) {
        this.seenCountTrigger = seenCountTrigger;
    }

    public double getDefaultConfidenceThreshold() {
        return defaultConfidenceThreshold;
    }

    public void setDefaultConfidenceThreshold(double defaultConfidenceThreshold) {
        this.defaultConfidenceThreshold = defaultConfidenceThreshold;
    }

    public int getDefaultWheelSpeed() {
        return defaultWheelSpeed;
    }

    public void setDefaultWheelSpeed(int defaultWheelSpeed) {
        this.defaultWheelSpeed = defaultWheelSpeed;
    }

    public int getSlowWheelSpeed() {
        return slowWheelSpeed;
    }

    public void setSlowWheelSpeed(int slowWheelSpeed) {
        this.slowWheelSpeed = slowWheelSpeed;
    }

    public FixHittingWallInfo getFixHittingWallInfo() {
        return fixHittingWallInfo;
    }

    public void setFixHittingWallInfo(FixHittingWallInfo fixHittingWallInfo) {
        this.fixHittingWallInfo = fixHittingWallInfo;
    }

    public int getBatteryLowThreshold() {
        return batteryLowThreshold;
    }

    public void setBatteryLowThreshold(int batteryLowThreshold) {
        this.batteryLowThreshold = batteryLowThreshold;
    }

    public int getBatteryVeryLowThreshold() {
        return batteryVeryLowThreshold;
    }

    public void setBatteryVeryLowThreshold(int batteryVeryLowThreshold) {
        this.batteryVeryLowThreshold = batteryVeryLowThreshold;
    }
}
