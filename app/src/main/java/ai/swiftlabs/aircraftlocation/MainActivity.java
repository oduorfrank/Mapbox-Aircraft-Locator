package ai.swiftlabs.aircraftlocation;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.model.Marker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.annotation.Symbol;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions;
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions;
import com.mapbox.mapboxsdk.utils.BitmapUtils;
import com.mapbox.mapboxsdk.utils.ColorUtils;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.flightcontroller.FlightControllerState;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import timber.log.Timber;

/**
 * Activity showcasing adding symbols using the annotation plugin
 */
public class MainActivity extends FragmentActivity implements View.OnClickListener, MapboxMap.OnMapClickListener {
    protected static final String TAG = "MainActivity";
    private static final String ID_ICON_AIRPORT = "airport";
    private static final String MAKI_ICON_MARKER = "castle-15";

    private double droneLocationLat = 181, droneLocationLng = 181;
    private FlightController mFlightController;

    private MapView mapView;
    private SymbolManager symbolManager;
    private Symbol symbol;
    private MapboxMap mapboxMap;

    private boolean isAdd = false;

    private final Map<Integer, Symbol> mSymbols = new ConcurrentHashMap<Integer, Symbol>();

    private Button add, clear;
    private Button config, upload, start, stop;

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };
    private Symbol mWayPointSymbols;

    private void onProductConnectionChange() {
        initFlightController();
    }

    private void initFlightController() {
        BaseProduct product = DJIDemoApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    updateDroneLocation();
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mapbox access token is configured here.
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_main);

        //Register BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);


        initUI();

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(mapboxMap -> mapboxMap.setStyle(Style.LIGHT, style -> {
            this.mapboxMap = mapboxMap;

            mapboxMap.moveCamera(CameraUpdateFactory.zoomTo(10));

            mapboxMap.addOnMapClickListener(MainActivity.this); // add listener for click for map object

            addAirplaneImageToStyle(style);

            initFloatingActionButtonClickListeners();

            // create symbol manager
            GeoJsonOptions geoJsonOptions = new GeoJsonOptions().withTolerance(0.4f);
            symbolManager = new SymbolManager(mapView, mapboxMap, style, null, geoJsonOptions);

            // set non data driven properties
            symbolManager.setIconAllowOverlap(true);
            symbolManager.setTextAllowOverlap(true);
        }));
    }

    private void initUI() {
        add = (Button) findViewById(R.id.add);
//        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        upload = (Button) findViewById(R.id.upload);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);

        add.setOnClickListener(this);
//        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
    }

    private void initFloatingActionButtonClickListeners() {
        FloatingActionButton locateAircraftFab = findViewById(R.id.locate);
        locateAircraftFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's location
            }
        });

    }

    private void addAirplaneImageToStyle(Style style) {
        style.addImage(ID_ICON_AIRPORT,
                Objects.requireNonNull(BitmapUtils.getBitmapFromDrawable(getResources().getDrawable(R.drawable.ic_airplanemode))),
                true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (symbolManager != null) {
            symbolManager.onDestroy();
        }

        unregisterReceiver(mReceiver);
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate();
                break;
            }
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.add:{
                enableDisableAdd();
                break;
            }
            default:
                break;
        }
    }

    private void enableDisableAdd() {
        if (!isAdd) {
            isAdd = true;
            add.setText("Exit");
        }else{
            isAdd = false;
            add.setText("Add");
        }
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                Log.d(TAG, "Select Speed finish");
            }
        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                Log.d(TAG, "Select action action");
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                Log.d(TAG, "Select heading finish");
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }

    private void cameraUpdate() {
        CameraPosition position = new CameraPosition.Builder()
                .target(new LatLng(droneLocationLat, droneLocationLng))
                .zoom(10)
                .tilt(20)
                .build();

        mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 4000);
    }

    private void updateDroneLocation() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (symbol != null)
                    symbolManager.delete(symbol);
                if (checkGpsCoordinates(droneLocationLat, droneLocationLng)) {
                    SymbolOptions symbolOptions = new SymbolOptions()
                            .withLatLng(new LatLng(droneLocationLat, droneLocationLng))
                            .withIconImage(ID_ICON_AIRPORT)
                            .withIconSize(1.3f)
                            .withSymbolSortKey(10.0f);
                    symbol = symbolManager.create(symbolOptions);
                    Timber.e(symbol.toString());
                }
            }
        });
    }

    private boolean checkGpsCoordinates(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);    }

    /**
     * @Description : RETURN BTN RESPONSE FUNCTION
     */
    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    @Override
    public boolean onMapClick(@NonNull @NotNull LatLng point) {
        if (isAdd){
            markWaypoint(point);
        }else{
            setResultToToast("Cannot add waypoint");
        }
        return false;
    }

    private void markWaypoint(LatLng point) {
        // create waypoint symbols
        SymbolOptions wayPointsOptions = new SymbolOptions()
                .withLatLng(point)
                .withIconImage(MAKI_ICON_MARKER)
                .withIconColor(ColorUtils.colorToRgbaString(Color.BLUE))
                .withIconSize(1.3f)
                .withSymbolSortKey(5.0f)
                .withDraggable(true);
        mWayPointSymbols = symbolManager.create(wayPointsOptions);
        mSymbols.put(mSymbols.size(), mWayPointSymbols);
    }

    private void setResultToToast(final String string){
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }
}