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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.bit.pixelopolis_car.services.BaseListener;
import com.bit.pixelopolis_car.services.api.ApiCommunicator;
import com.bit.pixelopolis_car.R;
import com.bit.pixelopolis_car.enums.ErrorStatus;
import com.bit.pixelopolis_car.services.CarInformation;
import com.bit.pixelopolis_car.services.config.Config;

import org.json.JSONException;
import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// notify user if the app is outdated

public class UpdateAppActivity extends BaseActivity {

    TextView versionView;
    BaseListener baseListener;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_app);
        versionView = findViewById(R.id.version_view);
        versionView.setText("Your application is outdated!\n\nCurrent Version : " + Config.APP_VERSION + "\nLatest Version : " + Config.getInstance().getLatestAppVersion());
        setCarId(CarInformation.getInstance().getCarId());
        setBaseListener(this);
    }

    public void onClickContinueConnectToServer(View view) {
        baseListener.showSpinner();
        connectToServer();
    }

    public void updateApplication(View view) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse(Config.getInstance().getAppDownloadPath()));
        startActivity(intent);
    }

    public void goToNextActivity() {
        Intent intent = new Intent(UpdateAppActivity.this, NavigateActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    public void connectToServer() {
        updateStateView("CONNECT");
        ApiCommunicator apiCommunicator = ApiCommunicator.getInstance();
        apiCommunicator.connectCar(new retrofit2.Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response != null && response.body() != null) {
                    Log.d("API_CALL", response.body());
                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            waitForConnectStation();
                            updateStateView("WAIT_FOR_STATION_TO_CONNECT");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Log.d("API_CALL", t.getMessage());
                baseListener.hideSpinner();
                showError(ErrorStatus.CANNOT_COMMUNICATE_WITH_SERVER);
            }
        });
    }

    protected void waitForConnectStation(){
        ApiCommunicator.getInstance().waitForConnectStation(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if(response != null && response.body() != null) {
                    Log.d("API_CALL", response.body());

                    try {
                        JSONObject returnObject = new JSONObject(response.body());
                        if (returnObject.getBoolean("success")) {
                            baseListener.hideSpinner();
                            goToNextActivity();
                        } else {
                            waitForConnectStation();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                // do nothing
                baseListener.hideSpinner();
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
