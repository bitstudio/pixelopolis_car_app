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

package com.bit.pixelopolis_car.services.streaming;

public class MovingAverage {
    private int end = 0;
    private int length = 0;
    private final int numValues;
    private long sum = 0;
    private final long[] values;

    MovingAverage(int numValues) {
        this.numValues = numValues;
        this.values = new long[numValues];
    }

    public void update(long value) {
        this.sum -= this.values[this.end];
        this.values[this.end] = value;
        this.end = (this.end + 1) % this.numValues;
        if (this.length < this.numValues) {
            this.length++;
        }
        this.sum += value;
    }

    public double getAverage() {
        return ((double) this.sum) / ((double) this.length);
    }
}
