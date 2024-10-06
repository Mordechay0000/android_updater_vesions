package com.mordechay.androidCustomUpdater;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiEvent {
    @GET("version")
    Call<Structure> getVersionInfo();
}