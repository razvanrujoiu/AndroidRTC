package fr.pchab.androidrtc;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.SessionDescription;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.util.List;

import fr.pchab.webrtcclient.PeerConnectionParameters;
import fr.pchab.webrtcclient.WebRtcClient;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RtcActivity extends Activity implements WebRtcClient.RtcListener {
    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView glSurfaceView;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private WebRtcClient webRtcClient;
    private String mSocketAddress;
    private String callerId;

    private static final String[] RequiredPermissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    protected PermissionChecker permissionChecker = new PermissionChecker();


    MqttAndroidClient mqttAndroidClient;
    final String mqttServerUri = "tcp://test.mosquitto.org:1883";
    SharedPreferences sharedPreferences;
    String phoneNumber = "";
    String remotePhoneNumber = "";

    private EditText remotePhoneNumberEditText;
    private Button callBtn;
    private Button answerBtn;

    Observable<IceCandidate> iceCandidateObservable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.main);
//        mSocketAddress = "http://" + getResources().getString(R.string.host);
//        mSocketAddress += (":" + getResources().getString(R.string.port) + "/");

        glSurfaceView = findViewById(R.id.glview_call);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setKeepScreenOn(true);
        VideoRendererGui.setView(glSurfaceView, new Runnable() {
            @Override
            public void run() {
                init();
            }
        });

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            final List<String> segments = intent.getData().getPathSegments();
            callerId = segments.get(0);
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        phoneNumber = sharedPreferences.getString("phoneNumber", null);

        remotePhoneNumberEditText = findViewById(R.id.remotePhoneNumberEditText);
        callBtn = findViewById(R.id.callBtn);
        answerBtn = findViewById(R.id.answerBtn);

        setupMqttConnection();
        checkPermissions();

        callBtn.setOnClickListener(v -> {
            remotePhoneNumber = remotePhoneNumberEditText.getText().toString();
            if (remotePhoneNumber.equals("") || remotePhoneNumber.isEmpty()) {
                Toast.makeText(getApplicationContext(), "Please fill phone number field", Toast.LENGTH_LONG).show();
                return;
            }

            JSONObject json = webRtcClient.getSdpOffer(phoneNumber);

            publishMessage(json.toString(), "/" + remotePhoneNumber);

            webRtcClient.setCamera();

        });

        answerBtn.setOnClickListener(v -> {

            if(!remotePhoneNumber.isEmpty()) {
                JSONObject json = webRtcClient.createSdpAnswer(phoneNumber);
                publishMessage(json.toString(), "/" + remotePhoneNumber);
                webRtcClient.setCamera();
            }
        });




    }


    void setupMqttConnection() {
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), mqttServerUri, phoneNumber);

        try {
            MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setAutomaticReconnect(true);
            mqttConnectOptions.setCleanSession(false);

            IMqttToken iMqttToken = mqttAndroidClient.connect(mqttConnectOptions);
            iMqttToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d("MQTT", "mqtt connection complete");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d("MQTT", "mqtt connection failed");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }

        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                subscribeToTopic("/" + phoneNumber);
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d("MQTT", "mqtt connection lost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                if(topic.equals("/" + phoneNumber)) {
                    byte[] mqttPayload = message.getPayload();
                    String jsonString = new String(mqttPayload, "UTF-8");
                    Log.d("MQTT", jsonString);

                    JSONObject jsonObject = new JSONObject(jsonString);
                    JSONObject payload = jsonObject.getJSONObject("payload");
                    String messageType = jsonObject.getString("type");

                    if (messageType.equals("SessionDescription")) {

                        String sdpDescription = payload.getString("sdp");
                        String type = payload.getString("type");
                        remotePhoneNumber = payload.getString("srcPhoneNumber");

                        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdpDescription);

                        webRtcClient.setRemoteSdp(remotePhoneNumber, sessionDescription);

                        Log.d("MQTT", sessionDescription.toString());

                    } else if (messageType.equals("IceCandidate")) {

                        String sdp = payload.getString("sdp");
                        int sdpMLineIndex = payload.getInt("sdpMLineIndex");
                        String sdpMid = payload.getString("sdpMid");
                        remotePhoneNumber = payload.getString("srcPhoneNumber");

                        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);

                        webRtcClient.addIceCandidate(remotePhoneNumber, iceCandidate);

                        Log.d("MQTT", iceCandidate.toString());

                    }

                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Log.d("MQTT", "mqtt delivery complete");
            }
        });


    }

    public void subscribeToTopic(final String topicName) {
        try {
          mqttAndroidClient.subscribe(topicName, 0, null, new IMqttActionListener() {
              @Override
              public void onSuccess(IMqttToken asyncActionToken) {
                  Log.d("MQTT", "subscribed sucessfully to: " + topicName);
              }

              @Override
              public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                  Log.d("MQTT", "failed to subscribe");
              }
          });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publishMessage(String message, String topic) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttAndroidClient.publish(topic, mqttMessage);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void checkPermissions() {
        permissionChecker.verifyPermissions(this, RequiredPermissions, new PermissionChecker.VerifyPermissionsCallback() {

            @Override
            public void onPermissionAllGranted() {

            }

            @Override
            public void onPermissionDeny(String[] permissions) {
                Toast.makeText(RtcActivity.this, "Please grant required permissions.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        PeerConnectionParameters params = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);

        webRtcClient = new WebRtcClient(this, params, VideoRendererGui.getEGLContext());

        iceCandidateObservable = webRtcClient.iceCandidatePublishSubject.doOnNext(iceCandidate -> {
            if (iceCandidate != null) {
                JSONObject json = new JSONObject();
                JSONObject payload = new JSONObject();

                try {
                    payload.put("sdp", iceCandidate.sdp);
                    payload.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                    payload.put("sdpMid", iceCandidate.sdpMid);
                    payload.put("srcPhoneNumber", phoneNumber);
                    json.put("payload", payload);
                    json.put("type", "IceCandidate");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                publishMessage(json.toString(), "/" + remotePhoneNumber);
            }
        });
        iceCandidateObservable.subscribe();


    }

    @Override
    public void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        if (webRtcClient != null) {
            webRtcClient.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        if (webRtcClient != null) {
            webRtcClient.onResume();
        }
    }

    @Override
    public void onDestroy() {
        if (webRtcClient != null) {
            webRtcClient.onDestroy();
        }
        super.onDestroy();
    }

//    @Override
//    public void onCallReady(String callId) {
//
////        if (callerId != null) {
////            try {
////                answer(callerId);
////            } catch (JSONException e) {
////                e.printStackTrace();
////            }
////        } else {
////            call(callId);
////        }
//    }

//    public void answer(String callerId) throws JSONException {
//        webRtcClient.sendMessage(callerId, "init", null);
//        startCam();
//    }

    public void call(String callId) {
        Intent msg = new Intent(Intent.ACTION_SEND);
        msg.putExtra(Intent.EXTRA_TEXT, mSocketAddress + callId);
        msg.setType("text/plain");
    }


    public void startCam() {
        // Camera settings
        if (PermissionChecker.hasPermissions(this, RequiredPermissions)) {
            webRtcClient.start("android_test");
        }
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, false);
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType, false);
    }

    @Override
    public void onRemoveRemoteStream() {
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}