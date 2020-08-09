package com.example.demotrackerapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

public class MapTrackerActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private static final int CHECK_SETTINGS_CODE = 111;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;

    private View mapView;
    private TextView currentLocationTextView;
    private TextView startTextView;
    private TextView destinationTextView;
    private TextView markPointTextView;
    private LinearLayout currentLocationLinearLayout;
    private LinearLayout startLocationLinearLayout;
    private LinearLayout destinationLocationLinearLayout;
    private Button buildRouteButton;

    private Location lastKnownLocation;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private SettingsClient settingsClient;

    private List<LatLng> listPoints;
    private boolean isButtonReady = false;
    private final float DEFAULT_ZOOM = 18;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_tracker);

        listPoints = new ArrayList<>();

        currentLocationLinearLayout = findViewById(R.id.ll_current_location);
        startLocationLinearLayout = findViewById(R.id.ll_start_location);
        destinationLocationLinearLayout = findViewById(R.id.ll_dest_location);

        currentLocationTextView = findViewById(R.id.tv_your_location_cords);
        startTextView = findViewById(R.id.tv_origin_cords);
        destinationTextView = findViewById(R.id.tv_destination_cords);
        markPointTextView = findViewById(R.id.tv_mark_points);
        buildRouteButton = findViewById(R.id.btn_build_route);
        buildRouteButton.setEnabled(false);
        buildRouteButton.setBackgroundColor(Color.GRAY);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mapView = mapFragment.getView();

        buildLocationRequest();
        buildLocationSettingsRequest();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);

        if (mapView != null && mapView.findViewById(Integer.parseInt("1")) != null) {
            View locationButton = ((View) mapView.findViewById(Integer.parseInt("1")).getParent())
                    .findViewById(Integer.parseInt("2"));
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            layoutParams.setMargins(0, 0, 40, 200);
        }

        checkLocationSettings();
        mMap.setOnMapLongClickListener(this);
        buildRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                markPointTextView.setText(R.string.mark_new_points);
                markPointTextView.setTextColor(ContextCompat.getColor(MapTrackerActivity.this, R.color.markPointsTextSuccessColor));
                PathBuildHelper pathBuildHelper = new PathBuildHelper(MapTrackerActivity.this, mMap, listPoints);
                pathBuildHelper.buildPath();
                buildRouteButton.setEnabled(false);
                buildRouteButton.setBackgroundColor(Color.GRAY);
            }
        });
    }

    private void checkLocationSettings() {
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(locationSettingsRequest);
        task.addOnSuccessListener(MapTrackerActivity.this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                getDeviceLocation();
            }
        });
        task.addOnFailureListener(MapTrackerActivity.this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                    try {
                        resolvableApiException.startResolutionForResult(MapTrackerActivity.this, CHECK_SETTINGS_CODE);
                    } catch (IntentSender.SendIntentException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHECK_SETTINGS_CODE) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    Log.d("MapTrackerActivity", "User has agreed to change location settings");
                    getDeviceLocation();
                    break;
                case Activity.RESULT_CANCELED:
                    Log.d("MapTrackerActivity", "User has not agreed to change location settings");
                    break;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void getDeviceLocation() {
        fusedLocationProviderClient.getLastLocation()
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) {
                                moveCameraToLastKnownLocationWithZoom(DEFAULT_ZOOM);
                                showLocationCoordinates(currentLocationTextView, currentLocationLinearLayout,
                                        lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                startLocationLinearLayout.setVisibility(View.INVISIBLE);
                                destinationLocationLinearLayout.setVisibility(View.INVISIBLE);
                                markPointTextView.setVisibility(View.VISIBLE);
                                markPointTextView.setText(R.string.mark_two_points);
                                markPointTextView.setTextColor(ContextCompat.getColor(MapTrackerActivity.this, R.color.markPointsTextColor));
                            } else {
                                buildLocationRequest();
                                buildLocationCallBackWithMovingCameraToLastKnownLocationWithZoom();
                                fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                            }
                        } else {
                            Toast.makeText(MapTrackerActivity.this, "Unable to get last location", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void showLocationCoordinates(TextView textView, ViewGroup viewGroup, Double latitude, Double longitude) {
        String coords = latitude + "; " + longitude;
        textView.setText(coords);
        viewGroup.setVisibility(View.VISIBLE);
    }

    private void moveCameraToLastKnownLocationWithZoom(float zoom) {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()), zoom));
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();
    }

    private void buildLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationCallBackWithMovingCameraToLastKnownLocationWithZoom() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                lastKnownLocation = locationResult.getLastLocation();
                moveCameraToLastKnownLocationWithZoom(DEFAULT_ZOOM);
                showLocationCoordinates(currentLocationTextView, currentLocationLinearLayout,
                        lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                startLocationLinearLayout.setVisibility(View.INVISIBLE);
                destinationLocationLinearLayout.setVisibility(View.INVISIBLE);
                markPointTextView.setVisibility(View.VISIBLE);
                markPointTextView.setText(R.string.mark_two_points);
                fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            }
        };
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        if (listPoints.size() == 2) {
            listPoints.clear();
            mMap.clear();
            currentLocationLinearLayout.setVisibility(View.VISIBLE);
            markPointTextView.setVisibility(View.VISIBLE);
            markPointTextView.setText(R.string.mark_two_points);
            isButtonReady = false;
        }
        listPoints.add(latLng);
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        if (listPoints.size() == 1) {
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            showLocationCoordinates(startTextView, startLocationLinearLayout, latLng.latitude, latLng.longitude);
            currentLocationLinearLayout.setVisibility(View.INVISIBLE);
            destinationLocationLinearLayout.setVisibility(View.INVISIBLE);
            markPointTextView.setText(R.string.mark_one_points);
            markPointTextView.setTextColor(ContextCompat.getColor(MapTrackerActivity.this, R.color.markPointsTextColor));
        } else {
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            showLocationCoordinates(destinationTextView, destinationLocationLinearLayout, latLng.latitude, latLng.longitude);
            currentLocationLinearLayout.setVisibility(View.INVISIBLE);
            markPointTextView.setText(R.string.push_build_route_button);
            isButtonReady = true;
        }
        mMap.addMarker(markerOptions);
        if (listPoints.size() == 2) {
            buildRouteButton.setEnabled(true);
            buildRouteButton.setBackgroundColor(ContextCompat.getColor(MapTrackerActivity.this, R.color.colorPrimary));
        }
    }
}