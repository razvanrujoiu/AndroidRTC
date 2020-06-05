package fr.pchab.webrtcclient;

import android.opengl.EGLContext;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoSource;

import java.util.HashMap;
import java.util.LinkedList;
import io.reactivex.rxjava3.android.*;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;


public class WebRtcClient {
    private final static String TAG = WebRtcClient.class.getCanonicalName();
    private final static int MAX_PEER = 2;
    private boolean[] endPoints = new boolean[MAX_PEER];
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private PeerConnectionParameters pcParams;
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private VideoSource videoSource;
    private RtcListener mListener;
//    private Socket client;

    public IceCandidate iceCandidate;
    public PublishSubject<IceCandidate> iceCandidatePublishSubject = PublishSubject.create();
    /**
     * Implement this interface to be notified of events.
     */



    public interface RtcListener {
//        void onCallReady(String callId);


        void onStatusChanged(String newStatus);

        void onLocalStream(MediaStream localStream);

        void onAddRemoteStream(MediaStream remoteStream);

        void onRemoveRemoteStream();

//        void didDiscoverIceCandidate(IceCandidate iceCandidate) {
//
//        };
    }


//    public Peer createOffer() {
//
//    }

    private interface Command {
        void execute(String peerId, JSONObject payload) throws JSONException;
    }

    public class CreateOfferCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "CreateOfferCommand");
            Peer peer = peers.get(peerId);
            peer.pc.createOffer(peer, pcConstraints);
        }
    }

    private class CreateAnswerCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "CreateAnswerCommand");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
            peer.pc.createAnswer(peer, pcConstraints);
        }


    }

    private class SetRemoteSDPCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "SetRemoteSDPCommand");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
        }
    }



    private class AddIceCandidateCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "AddIceCandidateCommand");
            PeerConnection pc = peers.get(peerId).pc;
            if (pc.getRemoteDescription() != null) {
                IceCandidate candidate = new IceCandidate(
                        payload.getString("phoneNumber"),
                        payload.getInt("label"),
                        payload.getString("candidate")
                );
                pc.addIceCandidate(candidate);
            }
        }

    }


    /**
     * Send a message through the signaling server
     *
     * @param to      phoneNumber of recipient
     * @param type    type of message
     * @param payload payload of message
     * @throws JSONException
     */
    public void sendMessage(String to, String type, JSONObject payload) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
//        client.emit("message", message);
    }


    public JSONObject getSdpOffer(String srcPhoneNumber) {
        Peer peer = new Peer(srcPhoneNumber);
        peer.pc.createOffer(peer, pcConstraints);
        JSONObject json = new JSONObject();
        JSONObject payload = new JSONObject();
        try {
            payload.put("sdp", peer.pc.getLocalDescription().description);
            payload.put("type", peer.pc.getLocalDescription().type.canonicalForm());
            payload.put("srcPhoneNumber", srcPhoneNumber);
            json.put("payload", payload);
            json.put("type","SessionDescription");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return  json;
    }

    public JSONObject createSdpAnswer(String srcPhoneNumber) {
        Peer peer = new Peer(srcPhoneNumber);
        peer.pc.createOffer(peer, pcConstraints);
        SessionDescription sdp = peer.pc.getLocalDescription();


        JSONObject json = new JSONObject();
        JSONObject payload = new JSONObject();

        try {
            payload.put("sdp", peer.pc.getLocalDescription().description);
            payload.put("type", peer.pc.getLocalDescription().type.canonicalForm());
            payload.put("srcPhoneNumber", srcPhoneNumber);
            json.put("payload", payload);
            json.put("type", "SessionDescription");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return  json;
    }

    public void setRemoteSdp(String phoneNumber, SessionDescription sdp) {
        Peer peer = new Peer(phoneNumber);
        peer.pc.setRemoteDescription(peer, sdp);
        peer.pc.createAnswer(peer, pcConstraints);
    }

    public void addIceCandidate(String phoneNumber, IceCandidate iceCandidate) {
        Peer peer = new Peer(phoneNumber);
        peer.pc.addIceCandidate(iceCandidate);
    }


    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;
        private String phoneNumber;
        private int endPoint;

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            pc.setLocalDescription(Peer.this, sdp);
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
        }

        @Override
        public void onSetFailure(String s) {
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            switch (signalingState) {
                case HAVE_LOCAL_OFFER:
                    Log.d("SIGNALING_STATE", "Have local OFFER");
                    break;
                case HAVE_LOCAL_PRANSWER:
                    Log.d("SIGNALING_STATE", "Have local PRANSWER");
                    break;
                case HAVE_REMOTE_OFFER:
                    Log.d("SIGNALING_STATE", "Have remote OFFER");
                    break;
                case HAVE_REMOTE_PRANSWER:
                    Log.d("SIGNALING_STATE", "Have remote PRANSWER");
                    break;
                case STABLE:
                    Log.d("SIGNALING_STATE", "Signaling STABLE");
                    break;
                case CLOSED:
                    Log.d("SIGNALING_STATE", "Signaling CLOSED");
                    break;
            }
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
//                removePeer(phoneNumber);
                mListener.onStatusChanged("DISCONNECTED");
            }
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }


        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            iceCandidate = candidate;
            iceCandidatePublishSubject.onNext(candidate);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream " + mediaStream.label());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream " + mediaStream.label());
            removePeer(phoneNumber);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        public Peer(String phoneNumber, int endPoint) {
            Log.d(TAG, "new Peer: " + phoneNumber + " " + endPoint);
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.phoneNumber = phoneNumber;
            this.endPoint = endPoint;

            pc.addStream(localMS); //, new MediaConstraints()

            mListener.onStatusChanged("CONNECTING");
        }

        public Peer(String srcPhoneNumber) {
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.phoneNumber = srcPhoneNumber;

            pc.addStream(localMS);
            mListener.onStatusChanged("CONNECTING");
        }
    }





    private void removePeer(String id) {
        Peer peer = peers.get(id);
//        mListener.onRemoveRemoteStream(peer.endPoint);
        peer.pc.close();
        peers.remove(peer.phoneNumber);
        endPoints[peer.endPoint] = false;
    }

    public WebRtcClient(RtcListener listener, PeerConnectionParameters params, EGLContext mEGLcontext) {
        mListener = listener;
        pcParams = params;
        PeerConnectionFactory.initializeAndroidGlobals(listener, true, true,
                params.videoCodecHwAcceleration, mEGLcontext);
        factory = new PeerConnectionFactory();

        iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        iceServers.add(new PeerConnection.IceServer("turn:numb.viagenie.ca","webrtc@live.com", "muazkh"));


        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));


        setCamera();
    }



    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
        if (videoSource != null) videoSource.stop();
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
        if (videoSource != null) videoSource.restart();
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    public void onDestroy() {
        for (Peer peer : peers.values()) {
            peer.pc.dispose();
        }
        if (videoSource != null) {
            videoSource.dispose();
        }
        factory.dispose();

    }



    /**
     * Start the client.
     * <p>
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     *
     * @param name client name
     */
    public void start(String name) {
        setCamera();
        try {
            JSONObject message = new JSONObject();
            message.put("name", name);
//            client.emit("readyToStream", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setCamera() {
        localMS = factory.createLocalMediaStream("ARDAMS");
        if (pcParams.videoCallEnabled) {
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

            videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
            localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        }

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));

        mListener.onLocalStream(localMS);
    }

    private VideoCapturer getVideoCapturer() {
//        String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        String rearCameraDeviceName = VideoCapturerAndroid.getNameOfBackFacingDevice();
        return VideoCapturerAndroid.create(rearCameraDeviceName);
    }
}
