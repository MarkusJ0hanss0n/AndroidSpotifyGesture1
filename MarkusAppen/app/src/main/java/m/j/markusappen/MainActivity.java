package m.j.markusappen;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity{

    private SeekBar sbHoldTime;
    private SeekBar sbUnregTime;
    private SeekBar sbGestureTime;
    private TextView tvHoldTime;
    private TextView tvUnregTime;
    private TextView tvGestureTime;
    private Button btnSetDefault;

    private SensorManager mSensorManager;
    private Sensor proxSensor;
    private SensorEventListener SEL;

    private static String CLIENT_ID;
    private static String REDIRECT_URI;
    private static String REQUEST_CODE;
    private String AccessToken = "";

    //MyService TheService;
    //boolean isBound = false;

    PowerManager.WakeLock wakeLock;

    // Timer to Unregister sensor to avoid accidentally double inputs
    CountDownTimer TimerUnregister;
    int UnregTime = 400;
    boolean timerUnregisterIsTicking = false;
    
    // Timer to count if single or double input on given time (next or previous)
    CountDownTimer TimerGesture;
    boolean timerGestureIsTicking = false;
    int GestureTime = 1200;
    int GestureIntervalTime = 200;

    // Timer to sense Close input is continoues (play or pause)
    CountDownTimer TimerHolding;
    int HoldingTime = 2000;
    boolean holdingHaveBeenExecuted = false;
    boolean timerHoldningIsTicking = false;

    boolean isPlaying = false;
    int gestureCount = 0;

    boolean onFinishHaveBeenExecuted = false;  //need to kill timer when two gestures occured

    boolean newCloseInput = false;
    boolean newFarInput = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Intent i = new Intent(this, MyService.class);
        //bindService(i, MyConnetion, BIND_AUTO_CREATE);
        InitComponents();
        SetSeekBarListeners();

        CLIENT_ID = getString(R.string.CLIENT_ID);
        REDIRECT_URI = getString(R.string.REDIRECT_URI);
        REQUEST_CODE = getString(R.string.REQUEST_CODE);

        AquireWakeLock();

        SetTimerUnregister();
        SetTimerGesture();
        SetTimerHold();

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
                    getWindow().getDecorView().setBackgroundColor(Color.rgb(34,35,38));
                    newCloseInput = true;
                    if(newFarInput == true) {
                        newFarInput = false;
                        HandleHold();
                    }
                }else{
                    getWindow().getDecorView().setBackgroundColor(Color.rgb(18,18,18));
                    newFarInput = true;
                    if(newCloseInput) {
                        newCloseInput = false;
                        if (timerHoldningIsTicking) {
                            TimerHolding.cancel();
                            timerHoldningIsTicking = false;
                        }
                        if (holdingHaveBeenExecuted == false) {
                            HandleGesture();
                        }else {
                            holdingHaveBeenExecuted = false;
                        }
                    }
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
    }
    @Override
    protected void onPause() {
        super.onPause();
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
    protected void InitComponents(){
        sbHoldTime = (SeekBar) findViewById(R.id.seekBarHoldTime);
        sbUnregTime = (SeekBar) findViewById(R.id.seekBarUnregTime);
        sbGestureTime = (SeekBar) findViewById(R.id.seekBarGestureTime);
        tvHoldTime = (TextView) findViewById(R.id.textViewHoldTime);
        tvUnregTime = (TextView) findViewById(R.id.textViewUnregTime);
        tvGestureTime = (TextView) findViewById(R.id.textViewGestureTime);
        btnSetDefault = (Button) findViewById(R.id.buttonSetDefault);
    }
    protected void SetSeekBarListeners(){
        tvHoldTime.setText("Hold Time: " + sbHoldTime.getProgress() + "/" + sbHoldTime.getMax());
        sbHoldTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                HoldingTime = i;
                tvHoldTime.setText("Hold Time: " + HoldingTime + "/" + sbHoldTime.getMax());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SetTimerHold();
            }
        });
        tvUnregTime.setText("Unreg Time: " + sbUnregTime.getProgress() + "/" + sbUnregTime.getMax());
        sbUnregTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int j, boolean b) {
                UnregTime = j;
                tvUnregTime.setText("Unreg Time: " + UnregTime + "/" + sbUnregTime.getMax());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SetTimerUnregister();
            }
        });
        tvGestureTime.setText("Gesture Time: " + sbGestureTime.getProgress() + "/" + sbGestureTime.getMax());
        sbGestureTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int k, boolean b) {
                GestureTime = k;
                tvGestureTime.setText("Gesture Time: " + GestureTime + "/" + sbGestureTime.getMax());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SetTimerGesture();

            }
        });
        btnSetDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sbHoldTime.setProgress(2000);
                sbUnregTime.setProgress(400);
                sbGestureTime.setProgress(1200);
                SetTimerHold();
                SetTimerUnregister();
                SetTimerGesture();

            }
        });
    }
    protected void SetTimerUnregister(){
        TimerUnregister = new CountDownTimer(UnregTime, UnregTime) {
            @Override
            public void onTick(long millLeft) {
            }

            @Override
            public void onFinish() {
                mSensorManager.registerListener(SEL, proxSensor,
                        2 * 1000 * 1000);
                timerUnregisterIsTicking = false;
            }
        };
    }
    protected void SetTimerGesture(){
        TimerGesture = new CountDownTimer(GestureTime, GestureIntervalTime) {
            @Override
            public void onTick(long millLeft) {
                if(gestureCount >= 2){
                    onFinish();
                    onFinishHaveBeenExecuted = true;
                }
            }
            @Override
            public void onFinish() {
                if(onFinishHaveBeenExecuted == false) {
                    if (gestureCount >= 2) {
                        PlayPrevious();
                    } else {
                        PlayNext();
                    }
                    isPlaying = true;
                    timerGestureIsTicking = false;
                    gestureCount = 0;
                }
                onFinishHaveBeenExecuted = false;
            }
        };
    }
    protected void SetTimerHold(){
        TimerHolding = new CountDownTimer(HoldingTime, HoldingTime) {
            @Override
            public void onTick(long millLeft) {

            }
            @Override
            public void onFinish() {
                TogglePlayPause();
                holdingHaveBeenExecuted = true;
                timerHoldningIsTicking = false;
            }
        };
    }
    protected void HandleHold(){
        if(timerHoldningIsTicking == true) {
            TimerHolding.cancel();
            timerHoldningIsTicking = false;
        }
        timerHoldningIsTicking = true;
        TimerHolding.start();
    }
    protected void HandleGesture(){
        UnregSensor();
        if(timerGestureIsTicking == false){
            timerGestureIsTicking = true;
            TimerGesture.start();
        }
          gestureCount += 1;
    }
    protected void UnregSensor(){
        mSensorManager.unregisterListener(SEL);
        if(timerUnregisterIsTicking == true) {
            TimerUnregister.cancel();
            timerUnregisterIsTicking = false;
        }
        timerUnregisterIsTicking = true;
        TimerUnregister.start();
    }
    protected void TogglePlayPause(){
        if (isPlaying == true){
            Pause();
            isPlaying = false;
        }else{
            Play();
            isPlaying = true;
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
    }
    protected  void Pause(){
        HandleHttpPut("https://api.spotify.com/v1/me/player/pause?access_token=" + AccessToken);
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

//    private ServiceConnection MyConnetion = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
//            MyLocalBinder binder = (MyLocalBinder) iBinder;
//            TheService = binder.getService();
//            isBound = true;
//            Toast.makeText(MainActivity.this, "Bound to service: True", Toast.LENGTH_LONG).show();
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName componentName) {
//            isBound = false;
//            Toast.makeText(MainActivity.this, "Bound to service: False", Toast.LENGTH_LONG).show();
//        }
//    };
    @Override
    protected void onDestroy() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        TimerUnregister.cancel();
        TimerGesture.cancel();
        TimerHolding.cancel();
        ReleaseWakeLock();
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