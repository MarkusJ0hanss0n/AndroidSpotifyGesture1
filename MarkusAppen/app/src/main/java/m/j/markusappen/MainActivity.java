package m.j.markusappen;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.test.suitebuilder.TestMethod;
import android.widget.EditText;
import android.widget.Toast;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;
import java.util.concurrent.ExecutionException;

import m.j.markusappen.MyService.MyLocalBinder;

public class MainActivity extends AppCompatActivity{

    private SensorManager mSensorManager;
    private Sensor proxSensor;
    private SensorEventListener SEL;

    private static String CLIENT_ID;
    private static String REDIRECT_URI;
    private static String REQUEST_CODE;
    private String AccessToken;

    MyService TheService;
    boolean isBound = false;

    PowerManager.WakeLock wakeLock;

    CountDownTimer timerUnregister;
    int DelayUnreg = 400;
    int DelayCount = 1200;
    int IntervalCount = 200;
    boolean timerUnregisterIsTicking;

    CountDownTimer timerGestureCount;
    boolean timerGestureCountIsTicking;

    boolean holdingGesture = false;
    boolean isPlaying;
    int gestureCount;

    boolean dontMind = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Intent i = new Intent(this, MyService.class);
        //bindService(i, MyConnetion, BIND_AUTO_CREATE);

        CLIENT_ID = getString(R.string.CLIENT_ID);
        REDIRECT_URI = getString(R.string.REDIRECT_URI);
        REQUEST_CODE = getString(R.string.REQUEST_CODE);
        AccessToken = "";
        isPlaying = false;
        gestureCount = 0;
        timerUnregisterIsTicking = false;
        timerGestureCountIsTicking = false;

        AquireWakeLock();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        proxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);


        if (proxSensor == null){
            Toast.makeText(this, "Proximity sensor is not available", Toast.LENGTH_LONG).show();
            finish();
        }
        SEL = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if(sensorEvent.values[0] < proxSensor.getMaximumRange()){
                    getWindow().getDecorView().setBackgroundColor(Color.RED);
                    HandleGesture();
                    //holdingGesture = true;
                    //Toast.makeText(MainActivity.this, "HoldingGesture: True", Toast.LENGTH_LONG).show();
                }else{
                    getWindow().getDecorView().setBackgroundColor(Color.GREEN);
                    //holdingGesture = false;
                    //Toast.makeText(MainActivity.this, "HoldingGesture: False", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };


        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming", "user-modify-playback-state", "user-read-recently-played"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, Integer.parseInt(REQUEST_CODE), request);

    }
    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(SEL, proxSensor,
                2 * 1000 * 1000);
        //holdingGesture = true;
    }
    @Override
    protected void onPause() {
        super.onPause();
        //mSensorManager.unregisterListener(SEL);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == Integer.parseInt(REQUEST_CODE)) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                AccessToken = response.getAccessToken();

            }
        }
    }
    protected void HandleGesture(){

        mSensorManager.unregisterListener(SEL);
        if(timerUnregisterIsTicking == true) {
            timerUnregister.cancel();
            timerUnregisterIsTicking = false;
        }
        SetTimerUnregister(timerUnregister);
        if(timerGestureCountIsTicking == false){
            SetTimerGestureCount(timerGestureCount);
        }
          gestureCount += 1;
    }
    protected void SetTimerUnregister(CountDownTimer timer){
        timerUnregisterIsTicking = true;
        timer = new CountDownTimer(DelayUnreg, DelayUnreg) {
            @Override
            public void onTick(long millLeft) {
            }

            @Override
            public void onFinish() {
                mSensorManager.registerListener(SEL, proxSensor,
                        2 * 1000 * 1000);
                //holdingGesture = true;
                //Toast.makeText(MainActivity.this, "Timer alive", Toast.LENGTH_LONG).show();
                timerUnregisterIsTicking = false;
            }
        }.start();
    }
    protected void SetTimerGestureCount(CountDownTimer timer){
        timerGestureCountIsTicking = true;
        timer = new CountDownTimer(DelayCount, IntervalCount) {
            @Override
            public void onTick(long millLeft) {
                if(gestureCount >= 2){
                    onFinish();
                    dontMind = true;
                }
            }
            @Override
            public void onFinish() {
                if(dontMind == false) {
                    if (gestureCount >= 2) {
                        PlayPrevious();
                    } else {
                        PlayNext();
                    }
                    timerGestureCountIsTicking = false;
                    gestureCount = 0;
                }
                dontMind = false;
            }
        }.start();
    }
    protected void TogglePlayPause(){
        if (isPlaying == true){
            Pause();
        }else{
            Play();
        }
    }
    protected  void PlayPrevious(){
        HandleHttpPost("https://api.spotify.com/v1/me/player/previous?access_token=" + AccessToken);
    }
    protected  void PlayNext(){
        HandleHttpPost("https://api.spotify.com/v1/me/player/next?access_token=" + AccessToken);
    }
    protected  void Play(){
        HandleHttpPut("https://api.spotify.com/v1/me/player/play?access_token=" + AccessToken);
        isPlaying = true;
    }
    protected  void Pause(){
        HandleHttpPut("https://api.spotify.com/v1/me/player/pause?access_token=" + AccessToken);
        isPlaying = false;
    }
    protected  void HandleHttpGet(String sURL){
        try {
            HttpGet(sURL);
        } catch (ExecutionException e) {
          e.printStackTrace();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
    }
    protected  void HandleHttpPost(String sURL){
        try {
            HttpPost(sURL);
        } catch (MalformedURLException e) {
           e.printStackTrace();
        }
    }
    protected  void HandleHttpPut(String sURL){
        try {
            HttpPut(sURL);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
    protected void HttpGet(String sURL) throws ExecutionException, InterruptedException {
        String result;
        HttpGetRequest getRequest = new HttpGetRequest();
        result = getRequest.execute(sURL).get();
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
    }
    protected void HttpPost(String sURL) throws MalformedURLException {
        URL url = new URL(sURL);
        new HttpPostRequest(this).execute(url);
    }
    protected void HttpPut(String sURL) throws MalformedURLException {
        URL url = new URL(sURL);
        new HttpPutRequest(this).execute(url);
    }

    private ServiceConnection MyConnetion = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MyLocalBinder binder = (MyLocalBinder) iBinder;
            TheService = binder.getService();
            isBound = true;
            Toast.makeText(MainActivity.this, "Bound to service: True", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
            Toast.makeText(MainActivity.this, "Bound to service: False", Toast.LENGTH_LONG).show();
        }
    };
    @Override
    protected void onDestroy() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        timerUnregister.cancel();
        timerGestureCount.cancel();
        super.onDestroy();
    }
    public void AquireWakeLock(){
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();
        Toast.makeText(this, "WakeLock: ON", Toast.LENGTH_LONG).show();
    }
    public void ReleaseWakeLock(){
        wakeLock.release();
        Toast.makeText(this, "WakeLock: OFF", Toast.LENGTH_LONG).show();
    }
}