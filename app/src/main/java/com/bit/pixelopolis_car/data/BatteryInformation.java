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

package com.bit.pixelopolis_car.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryInformation {
    private float batteryPercentage = -1.0f;
    Context myActivityContext;
    public BatteryInformation(Context activity_context)
    {
        myActivityContext = activity_context;
        activity_context.registerReceiver(this.batteryInfoReceiver,	new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // this is where we deal with the data sent from the battery.
            int  level= intent.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
            int  scale= intent.getIntExtra(BatteryManager.EXTRA_SCALE,0);

            batteryPercentage = level / (float)scale;
        }
    };

    public int getBatteryPercentage()
    {
        return (int)(batteryPercentage * 100.0f);
    }

    public void resume() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        myActivityContext.registerReceiver(batteryInfoReceiver, filter);
    }

    public void pause() {
        myActivityContext.unregisterReceiver(batteryInfoReceiver);
    }
}
