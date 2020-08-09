package com.example.demotrackerapp;

import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PathBuildHelper {
    private Context context;
    private GoogleMap mMap;
    private List<LatLng> listPoints;

    public PathBuildHelper(Context context, GoogleMap mMap, List<LatLng> listPoints) {
        this.context = context;
        this.mMap = mMap;
        this.listPoints = listPoints;
    }

    public void buildPath() {
        if (listPoints.size() == 2) {
            String url = getRequestUrl(listPoints.get(0), listPoints.get(1));
            TaskRequestDirections taskRequestDirections = new TaskRequestDirections();
            taskRequestDirections.execute(url);
        }
    }

    private String getRequestUrl(LatLng origin, LatLng dest) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=driving";
        String key = "key=" + context.getResources().getString(R.string.google_maps_key);
        String param = str_origin + "&" + str_dest + "&" + mode + "&" + key;
        String output = "json";
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + param;
    }

    private String requestDirection(String reqUrl) throws IOException {
        String responseString = "";
        InputStream inputStream = null;
        HttpURLConnection httpURLConnection = null;
        try {
            URL url = new URL(reqUrl);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.connect();

            inputStream = httpURLConnection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuffer stringBuffer = new StringBuffer();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            responseString = stringBuffer.toString();
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
        return responseString;
    }


    public class TaskRequestDirections extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            String responseString = "";
            try {
                responseString = requestDirection(strings[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            TaskParser taskParser = new TaskParser();
            taskParser.execute(s);

        }
    }

    public class TaskParser extends AsyncTask<String, Void, List<LatLng>> {
        @Override
        protected List<LatLng> doInBackground(String... strings) {
            JSONObject jsonObject;
            List<LatLng> path = new ArrayList<>();
            try {
                jsonObject = new JSONObject(strings[0]);
                DirectionsParser parser = new DirectionsParser();
                path = parser.parseOverviewPolyline(jsonObject);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return path;
        }

        @Override
        protected void onPostExecute(List<LatLng> path) {
            PolylineOptions polylineOptions = new PolylineOptions();
            polylineOptions.addAll(path);
            polylineOptions.width(12);
            polylineOptions.color(Color.BLUE);
            polylineOptions.geodesic(true);
            mMap.addPolyline(polylineOptions);
        }
    }
}
