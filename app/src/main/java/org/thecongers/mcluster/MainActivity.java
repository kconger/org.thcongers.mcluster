/*
Copyright (C) 2014 Keith Conger <keith.conger@gmail.com>

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.thecongers.mcluster;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends FragmentActivity {

    private View root;
    private LinearLayout layoutIcons;
    private LinearLayout layoutMiddleLeft;
    private LinearLayout layoutMiddleRight;
    private LinearLayout layoutBottomLeft;
    private LinearLayout layoutBottomRight;
    private ImageView imageKillSwitch;
    private ImageView imageLeftArrow;
    private ImageView imageRightArrow;
    private ImageView imageHighBeam;
    private ImageView imageHeatedGrips;
    private ImageView imageABS;
    private ImageView imageFuelWarning;
    private ImageView imageFuelLevel;
    private ImageView imageESA;
    private TextView txtInfo;
    private TextView txtFrontTPMS;
    private TextView txtRearTPMS;
    private TextView txtSpeed;
    private TextView txtSpeedUnit;
    private TextView txtGear;
    private TextView txtOdometers;
    private TextView txtESA;
    private ImageButton imageButtonTPMS;
    private ImageButton imageButtonBluetooth;
    private ImageButton imageButtonPreference;
    private ProgressBar progressFuelLevel;
    private int background;
    private int backgroundDark;
    private SharedPreferences sharedPrefs;
    private BluetoothAdapter btAdapter = null;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "mCluster";
    private final int RECEIVE_MESSAGE = 1;
    private static final int SETTINGS_RESULT = 1;
    private String address;
    static boolean hasSensor = false;
    private boolean itsDark = false;
    boolean btnPressed = false;
    private boolean fuelAlertTriggered = false;
    private boolean fuelReserveAlertTriggered = true;
    private long alertTimer = 0;
    private long darkTimer = 0;
    private long lightTimer = 0;
    String lastStatus;
    private int lastDirection;
    private int frontPressurePSI = 0;
    private int rearPressurePSI = 0;
    private int frontStatus = 0;
    private int rearStatus = 3;
    private LogData logger = null;
    private static Handler canBusMessages;
    private static Handler sensorMessages;
    private canBusConnectThread canBusConnectThread;
    private iTPMSConnectThread iTPMSConnectThread;
    private TextToSpeech text2speech;
    /*
    * time smoothing constant for low-pass filter
    * 0 ≤ alpha ≤ 1 ; a smaller value basically means more smoothing
    * was 0.15f
    * See: http://en.wikipedia.org/wiki/Low-pass_filter#Discrete-time_realization
    */
    static final float ALPHA = 0.05f;
    float[] gravity = new float[3];
    float[] geomagnetic = new float[3];

    // Setup TTS
    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            if (text2speech.isLanguageAvailable(Locale.US) == TextToSpeech.LANG_AVAILABLE)
                text2speech.setLanguage(Locale.US);
        } else if (initStatus == TextToSpeech.ERROR) {
            Log.d(TAG, "Text to Speech startup failed...");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        View myView = findViewById(R.id.layoutApp);
        root = myView.getRootView();
        layoutIcons = (LinearLayout) findViewById(R.id.layoutIcons);
        layoutMiddleLeft = (LinearLayout) findViewById(R.id.layoutMiddleLeft);
        layoutMiddleRight = (LinearLayout) findViewById(R.id.layoutMiddleRight);
        layoutBottomLeft = (LinearLayout) findViewById(R.id.layoutBottomLeft);
        layoutBottomRight = (LinearLayout) findViewById(R.id.layoutBottomRight);
        imageKillSwitch = (ImageView) findViewById(R.id.imageViewKillSwitch);
        imageLeftArrow = (ImageView) findViewById(R.id.imageViewLeftArrow);
        imageRightArrow = (ImageView) findViewById(R.id.imageViewRightArrow);
        imageHighBeam = (ImageView) findViewById(R.id.imageViewHighBeam);
        imageHeatedGrips = (ImageView) findViewById(R.id.imageViewHeatedGrips);
        imageABS = (ImageView) findViewById(R.id.imageViewABS);
        imageFuelWarning = (ImageView) findViewById(R.id.imageViewFuelWarning);
        imageFuelLevel = (ImageView) findViewById(R.id.imageViewFuelLevel);
        imageESA = (ImageView) findViewById(R.id.imageViewESA);
        txtSpeed = (TextView) findViewById(R.id.textViewSpeed);
        txtSpeedUnit = (TextView) findViewById(R.id.textViewSpeedUnit);
        txtGear = (TextView) findViewById(R.id.textViewGear);
        txtOdometers = (TextView) findViewById(R.id.textViewOdometer);
        txtESA = (TextView) findViewById(R.id.textViewESA);
        imageButtonTPMS = (ImageButton) findViewById(R.id.imageButtonTPMS);
        imageButtonBluetooth = (ImageButton) findViewById(R.id.imageButtonBluetooth);
        imageButtonPreference = (ImageButton) findViewById(R.id.imageButtonPreference);
        progressFuelLevel = (ProgressBar) findViewById(R.id.progressBarFuelLevel);

        // Backgrounds
        background = R.drawable.rectangle_bordered;
        backgroundDark = R.drawable.rectangle_bordered_dark;

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Update layout
        updateLayout();

        if (!sharedPrefs.getBoolean("prefEnableTPMS", false)) {
            imageButtonTPMS.setImageResource(R.mipmap.blank_icon);
            imageButtonTPMS.setEnabled(false);
        } else {
            imageButtonTPMS.setImageResource(R.mipmap.tpms_alert);
            imageButtonTPMS.setEnabled(true);
        }

        // Set initial color scheme
        updateColors();
        if (sharedPrefs.getBoolean("prefNightMode", false)) {
            imageHeatedGrips.setImageResource(R.mipmap.heated_grips_high_dark);
        }

        // Watch for Bluetooth Changes
        IntentFilter filter1 = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        IntentFilter filter3 = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(btReceiver, filter1);
        this.registerReceiver(btReceiver, filter2);
        this.registerReceiver(btReceiver, filter3);

        // Setup Text To Speech
        text2speech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    text2speech.setLanguage(Locale.US);
                }
            }
        });

        imageButtonBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                canBusConnect();
            }
        });

        imageButtonTPMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sharedPrefs.getBoolean("prefEnableTPMS", false)) {
                    iTPMSConnect();
                }
            }
        });

        imageButtonPreference.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(MainActivity.this, v);
                popup.getMenuInflater().inflate(R.menu.main, popup.getMenu());
                popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_settings:
                                // Settings Menu was selected
                                Intent i = new Intent(getApplicationContext(), org.thecongers.mcluster.UserSettingActivity.class);
                                startActivityForResult(i, SETTINGS_RESULT);
                                return true;
                            case R.id.action_about:
                                // About was selected
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                builder.setTitle(getResources().getString(R.string.alert_about_title));
                                builder.setMessage(readRawTextFile(MainActivity.this, R.raw.about));
                                builder.setPositiveButton(getResources().getString(R.string.alert_about_button), null);
                                builder.show();
                                return true;
                            case R.id.action_exit:
                                // Exit menu item was selected
                                if (logger != null) {
                                    logger.shutdown();
                                }
                                finish();
                                System.exit(0);
                            default:
                                return true;
                        }
                    }
                });

                popup.show();
            }
        });

        layoutMiddleRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int infoViewCurr = Integer.valueOf(sharedPrefs.getString("prefInfoView", "0"));
                if (infoViewCurr < 2) {
                    infoViewCurr = infoViewCurr + 1;
                } else {
                    infoViewCurr = 0;
                }
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString("prefInfoView", String.valueOf(infoViewCurr));
                editor.commit();

                //update layout
                updateLayout();
            }
        });

        canBusMessages = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RECEIVE_MESSAGE:
                        // Check to see if message is the correct size
                        if (msg.arg1 == 27) {
                            byte[] readBuf = (byte[]) msg.obj;
                            String message = new String(readBuf);

                            //Log.d(TAG, "CANBus MSG: " + message);
                            if (!sharedPrefs.getBoolean("prefDataLogging", false) && (logger != null)) {
                                logger.write("CANBus MSG: " + message);
                            }

                            //Default Units
                            String speedUnit = "km/h";
                            String odometerUnit = "km";
                            String temperatureUnit = "C";

                            String[] splitMessage = message.split(",");
                            if (splitMessage[0].contains("10C")) {

                                //RPM
                                if (sharedPrefs.getString("prefInfoView", "0").contains("0")) {
                                    int rpm = (Integer.parseInt(splitMessage[4], 16) * 255 + Integer.parseInt(splitMessage[3], 16)) / 4;
                                    txtInfo = (TextView) findViewById(R.id.textViewInfo);
                                    txtInfo.setGravity(Gravity.CENTER | Gravity.BOTTOM);
                                    txtInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
                                    txtInfo.setText(Integer.toString(rpm) + " RPM");
                                    if (rpm > 8500) {
                                        txtInfo.setTextColor(getResources().getColor(R.color.red));
                                    }
                                }

                                //Kill Switch
                                String killSwitchValue = splitMessage[5].substring(1);
                                if (killSwitchValue.contains("5") || killSwitchValue.contains("9")){
                                    //Kill Switch On
                                    imageKillSwitch.setImageResource(R.mipmap.kill_switch);
                                }else{
                                    //Kill Switch Off
                                    imageKillSwitch.setImageResource(R.mipmap.blank_icon);
                                }

                            }else if (splitMessage[0].contains("130")){
                                //Turn indicators
                                String indicatorValue = splitMessage[8];

                                if (indicatorValue.contains("D7")){
                                    imageLeftArrow.setImageResource(R.mipmap.left_arrow);
                                    imageRightArrow.setImageResource(R.mipmap.blank_icon);
                                }else if (indicatorValue.contains("E7")){
                                    imageLeftArrow.setImageResource(R.mipmap.blank_icon);
                                    imageRightArrow.setImageResource(R.mipmap.right_arrow);
                                }else if (indicatorValue.contains("EF")){
                                    imageLeftArrow.setImageResource(R.mipmap.left_arrow);
                                    imageRightArrow.setImageResource(R.mipmap.right_arrow);
                                }else{
                                    imageLeftArrow.setImageResource(R.mipmap.blank_icon);
                                    imageRightArrow.setImageResource(R.mipmap.blank_icon);
                                }

                                //High Beam
                                String highBeamValue = splitMessage[7].substring(1);
                                if (highBeamValue.contains("9")){
                                    //High Beam On
                                    imageHighBeam.setImageResource(R.mipmap.high_beam);
                                }else{
                                    //High Beam Off
                                    imageHighBeam.setImageResource(R.mipmap.blank_icon);
                                }

                            }else if (splitMessage[0].contains("294")){
                                //Front Wheel Speed
                                double frontSpeed = ((Integer.parseInt(splitMessage[4], 16) * 256.0 + Integer.parseInt(splitMessage[3], 16)) * 0.062);
                                //If 21" Wheel
                                if (sharedPrefs.getString("prefDistance", "0").contains("1")) {
                                    //TODO: Adjust factor for 21 inch wheel
                                    frontSpeed = ((Integer.parseInt(splitMessage[4], 16) * 256.0 + Integer.parseInt(splitMessage[3], 16)) * 0.062);
                                }
                                if (sharedPrefs.getString("prefDistance", "0").contains("0")) {
                                    speedUnit = "MPH";
                                    frontSpeed = frontSpeed / 1.609344;
                                }
                                txtSpeed.setText(String.valueOf((int) Math.round(frontSpeed)));
                                txtSpeedUnit.setText(speedUnit);

                                //ABS
                                String absValue = splitMessage[2].substring(0,1);
                                if (absValue.contains("B")){
                                    //ABS Off
                                    imageABS.setImageResource(R.mipmap.abs);
                                }else {
                                    //ABS On
                                    imageABS.setImageResource(R.mipmap.blank_icon);
                                }

                            }else if (splitMessage[0].contains("2BC")){
                                //TODO: Calibration and display
                                // Oil Temperature
                                double oilTemp = Integer.parseInt(splitMessage[3], 16 ) - 48.0;
                                if (sharedPrefs.getString("prefTempFormat", "0").contains("0")) {
                                    // F
                                    oilTemp = (9.0 / 5.0) * oilTemp + 32.0;
                                    temperatureUnit = "F";
                                }
                                //Log.d(TAG,oilTemp + temperatureUnit);

                                // Gear
                                String gearValue = splitMessage[6].substring(0,1);
                                String gear;
                                if (gearValue.contains("1")){
                                    gear = "1";
                                }else if (gearValue.contains("2")){
                                    gear = "N";
                                }else if (gearValue.contains("4")){
                                    gear = "2";
                                }else if (gearValue.contains("7")){
                                    gear = "3";
                                }else if (gearValue.contains("8")){
                                    gear = "4";
                                }else if (gearValue.contains("B")){
                                    gear = "5";
                                }else if (gearValue.contains("D")){
                                    gear = "6";
                                } else {
                                    gear = "-";
                                }
                                txtGear.setText(gear);

                                //TODO: Calibration and display
                                // Air Intake Temperature
                                double airTemp = (Integer.parseInt(splitMessage[8], 16 ) - 80.0);
                                if (sharedPrefs.getString("prefTempFormat", "0").contains("0")) {
                                    // F
                                    airTemp = (9.0 / 5.0) * airTemp + 32.0;
                                    temperatureUnit = "F";
                                }
                                //Log.d(TAG,airTemp + temperatureUnit);


                            }else if (splitMessage[0].contains("2D0")){
                                //Info Button
                                String infoButtonValue = splitMessage[6].substring(1);
                                if (infoButtonValue.contains("5")){
                                    //Short Press
                                    if (!btnPressed) {
                                        int infoButton = Integer.valueOf(sharedPrefs.getString("prefInfoView", "0"));
                                        if (infoButton < 2) {
                                            infoButton = infoButton + 1;
                                        } else {
                                            infoButton = 0;
                                        }
                                        SharedPreferences.Editor editor = sharedPrefs.edit();
                                        editor.putString("prefInfoView", String.valueOf(infoButton));
                                        editor.commit();

                                        //update layout
                                        updateLayout();
                                        btnPressed = true;
                                    }
                                }else if (infoButtonValue.contains("6")){
                                    //Long Press
                                }else{
                                    btnPressed = false;
                                }

                                //Heated Grips
                                String heatedGripSwitchValue = splitMessage[8].substring(0,1);
                                if (heatedGripSwitchValue.contains("C")){
                                    imageHeatedGrips.setImageResource(R.mipmap.blank_icon);
                                }else if (heatedGripSwitchValue.contains("D")){
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageHeatedGrips.setImageResource(R.mipmap.heated_grips_low);
                                    } else {
                                        imageHeatedGrips.setImageResource(R.mipmap.heated_grips_low_dark);
                                    }
                                }else if (heatedGripSwitchValue.contains("E")){
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageHeatedGrips.setImageResource(R.mipmap.heated_grips_high);
                                    } else {
                                        imageHeatedGrips.setImageResource(R.mipmap.heated_grips_high_dark);
                                    }
                                }else{
                                    imageHeatedGrips.setImageResource(R.mipmap.blank_icon);
                                }

                                //ESA Damping and Preload
                                String esaDampingValue1 = splitMessage[5].substring(1);
                                String esaDampingValue2 = splitMessage[8].substring(1);
                                String esaPreLoadValue = splitMessage[5].substring(0,1);
                                if (esaDampingValue1.contains("B") && esaDampingValue2.contains("1")){
                                    txtESA.setText("SOFT");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("2")){
                                    txtESA.setText("NORM");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("3")){
                                    txtESA.setText("HARD");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("4")){
                                    txtESA.setText("SOFT");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("5")){
                                    txtESA.setText("NORM");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("B") && esaDampingValue2.contains("6")){
                                    txtESA.setText("HARD");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("1")){
                                    txtESA.setText("SOFT");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("2")){
                                    txtESA.setText("NORM");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("3")){
                                    txtESA.setText("HARD");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.smooth_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("4")){
                                    txtESA.setText("SOFT");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("5")){
                                    txtESA.setText("NORM");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain_dark);
                                    }
                                } else if (esaDampingValue1.contains("7") && esaDampingValue2.contains("6")){
                                    txtESA.setText("HARD");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.uneven_terrain_dark);
                                    }
                                } else if (esaPreLoadValue.contains("1")){
                                    txtESA.setText("COMF");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_dark);
                                    }
                                } else if (esaPreLoadValue.contains("2")){
                                    txtESA.setText("NORM");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_dark);
                                    }
                                } else if (esaPreLoadValue.contains("3")){
                                    txtESA.setText("SPORT");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_dark);
                                    }
                                } else if (esaPreLoadValue.contains("4")){
                                    txtESA.setText("COMF");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet_luggage);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_luggage_dark);
                                    }
                                } else if (esaPreLoadValue.contains("5")){
                                    txtESA.setText("NORM");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet_luggage);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_luggage_dark);
                                    }
                                } else if (esaPreLoadValue.contains("6")){
                                    txtESA.setText("SPORT");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet_luggage);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_luggage_dark);
                                    }
                                } else if (esaPreLoadValue.contains("7")){
                                    txtESA.setText("COMF");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet_helmet);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_helmet_dark);
                                    }
                                } else if (esaPreLoadValue.contains("8")){
                                    txtESA.setText("NORM");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet_helmet);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_helmet_dark);
                                    }
                                } else if (esaPreLoadValue.contains("9")){
                                    txtESA.setText("SPORT");
                                    if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                                        imageESA.setImageResource(R.mipmap.helmet_helmet);
                                    } else {
                                        imageESA.setImageResource(R.mipmap.helmet_helmet_dark);
                                    }
                                } else {
                                    txtESA.setText("");
                                    imageESA.setImageResource(R.mipmap.blank_icon);
                                }

                                //Fuel Level
                                double fuelLevelPercent = ((Integer.parseInt(splitMessage[4], 16) - 29) / 226.0) * 100.0;
                                progressFuelLevel.setProgress((int) Math.round(fuelLevelPercent));
                                //TODO: Remove
                                Log.d(TAG,"Fuel: " + Integer.parseInt(splitMessage[4], 16));

                                //Fuel Level Warning
                                double fuelWarning = sharedPrefs.getInt("prefFuelWarning", 30);
                                if (fuelLevelPercent > fuelWarning) {
                                    imageFuelWarning.setImageResource(R.mipmap.blank_icon);
                                    fuelAlertTriggered = false;
                                    fuelReserveAlertTriggered = false;
                                } else if (fuelLevelPercent == 0){
                                    if (!fuelReserveAlertTriggered) {
                                        //Audio Warning
                                        speakString(getResources().getString(R.string.fuel_alert_reserve));
                                        fuelReserveAlertTriggered = true;
                                    }
                                } else {
                                    //Visual Warning
                                    imageFuelWarning.setImageResource(R.mipmap.fuel_warning);
                                    if (!fuelAlertTriggered){
                                        fuelAlertTriggered = true;
                                        //Audio Warning
                                        String fuelAlert = getResources().getString(R.string.fuel_alert_begin) + String.valueOf((int) Math.round(fuelLevelPercent)) + getResources().getString(R.string.fuel_alert_end);
                                        speakString(fuelAlert);
                                        //Suggest nearby fuel stations
                                        if (!sharedPrefs.getString("prefFuelStation", "0").contains("0")) {
                                            // Display prompt to open google maps
                                            MainActivity.this.runOnUiThread(new Runnable() {

                                                @Override
                                                public void run() {
                                                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                                    builder.setTitle(getResources().getString(R.string.alert_fuel_stations_title));
                                                    if (sharedPrefs.getString("prefFuelStation", "0").contains("1")) {
                                                        // Search for fuel stations nearby
                                                        builder.setMessage(getResources().getString(R.string.alert_fuel_stations_message_suggestions));
                                                    } else if (sharedPrefs.getString("prefFuelStation", "0").contains("2")) {
                                                        // Route to nearest fuel station
                                                        builder.setMessage(getResources().getString(R.string.alert_fuel_stations_message_navigation));
                                                    }
                                                    builder.setPositiveButton(getResources().getString(R.string.alert_fuel_stations_button_positive), new DialogInterface.OnClickListener() {
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            Uri gmmIntentUri = null;
                                                            if (sharedPrefs.getString("prefFuelStation", "0").contains("1")) {
                                                                // Search for fuel stations nearby
                                                                gmmIntentUri = Uri.parse("geo:0,0?q=gas+station");
                                                            } else if (sharedPrefs.getString("prefFuelStation", "0").contains("2")) {
                                                                // Route to nearest fuel station
                                                                gmmIntentUri = Uri.parse("google.navigation:q=gas+station");
                                                            }
                                                            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                                                            mapIntent.setPackage("com.google.android.apps.maps");
                                                            startActivity(mapIntent);
                                                        }
                                                    });
                                                    builder.setNegativeButton(getResources().getString(R.string.alert_fuel_stations_button_negative), new DialogInterface.OnClickListener() {
                                                        public void onClick(DialogInterface dialog, int id) {
                                                            dialog.cancel();
                                                        }
                                                    });
                                                    builder.show();
                                                }
                                            });
                                        }
                                    }
                                }
                            }else if (splitMessage[0].contains("3F8")){
                                String odometerValue = "";
                                for(int i=4;i>1;i--){
                                    odometerValue = odometerValue + splitMessage[i];
                                }
                                double odometer = Integer.parseInt(odometerValue, 16 );
                                if (sharedPrefs.getString("prefDistance", "0").contains("0")) {
                                    odometerUnit = "Miles";
                                    odometer = odometer * 0.6214;
                                }
                                txtOdometers.setText(String.valueOf((int) Math.round(odometer)) + " " + odometerUnit);
                            }
                            imageButtonBluetooth.setImageResource(R.mipmap.bluetooth_on);
                        } else {
                            Log.d(TAG, "Malformed message, message length: " + msg.arg1);
                        }
                        break;
                }
            }
        };

        sensorMessages = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RECEIVE_MESSAGE:
                        // Message received
                        Log.d(TAG, "iTPMS Message Received, Length: " + msg.arg1);
                        // Check to see if message is the correct size
                        if (msg.arg1 == 13) {
                            byte[] readBuf = (byte[]) msg.obj;

                            // Validate against checksum
                            int calculatedCheckSum = readBuf[4] + readBuf[5] + readBuf[6] + readBuf[7] + readBuf[8] + readBuf[9] + readBuf[10];
                            if (calculatedCheckSum == readBuf[11]){
                                // Convert to hex
                                String[] hexData = new String[13];
                                StringBuilder sbhex = new StringBuilder();
                                for (int i = 0; i < msg.arg1; i++) {
                                    hexData[i] = String.format("%02X", readBuf[i]);
                                    sbhex.append(hexData[i]);
                                }

                                // Get sensor position
                                String position = hexData[3];
                                // Get sensor ID
                                StringBuilder sensorID = new StringBuilder();
                                sensorID.append(hexData[4]);
                                sensorID.append(hexData[5]);
                                sensorID.append(hexData[6]);
                                sensorID.append(hexData[7]);

                                // Only parse message if there is one or more sensor mappings
                                String prefFrontID = sharedPrefs.getString("prefFrontID", "");
                                String prefRearID = sharedPrefs.getString("prefRearID", "");
                                try {
                                    // Get temperature
                                    int tempC = Integer.parseInt(hexData[8], 16) - 50;
                                    double temp = tempC;
                                    String temperatureUnit = "C";
                                    // Get tire pressure
                                    int psi = Integer.parseInt(hexData[9], 16);
                                    double pressure = psi;
                                    String pressureUnit = "psi";
                                    // Get battery voltage
                                    double voltage = Integer.parseInt(hexData[10], 16) / 50;
                                    // Get pressure thresholds
                                    int lowPressure = Integer.parseInt(sharedPrefs.getString("prefLowPressure", "30"));
                                    int highPressure = Integer.parseInt(sharedPrefs.getString("prefHighPressure", "46"));
                                    if (sharedPrefs.getString("preftempf", "0").contains("0")) {
                                        // F
                                        temp = (9.0 / 5.0) * tempC + 32.0;
                                        temperatureUnit = "F";
                                    }
                                    int formattedTemperature = (int) (temp + 0.5d);
                                    String pressureFormat = sharedPrefs.getString("prefpressuref", "0");
                                    if (pressureFormat.contains("1")) {
                                        // KPa
                                        pressure = psi * 6.894757293168361;
                                        pressureUnit = "KPa";
                                    } else if (pressureFormat.contains("2")) {
                                        // Kg-f
                                        pressure = psi * 0.070306957965539;
                                        pressureUnit = "Kg-f";
                                    } else if (pressureFormat.contains("3")) {
                                        // Bar
                                        pressure = psi * 0.0689475729;
                                        pressureUnit = "Bar";
                                    }
                                    int formattedPressure = (int) (pressure + 0.5d);
                                    // Get checksum
                                    String checksum = hexData[11];

                                    //Log.d(TAG, "Sensor ID: " + sensorID.toString() + ", Sensor Position: " + position + ", Temperature(" + temperatureUnit + "): " + String.valueOf(temp) + ", Pressure(" + pressureUnit + "): " + String.valueOf(pressure) + ", Voltage: " + String.valueOf(voltage) + ", Checksum: " + checksum + ", Data: " + sbhex.toString() + ", Bytes:" + msg.arg1);
                                    if (!sharedPrefs.getBoolean("prefDataLogging", false) && (logger != null)) {
                                        logger.write("iTPMS MSG: Sensor ID: " + sensorID.toString() + ", Sensor Position: " + position + ", Temperature(" + temperatureUnit + "): " + String.valueOf(temp) + ", Pressure(" + pressureUnit + "): " + String.valueOf(pressure) + ", Voltage: " + String.valueOf(voltage) + ", Checksum: " + checksum + ", Data: " + sbhex.toString() + ", Bytes:" + msg.arg1);
                                    }

                                    if (Integer.parseInt(hexData[3], 16) <= 2) {
                                        Log.d(TAG, "Front ID matched: " + Integer.parseInt(hexData[3], 16));

                                        frontPressurePSI = psi;
                                        // Set front tire status
                                        if (psi <= lowPressure) {
                                            frontStatus = 1;
                                        } else if (psi >= highPressure) {
                                            frontStatus = 2;
                                        } else {
                                            frontStatus = 0;
                                        }
                                        if (sharedPrefs.getString("prefInfoView", "0").contains("2")) {
                                            txtFrontTPMS = (TextView) findViewById(R.id.textViewFrontTPMS);
                                            txtFrontTPMS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
                                            txtFrontTPMS.setText(String.valueOf(formattedPressure) + " " + pressureUnit);
                                        }
                                    } else if (Integer.parseInt(hexData[3], 16) > 2) {
                                        Log.d(TAG, "Rear ID matched: " + Integer.parseInt(hexData[3], 16));

                                        rearPressurePSI = psi;
                                        // Set rear tire status
                                        if (psi <= lowPressure) {
                                            rearStatus = 4;
                                        } else if (psi >= highPressure) {
                                            rearStatus = 5;
                                        } else {
                                            rearStatus = 3;
                                        }
                                        if (sharedPrefs.getString("prefInfoView", "0").contains("2")) {
                                            txtRearTPMS = (TextView) findViewById(R.id.textViewRearTPMS);
                                            txtRearTPMS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
                                            txtRearTPMS.setText(String.valueOf(formattedPressure) + " " + pressureUnit);
                                        }
                                    }
                                    // Reset icon
                                    if ((frontStatus == 0) && (rearStatus == 3)){
                                        imageButtonTPMS.setImageResource(R.mipmap.tpms_on);
                                    }
                                    if ((frontStatus != 0) || (rearStatus != 3)) {
                                        imageButtonTPMS.setImageResource(R.mipmap.tpms_alert);
                                        SharedPreferences.Editor editor = sharedPrefs.edit();
                                        editor.putString("prefInfoView", String.valueOf(2));
                                        editor.commit();

                                        updateLayout();

                                        int delay = (Integer.parseInt(sharedPrefs.getString("prefAudioAlertDelay", "30")) * 1000);
                                        String currStatus = (String.valueOf(frontStatus) + String.valueOf(rearStatus));
                                        if (alertTimer == 0) {
                                            alertTimer = System.currentTimeMillis();
                                        } else {
                                            long currentTime = System.currentTimeMillis();
                                            long duration = (currentTime - alertTimer);
                                            if (!currStatus.equals(lastStatus) || duration >= delay)
                                            {
                                                alertTimer = 0;
                                                if ((frontStatus == 1) && (rearStatus == 3)) {
                                                    speakString(getResources().getString(R.string.alert_lowFrontPressure));
                                                } else if ((frontStatus == 2) && (rearStatus == 3)) {
                                                    speakString(getResources().getString(R.string.alert_highFrontPressure));
                                                } else if ((rearStatus == 4) && (frontStatus == 0)) {
                                                    speakString(getResources().getString(R.string.alert_lowRearPressure));
                                                } else if ((rearStatus == 5) && (frontStatus == 0)) {
                                                    speakString(getResources().getString(R.string.alert_highRearPressure));
                                                } else if ((frontStatus == 1) && (rearStatus == 4)) {
                                                    speakString(getResources().getString(R.string.alert_lowFrontLowRearPressure));
                                                } else if ((frontStatus == 2) && (rearStatus == 5)) {
                                                    speakString(getResources().getString(R.string.alert_highFrontHighRearPressure));
                                                } else if ((frontStatus == 1) && (rearStatus == 5)) {
                                                    speakString(getResources().getString(R.string.alert_lowFrontHighRearPressure));
                                                } else if ((frontStatus == 2) && (rearStatus == 4)) {
                                                    speakString(getResources().getString(R.string.alert_highFrontLowRearPressure));
                                                }
                                                lastStatus = (String.valueOf(frontStatus) + String.valueOf(rearStatus));
                                            }
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                    Log.d(TAG, "Malformed message, unexpected value");
                                }
                            }
                        } else {
                            Log.d(TAG, "Malformed message, message length: " + msg.arg1);
                        }
                        break;
                }
            }
        };

        // Sensor Stuff
        SensorManager sensorManager
                = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Light
        if (lightSensor == null){
            Log.d(TAG,"Light sensor not found");
        }else {
            sensorManager.registerListener(sensorEventListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            hasSensor = true;
        }
        // Compass
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_UI);

        // Try to connect to CANBusGateway
        canBusConnect();
        // Connect to iTPMS if enabled
        if (sharedPrefs.getBoolean("prefEnableTPMS", false)) {
            iTPMSConnect();
        }
    }

    @Override
    protected void onDestroy()
    {
        try {
            unregisterReceiver(btReceiver);
        } catch (IllegalArgumentException e){
            Log.d(TAG, "Receiver not registered");
        }
        if(text2speech !=null){
            text2speech.stop();
            text2speech.shutdown();
        }
        super.onDestroy();
    }

    //Runs when settings are updated
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==SETTINGS_RESULT)
        {
            updateUserSettings();
        }
    }

    // Update UI when settings are updated
    private void updateUserSettings()
    {
        // Shutdown Logger
        if (!sharedPrefs.getBoolean("prefDataLogging", false) && (logger != null)) {
            logger.shutdown();
        }

        // Update Colors
        updateColors();

        //update layout
        updateLayout();
    }

    // Connect to CANBusGateway
    private boolean canBusConnect() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();
        if(btAdapter!=null) {
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            // If there are paired devices
            if (pairedDevices.size() > 0) {
                // Loop through paired devices
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().contains("CANBusGateway")) {
                        address = device.getAddress();
                        Log.d(TAG, "Paired CANBusGateway found: " + device.getName() + " " + device.getAddress());
                    }
                }
                if (address == null) {
                    Toast.makeText(MainActivity.this,
                            getResources().getString(R.string.toast_noPaired),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            if (address != null){
                // Set up a pointer to the remote node using it's address.
                BluetoothDevice device = btAdapter.getRemoteDevice(address);
                canBusConnectThread = new canBusConnectThread(device);
                canBusConnectThread.start();
            } else {
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.toast_noPaired),
                        Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
        Log.d(TAG, "Bluetooth not supported");
        return false;
    }

    // Connect to CANBusGateway
    private boolean iTPMSConnect() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();
        if(btAdapter!=null) {
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            // If there are paired devices
            if (pairedDevices.size() > 0) {
                // Loop through paired devices
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().contains("iTPMS")) {
                        address = device.getAddress();
                        Log.d(TAG, "Paired iTPMS found: " + device.getName() + " " + device.getAddress());
                    }
                }
                if (address == null) {
                    Toast.makeText(MainActivity.this,
                            getResources().getString(R.string.toast_noPairedTPMS),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            if (address != null){
                // Set up a pointer to the remote node using it's address.
                BluetoothDevice device = btAdapter.getRemoteDevice(address);
                iTPMSConnectThread = new iTPMSConnectThread(device);
                iTPMSConnectThread.start();
            } else {
                Toast.makeText(MainActivity.this,
                        getResources().getString(R.string.toast_noPairedTPMS),
                        Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
        Log.d(TAG, "Bluetooth not supported");
        return false;
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    // Listens for Bluetooth broadcasts
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if ((BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) && (device.getName().contains("CANBusGateway"))) {
                // Do something if connected
                Log.d(TAG, "CANBusGateway Connected: ACL");
                canBusConnect();
            }
            else if ((BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) && (device.getName().contains("iTPMS"))) {
                // Do something if connected
                Log.d(TAG, "iTPMS Connected: ACL");
                iTPMSConnect();
            }
            else if ((BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) && (device.getName().contains("CANBusGateway"))) {
                // Do something if disconnected
                Log.d(TAG, "CANBusGateway Disconnected: ACL");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        imageButtonBluetooth.setImageResource(R.mipmap.bluetooth_off);
                    }
                });
            }
            else if ((BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) && (device.getName().contains("iTPMS"))) {
                // Do something if disconnected
                Log.d(TAG, "iTPMS Disconnected: ACL");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        imageButtonTPMS.setImageResource(R.mipmap.tpms_alert);
                    }
                });
            }
        }
    };

    // Check current Bluetooth state
    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        if(btAdapter==null) {
            Log.d(TAG, "Bluetooth not supported");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "Bluetooth is on");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    // Bluetooth Connect Thread
    private class canBusConnectThread extends Thread {
        private final BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        public canBusConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because btSocket is final
            BluetoothSocket tmp = null;
            btDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                tmp = createBluetoothSocket(device);
            } catch (IOException e) {
                Log.d(TAG,"Bluetooth socket create failed: " + e.getMessage() + ".");
            }
            btSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            btAdapter.cancelDiscovery();
            Log.d(TAG, "Connecting to the CANBusGateway...");
            try {
                // Connect the device through the socket. This will block until it succeeds or
                // throws an exception
                btSocket.connect();

                if (btSocket.isConnected()) {
                    Log.d(TAG, "Connected to: " + btDevice.getName() + " " + btDevice.getAddress());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageButtonBluetooth.setImageResource(R.mipmap.bluetooth_on);
                            Toast.makeText(MainActivity.this,
                                    getResources().getString(R.string.toast_connectedTo) +
                                            " " + btDevice.getName() + " " + btDevice.getAddress(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (IOException connectException) {
                // Unable to connect
                Log.d(TAG, "Unable to connect to the CANBusGateway: " + connectException);
                try {
                    btSocket.close();
                } catch (IOException closeException) {
                    Log.d(TAG,"Unable to close socket during connection failure");
                }

                return;
            }

            // Do work to manage the connection (in a separate thread)
            canBusConnectedThread btConnectedThread = new canBusConnectedThread(btSocket);
            btConnectedThread.start();
        }

        // Cancel an in-progress connection, and close the socket
        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "Unable to close Bluetooth socket");
            }
        }
    }

    // Connected bluetooth thread
    private class canBusConnectedThread extends Thread {
        private final InputStream btInStream;

        public canBusConnectedThread(BluetoothSocket socket) {
            InputStream tmpInput = null;

            // Get the input stream, using temp objects because member streams are final
            try {
                tmpInput = socket.getInputStream();
            } catch (IOException e) {
                Log.d(TAG, "IO Exception getting input stream");
            }
            btInStream = tmpInput;
        }

        public void run() {
            int bytesAvailable; // Bytes returned from read()
            final byte delimiter = 59; //This is the ASCII code for a ';'
            int readBufferPosition;
            readBufferPosition = 0;
            byte[] readBuffer = new byte[1024];
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytesAvailable = btInStream.available();
                    if(bytesAvailable > 0){
                        byte[] packetBytes = new byte[bytesAvailable];
                        btInStream.read(packetBytes);
                        for(int i=0;i<bytesAvailable;i++){
                            byte b = packetBytes[i];
                            if(b == delimiter){
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                readBufferPosition = 0;
                                // Send to message queue Handler
                                canBusMessages.obtainMessage(RECEIVE_MESSAGE, encodedBytes.length, -1, encodedBytes).sendToTarget();
                            }else{
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.d(TAG, "IO Exception while reading stream");
                    canBusConnectThread.cancel();
                    canBusConnect();
                    break;
                }
            }
        }
    }

    // Bluetooth iTPMS Connect Thread
    private class iTPMSConnectThread extends Thread {
        private final BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        public iTPMSConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because btSocket is final
            BluetoothSocket tmp = null;
            btDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                tmp = createBluetoothSocket(device);
            } catch (IOException e) {
                Log.d(TAG,"Bluetooth socket create failed: " + e.getMessage() + ".");
            }
            btSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            btAdapter.cancelDiscovery();
            Log.d(TAG, "Connecting to the iTPMS...");
            try {
                // Connect the device through the socket. This will block until it succeeds or
                // throws an exception
                btSocket.connect();

                if (btSocket.isConnected()) {
                    Log.d(TAG, "Connected to: " + btDevice.getName() + " " + btDevice.getAddress());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageButtonTPMS.setImageResource(R.mipmap.tpms_on);
                            Toast.makeText(MainActivity.this,
                                    getResources().getString(R.string.toast_connectedTo) +
                                            " " + btDevice.getName() + " " + btDevice.getAddress(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (IOException connectException) {
                // Unable to connect
                Log.d(TAG, "Unable to connect to the iTPMS: " + connectException);
                try {
                    btSocket.close();
                } catch (IOException closeException) {
                    Log.d(TAG,"Unable to close socket during connection failure");
                }

                return;
            }

            // Do work to manage the connection (in a separate thread)
            iTPMSConnectedThread btConnectedThread = new iTPMSConnectedThread(btSocket);
            btConnectedThread.start();
        }

        // Cancel an in-progress connection, and close the socket
        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "Unable to close Bluetooth socket");
            }
        }
    }

    // Connected bluetooth thread
    private class iTPMSConnectedThread extends Thread {
        private final InputStream btInStream;

        public iTPMSConnectedThread(BluetoothSocket socket) {
            InputStream tmpInput = null;

            // Get the input stream, using temp objects because member streams are final
            try {
                tmpInput = socket.getInputStream();
            } catch (IOException e) {
                Log.d(TAG, "IO Exception getting input stream");
            }
            btInStream = tmpInput;
        }

        public void run() {
            byte[] buffer = new byte[256];  // Buffer store for the stream
            int bytes; // Bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = btInStream.read(buffer); // Get number of bytes and message in "buffer"
                    sensorMessages.obtainMessage(RECEIVE_MESSAGE, bytes, -1, buffer).sendToTarget(); // Send to message queue Handler
                } catch (IOException e) {
                    Log.d(TAG, "IO Exception while reading stream");
                    iTPMSConnectThread.cancel();
                    break;
                }
            }
        }
    }

    // Read raw text file
    private static String readRawTextFile(Context ctx, int resId)
    {
        InputStream inputStream = ctx.getResources().openRawResource(resId);

        InputStreamReader inputreader = new InputStreamReader(inputStream);
        BufferedReader buffreader = new BufferedReader(inputreader);
        String line;
        StringBuilder text = new StringBuilder();

        try {
            while (( line = buffreader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return text.toString();
    }

    public void updateLayout(){
        if (sharedPrefs.getString("prefInfoView", "0").contains("0")) {
            View otherLayout = findViewById(R.id.layoutInfoTPMS);
            if (otherLayout != null ) {
                ViewGroup parent = (ViewGroup) otherLayout.getParent();
                parent.removeView(otherLayout);
            }

            LinearLayout infoOtherLayout = (LinearLayout) findViewById(R.id.layoutInfoOther);
            if (infoOtherLayout == null) {
                LinearLayout infoLayout = (LinearLayout) findViewById(R.id.layoutInfo);
                View infoView = getLayoutInflater().inflate(R.layout.activity_main_info, infoLayout, false);
                infoLayout.addView(infoView);

            }

            txtInfo = (TextView) findViewById(R.id.textViewInfo);
            txtInfo.setGravity(Gravity.CENTER | Gravity.BOTTOM);
            txtInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            // Update Colors
            if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))){
                txtInfo.setTextColor(getResources().getColor(android.R.color.black));
            } else {
                txtInfo.setTextColor(getResources().getColor(android.R.color.white));
            }
            txtInfo.setText("- RPM");
        } else if (sharedPrefs.getString("prefInfoView", "0").contains("1")) {
            View otherLayout = findViewById(R.id.layoutInfoTPMS);
            if (otherLayout != null ) {
                ViewGroup parent = (ViewGroup) otherLayout.getParent();
                parent.removeView(otherLayout);
            }

            LinearLayout infoOtherLayout = (LinearLayout) findViewById(R.id.layoutInfoOther);
            if (infoOtherLayout == null) {
                LinearLayout infoLayout = (LinearLayout) findViewById(R.id.layoutInfo);
                View infoView = getLayoutInflater().inflate(R.layout.activity_main_info, infoLayout, false);
                infoLayout.addView(infoView);

            }

            txtInfo = (TextView) findViewById(org.thecongers.mcluster.R.id.textViewInfo);
            txtInfo.setGravity(Gravity.CENTER | Gravity.CENTER_VERTICAL);
            txtInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 120);
            // Update Colors
            if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))){
                txtInfo.setTextColor(getResources().getColor(android.R.color.black));
            } else {
                txtInfo.setTextColor(getResources().getColor(android.R.color.white));
            }
            txtInfo.setText("-");
        } else if (sharedPrefs.getString("prefInfoView", "0").contains("2")) {
            View otherLayout = findViewById(R.id.layoutInfoOther);
            if (otherLayout != null ) {
                ViewGroup parent = (ViewGroup) otherLayout.getParent();
                parent.removeView(otherLayout);
            }

            LinearLayout infoTPMSLayout = (LinearLayout) findViewById(R.id.layoutInfoTPMS);
            if (infoTPMSLayout == null) {
                LinearLayout myLayout = (LinearLayout) findViewById(R.id.layoutInfo);
                View infoView = getLayoutInflater().inflate(R.layout.activity_main_tpms, myLayout, false);
                myLayout.addView(infoView);
            }

            String pressureUnit = "psi";
            double frontPressure = frontPressurePSI;
            double rearPressure = rearPressurePSI;
            String pressureFormat = sharedPrefs.getString("prefpressuref", "0");
            if (pressureFormat.contains("1")) {
                // KPa
                frontPressure = frontPressurePSI * 6.894757293168361;
                rearPressure = rearPressurePSI * 6.894757293168361;
                pressureUnit = "KPa";
            } else if (pressureFormat.contains("2")) {
                // Kg-f
                frontPressure = frontPressurePSI * 0.070306957965539;
                rearPressure = rearPressurePSI * 0.070306957965539;
                pressureUnit = "Kg-f";
            } else if (pressureFormat.contains("3")) {
                // Bar
                frontPressure = frontPressurePSI * 0.0689475729;
                rearPressure = rearPressurePSI * 0.0689475729;
                pressureUnit = "Bar";
            }
            int formattedFrontPressure = (int) (frontPressure + 0.5d);
            int formattedRearPressure = (int) (rearPressure + 0.5d);
            txtFrontTPMS = (TextView) findViewById(R.id.textViewFrontTPMS);
            txtFrontTPMS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
            txtFrontTPMS.setText(formattedFrontPressure + " " + pressureUnit);
            txtRearTPMS = (TextView) findViewById(R.id.textViewRearTPMS);
            txtRearTPMS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
            txtRearTPMS.setText(formattedRearPressure + " " + pressureUnit);
            // Update Colors
            if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))){
                txtFrontTPMS.setTextColor(getResources().getColor(android.R.color.black));
                txtRearTPMS.setTextColor(getResources().getColor(android.R.color.black));
            } else {
                txtFrontTPMS.setTextColor(getResources().getColor(android.R.color.white));
                txtRearTPMS.setTextColor(getResources().getColor(android.R.color.white));
            }
        }
    }

    // Update colors
    public void updateColors(){
        if ((!itsDark) && (!sharedPrefs.getBoolean("prefNightMode", false))){
            layoutIcons.setBackgroundResource(background);
            layoutMiddleLeft.setBackgroundResource(background);
            txtGear.setBackgroundResource(background);
            layoutMiddleRight.setBackgroundResource(background);
            progressFuelLevel.setBackgroundResource(background);
            layoutBottomLeft.setBackgroundResource(background);
            layoutBottomRight.setBackgroundResource(background);
            root.setBackgroundColor(getResources().getColor(android.R.color.white));
            if (txtInfo != null) {
                txtInfo.setTextColor(getResources().getColor(android.R.color.black));
            } else if ((txtFrontTPMS != null) && (txtRearTPMS != null)){
                txtFrontTPMS.setTextColor(getResources().getColor(android.R.color.black));
                txtRearTPMS.setTextColor(getResources().getColor(android.R.color.black));
            }
            txtSpeed.setTextColor(getResources().getColor(android.R.color.black));
            txtSpeedUnit.setTextColor(getResources().getColor(android.R.color.black));
            txtGear.setTextColor(getResources().getColor(android.R.color.black));
            txtOdometers.setTextColor(getResources().getColor(android.R.color.black));
            txtESA.setTextColor(getResources().getColor(android.R.color.black));
            imageFuelLevel.setImageResource(R.mipmap.fuel_icon);
            imageButtonPreference.setImageResource(R.mipmap.cog);
        } else {
            layoutIcons.setBackgroundResource(backgroundDark);
            layoutMiddleLeft.setBackgroundResource(backgroundDark);
            txtGear.setBackgroundResource(backgroundDark);
            layoutMiddleRight.setBackgroundResource(backgroundDark);
            progressFuelLevel.setBackgroundResource(backgroundDark);
            layoutBottomLeft.setBackgroundResource(backgroundDark);
            layoutBottomRight.setBackgroundResource(backgroundDark);
            root.setBackgroundColor(getResources().getColor(android.R.color.black));
            if (txtInfo != null) {
                txtInfo.setTextColor(getResources().getColor(android.R.color.white));
            } else if ((txtFrontTPMS != null) && (txtRearTPMS != null)){
                txtFrontTPMS.setTextColor(getResources().getColor(android.R.color.white));
                txtRearTPMS.setTextColor(getResources().getColor(android.R.color.white));
            }
            txtSpeed.setTextColor(getResources().getColor(android.R.color.white));
            txtSpeedUnit.setTextColor(getResources().getColor(android.R.color.white));
            txtGear.setTextColor(getResources().getColor(android.R.color.white));
            txtOdometers.setTextColor(getResources().getColor(android.R.color.white));
            txtESA.setTextColor(getResources().getColor(android.R.color.white));
            imageFuelLevel.setImageResource(R.mipmap.fuel_icon_dark);
            imageButtonPreference.setImageResource(R.mipmap.cog_dark);
        }
    }

    // speak the user text
    public void speakString(String speech) {
        HashMap<String, String> myHashAlarm = new HashMap();
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                String.valueOf(AudioManager.STREAM_ALARM));
        text2speech.speak(speech, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
    }

    /*
     * @see http://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
     */
    // Lowpass filter
    protected float[] lowPass( float[] input, float[] output ) {
        if ( output == null ) return input;

        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    // Listens for light sensor events
    private final SensorEventListener sensorEventListener
            = new SensorEventListener(){

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do something

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (sharedPrefs.getBoolean("prefAutoNightMode", false) && (!sharedPrefs.getBoolean("prefNightMode", false))) {
                int delay = (Integer.parseInt(sharedPrefs.getString("prefAutoNightModeDelay", "30")) * 1000);
                if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                    float currentReading = event.values[0];
                    double darkThreshold = 20.0;  // Light level to determine darkness
                    if (currentReading < darkThreshold) {
                        lightTimer = 0;
                        if (darkTimer == 0) {
                            darkTimer = System.currentTimeMillis();
                        } else {
                            long currentTime = System.currentTimeMillis();
                            long duration = (currentTime - darkTimer);
                            if ((duration >= delay) && (!itsDark)) {
                                itsDark = true;
                                Log.d(TAG, "Its dark");
                                // Update colors
                                updateColors();
                            }
                        }
                    } else {
                        darkTimer = 0;
                        if (lightTimer == 0) {
                            lightTimer = System.currentTimeMillis();
                        } else {
                            long currentTime = System.currentTimeMillis();
                            long duration = (currentTime - lightTimer);
                            if ((duration >= delay) && (itsDark)) {
                                itsDark = false;
                                Log.d(TAG, "Its light");
                                // Update colors
                                updateColors();
                            }
                        }
                    }
                }
            }
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravity = lowPass(event.values.clone(), gravity);
            }
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagnetic = lowPass(event.values.clone(), geomagnetic);
            }
            if (sharedPrefs.getString("prefInfoView", "0").contains("1")) {
                if (gravity != null && geomagnetic != null) {
                    float R[] = new float[9];
                    float I[] = new float[9];
                    float remappedR[] = new float[9];
                    boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
                    if (success) {
                        float orientation[] = new float[3];
                        String bearing = "-";
                        SensorManager.remapCoordinateSystem(R, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedR);
                        SensorManager.getOrientation(remappedR, orientation);
                        int direction = filterChange(normalizeDegrees(Math.toDegrees(orientation[0])));
                        if((int)direction != (int)lastDirection) {
                            lastDirection = (int) direction;
                            Log.d(TAG, "Direction: " + direction);
                            if (lastDirection > 331 || lastDirection <= 28) {
                                bearing = "N";
                            } else if (lastDirection > 28 && lastDirection <= 73) {
                                bearing = "NE";
                            } else if (lastDirection > 73 && lastDirection <= 118) {
                                bearing = "E";
                            } else if (lastDirection > 118 && lastDirection <= 163) {
                                bearing = "SE";
                            } else if (lastDirection > 163 && lastDirection <= 208) {
                                bearing = "S";
                            } else if (lastDirection > 208 && lastDirection <= 253) {
                                bearing = "SW";
                            } else if (lastDirection > 253 && lastDirection <= 298) {
                                bearing = "W";
                            } else if (lastDirection > 298 && lastDirection <= 331) {
                                bearing = "NW";
                            }
                            txtInfo = (TextView) findViewById(org.thecongers.mcluster.R.id.textViewInfo);
                            txtInfo.setGravity(Gravity.CENTER | Gravity.CENTER_VERTICAL);
                            txtInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 120);
                            txtInfo.setText(bearing);
                        }
                    }
                }
            }
        }
    };

    //Normalize a degree from 0 to 360 instead of -180 to 180
    private int normalizeDegrees(double rads){
        return (int)((rads+360)%360);
    }


    private int filterChange(int newDir){
        int change = newDir - lastDirection;
        int circularChange = newDir-(lastDirection+360);
        int smallestChange;
        if(Math.abs(change) < Math.abs(circularChange)){
            smallestChange = change;
        }
        else{
            smallestChange = circularChange;
        }
        smallestChange = Math.max(Math.min(change,3),-3);
        return lastDirection+smallestChange;
    }
}