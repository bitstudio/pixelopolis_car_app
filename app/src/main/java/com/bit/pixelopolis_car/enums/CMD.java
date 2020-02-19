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

public enum CMD {
    DO_NOTHING("DO_NOTHING"),
    GO_BACKWARD("GO_BACKWARD"),
    GO_FORWARD("GO_FORWARD"),
    TURN_LEFT("TURN_LEFT"),
    TURN_RIGHT("TURN_RIGHT"),
    SHARP_TURN_LEFT("SHARP_TURN_LEFT"),
    SHARP_TURN_RIGHT("SHARP_TURN_RIGHT"),
    FIX_HITTING_WALL_TURN_LEFT("FIX_HITTING_WALL_TURN_LEFT");

    private final String name;
    private CMD(String name){
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
