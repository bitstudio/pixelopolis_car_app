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

package com.bit.pixelopolis_car.services.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface CallWebService {
    @Headers("Content-Type: application/json")
    @POST("/connect_car")
    Call<String> connectCar(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/wait_for_connect_station")
    Call<String> waitForConnectStation(@Body String body);

    @Headers("Content-Type: application/json")
    @GET("/get_all_node_data")
    Call<String> getAllNodeData();

    @Headers("Content-Type: application/json")
    @POST("/wait_for_start")
    Call<String> waitForStart(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/alive")
    Call<String> alive(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/wait_for_place_selection")
    Call<String> waitForPlaceSelection(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/wait_for_cancel_place")
    Call<String> waitForCancelPlace(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/request_route_to_random_destination")
    Call<String> requestRouteToRandomDestination(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/request_route_to_destination")
    Call<String> requestRouteToDestination(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/arrive_at_node")
    Call<String> arriveAtNode(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/arrive_wrong_node")
    Call<String> arriveWrongNode(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/wait_for_traffic")
    Call<String> waitForTraffic(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/finish_auto_turn_command")
    Call<String> finishAutoTurnCommand(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/arrive_at_destination")
    Call<String> arriveAtDestination(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/wait_for_station_disconnect")
    Call<String> waitForStationDisconnect(@Body String body);

    @Headers("Content-Type: application/json")
    @POST("/disconnect_car")
    Call<String> disconnectCar(@Body String body);

    @Headers("Content-Type: application/json")
    @GET("/get_config")
    Call<String> getConfig();

    @Headers("Content-Type: application/json")
    @POST("/video_status")
    Call<String> videoStatus(@Body String body);

}
