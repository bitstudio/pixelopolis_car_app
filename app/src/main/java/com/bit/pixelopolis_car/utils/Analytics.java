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

package com.bit.pixelopolis_car.utils;

import android.content.Context;
import android.os.Bundle;

import com.bit.pixelopolis_car.services.CarInformation;
import com.google.firebase.analytics.FirebaseAnalytics;

public class Analytics {

    public static void logError(Context context, String errorStatus) {
        FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(context);
        CarInformation carInfo = CarInformation.getInstance();

        Bundle params = new Bundle();
        params.putString("error_message", errorStatus);
        params.putString("car_id", carInfo.getCarId());
        params.putString("car_device_id", carInfo.getDeviceId());
        firebaseAnalytics.logEvent("error_status", params);
        firebaseAnalytics.setUserProperty("car_id", carInfo.getCarId());
        firebaseAnalytics.setUserProperty("car_device_id", carInfo.getDeviceId());
    }

    public static void logWarning(Context context, String warningStatus) {
        FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(context);
        CarInformation carInfo = CarInformation.getInstance();

        Bundle params = new Bundle();
        params.putString("warning_message", warningStatus);
        params.putString("car_id", carInfo.getCarId());
        params.putString("car_device_id", carInfo.getDeviceId());
        firebaseAnalytics.logEvent("warning_status", params);
        firebaseAnalytics.setUserProperty("car_id", carInfo.getCarId());
        firebaseAnalytics.setUserProperty("car_device_id", carInfo.getDeviceId());
    }


}
