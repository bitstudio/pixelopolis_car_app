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

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bit.pixelopolis_car.enums.ErrorStatus;
import com.bit.pixelopolis_car.enums.WarningStatus;
import com.bit.pixelopolis_car.services.BaseListener;
import com.bit.pixelopolis_car.R;
import com.bit.pixelopolis_car.services.config.Config;
import com.bit.pixelopolis_car.utils.Analytics;

// Here is the base activity for every activities, which provides fundamental stuffs that needed in every activities

public class BaseActivity  extends AppCompatActivity implements BaseListener {
    protected LinearLayout fullLayout;
    protected FrameLayout actContent;
    protected ImageView warningImageView;
    protected ImageView errorImageView;
    protected TextView stateTextView;
    protected TextView carIdTextView;
    protected View spinnerLayout;
    View root;

    private static int ERROR_CANNOT_COMMUNICATE_WITH_CONTROLLER_BOARD_IMAGE = R.drawable.error_302;
    private static int ERROR_CANNOT_COMMUNICATE_WITH_SERVER_IMAGE = R.drawable.error_301;

    private static int WARNING_CAR_PHONE_LOW_BATTERY_IMAGE = R.drawable.error_101;
    private static int WARNING_CAR_PHONE_VERY_LOW_BATTERY_IMAGE = R.drawable.error_102;
    private static int WARNING_MOTOR_LOW_BATTERY_IMAGE = R.drawable.error_103;
    private static int WARNING_MOTOR_VERY_LOW_BATTERY_IMAGE = R.drawable.error_104;
    private static int WARNING_LOST_IMAGE = R.drawable.error_105;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void setStateTextView(String text) {
        if(stateTextView != null) {
            runOnUiThread(() -> stateTextView.setText(text));
        }
    }

    @Override
    public void updateStateView(String string){
        setStateTextView(string);
    }

    @Override
    public void setContentView(final int layoutResID) {
        // hide status bar
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        // base layout
        fullLayout= (LinearLayout) getLayoutInflater().inflate(R.layout.activity_base, null);
        fullLayout.setOrientation(LinearLayout.VERTICAL);
        actContent= (FrameLayout) fullLayout.findViewById(R.id.act_content);
        root = actContent.getRootView();

        // set content of layout to the act_content frame
        getLayoutInflater().inflate(layoutResID, actContent, true);
        super.setContentView(fullLayout);

        stateTextView = findViewById(R.id.state_textbox);
        if(!Config.getInstance().isInDebugMode()) {
            stateTextView.setVisibility(View.GONE);
        }

        carIdTextView = findViewById(R.id.car_id_textbox);
        if(!Config.getInstance().isInDebugMode()){
            carIdTextView.setVisibility(View.GONE);
        }

        spinnerLayout = fullLayout.findViewById(R.id.spinner_layout);

        showWarning(WarningStatus.NONE);
        showError(ErrorStatus.NONE);
    }

    @Override
    public void setCarId(String id){
        carIdTextView.setText("ID " + id);
    }

    @Override
    public void showWarning(WarningStatus warningStatus){
        if(warningImageView == null)
            warningImageView = findViewById(R.id.warning_image);

        switch (warningStatus) {
            case NONE:
                runOnUiThread(() -> warningImageView.setVisibility(View.INVISIBLE));
                break;
            case CAR_PHONE_LOW_BATTERY:
                runOnUiThread(() -> warningImageView.setImageResource(WARNING_CAR_PHONE_LOW_BATTERY_IMAGE));
                runOnUiThread(() -> warningImageView.setVisibility(View.VISIBLE));
                break;
            case CAR_PHONE_VERY_LOW_BATTERY:
                runOnUiThread(() -> warningImageView.setImageResource(WARNING_CAR_PHONE_VERY_LOW_BATTERY_IMAGE));
                runOnUiThread(() -> warningImageView.setVisibility(View.VISIBLE));
                break;
            case MOTOR_LOW_BATTERY:
                runOnUiThread(() -> warningImageView.setImageResource(WARNING_MOTOR_LOW_BATTERY_IMAGE));
                runOnUiThread(() -> warningImageView.setVisibility(View.VISIBLE));
                break;
            case MOTOR_VERY_LOW_BATTERY:
                runOnUiThread(() -> warningImageView.setImageResource(WARNING_MOTOR_VERY_LOW_BATTERY_IMAGE));
                runOnUiThread(() -> warningImageView.setVisibility(View.VISIBLE));
                break;
            case LOST:
                runOnUiThread(() -> warningImageView.setImageResource(WARNING_LOST_IMAGE));
                runOnUiThread(() -> warningImageView.setVisibility(View.VISIBLE));
                break;
        }
        Analytics.logWarning(this, warningStatus.toString());
    }

    @Override
    public void showError(ErrorStatus errorStatus){
        if(errorImageView == null)
            errorImageView = findViewById(R.id.error_image);

        switch (errorStatus) {
            case NONE:
                runOnUiThread(() -> errorImageView.setVisibility(View.INVISIBLE));
                break;
            case CANNOT_COMMUNICATE_WITH_CONTROLLER_BOARD:
                runOnUiThread(() -> errorImageView.setImageResource(ERROR_CANNOT_COMMUNICATE_WITH_CONTROLLER_BOARD_IMAGE));
                runOnUiThread(() -> errorImageView.setVisibility(View.VISIBLE));
                break;
            case CANNOT_COMMUNICATE_WITH_SERVER:
                runOnUiThread(() -> errorImageView.setImageResource(ERROR_CANNOT_COMMUNICATE_WITH_SERVER_IMAGE));
                runOnUiThread(() -> errorImageView.setVisibility(View.VISIBLE));
                break;
        }
        Analytics.logError(this, errorStatus.toString());
    }

    @Override
    public void showSpinner() {
        spinnerLayout.setVisibility(View.VISIBLE);
        new CountDownTimer(Config.SPINNER_TIME_OUT, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                spinnerLayout.setVisibility(View.GONE);
            }
        }.start();
    }

    @Override
    public void hideSpinner() {
        spinnerLayout.setVisibility(View.GONE);
    }

    public void onClickWarningMessage(View view) {
        warningImageView.setVisibility(View.INVISIBLE);
    }

    public void onClickErrorMessage(View view) {
        errorImageView.setVisibility(View.INVISIBLE);
    }
}
