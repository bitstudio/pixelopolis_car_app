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

package com.bit.pixelopolis_car.enums;

public enum AppStatus {
    WAIT_TO_CONNECT("WAIT_TO_CONNECT"),
    //CONNECT("CONNECT"),

    //WAIT_FOR_PLACE_SELECTION("WAIT_FOR_PLACE_SELECTION"),
    IDLE("IDLE"),
    REQUEST_RANDOM_ROUTE("REQUEST_RANDOM_ROUTE"),
    REQUEST_ROUTE_TO_DESTINATION("REQUEST_ROUTE_TO_DESTINATION"),
    RECEIVED_ROUTE_TO_DESTINATION("RECEIVED_ROUTE_TO_DESTINATION"),
    ON_ROUTE_TO_NODE("ON_ROUTE_TO_NODE"),
    GET_NEXT_NAVIGATION_COMMAND("GET_NEXT_NAVIGATION_COMMAND"),
    ARRIVE_AT_NODE("ARRIVE_AT_NODE"),
    LOST("LOST"),
    WAIT_FOR_TRAFFIC("WAIT_FOR_TRAFFIC"),
    START_AUTO_TURN_COMMAND("START_AUTO_TURN_COMMAND"),
    WORKING_ON_AUTO_TURN_COMMAND("WORKING_ON_AUTO_TURN_COMMAND"),
    FINISH_AUTO_TURN_COMMAND("FINISH_AUTO_TURN_COMMAND"),
    ARRIVE_AT_DESTINATION("ARRIVE_AT_DESTINATION"),
    STAY_AT_DESTINATION("STAY_AT_DESTINATION"),
    NAVIGATION_COMMAND_CANCELLED_DUE_TO_PLACE_SELECTION("NAVIGATION_COMMAND_CANCELLED_DUE_TO_PLACE_SELECTION"),
    CANCEL_PLACE("CANCEL_PLACE"),
    HITTING_WALL("HITTING_WALL"),
    FIX_HITTING_WALL_GO_BACKWARD("FIX_HITTING_WALL_GO_BACKWARD"),
    FIX_HITTING_WALL_SCRIPT_TURN_LEFT("FIX_HITTING_WALL_SCRIPT_TURN_LEFT"),
    FINISH_FIX_HITTING_WALL("FINISH_FIX_HITTING_WALL"),
    PREPARE_TO_DISCONNECT("PREPARE_TO_DISCONNECT"),
    DISCONNECT("DISCONNECT");

    private final String name;
    private AppStatus(String name){
        this.name = name;
    }

    public boolean equalsName(String otherName) {
        // (otherName == null) check is not needed because name.equals(null) returns false
        return name.equals(otherName);
    }

    public String toString() {
        return this.name;
    }
}
