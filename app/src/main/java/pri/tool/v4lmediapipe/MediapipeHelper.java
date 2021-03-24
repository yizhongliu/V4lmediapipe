package pri.tool.v4lmediapipe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;


public class MediapipeHelper {
    private static final String TAG = "MediapipeHelper";

    // 资源文件和流输出名
    private static final String BINARY_GRAPH_NAME = "hand_tracking_mobile_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_HAND_PRESENCE_STREAM_NAME = "hand_presence";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks";

    // 因为OpenGL表示图像时假设图像原点在左下角，而MediaPipe通常假设图像原点在左上角，所以要翻转
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    // 加载动态库
    static {
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    public static MediapipeHelper sMediapipeHelper;
    Context appContext;
    OnHandGestureListener handGestureListener;

    public static MediapipeHelper getInstance(Context context) {
        synchronized (MediapipeHelper.class) {
            if (sMediapipeHelper == null) {
                sMediapipeHelper = new MediapipeHelper(context.getApplicationContext());
            }

            return sMediapipeHelper;
        }
    }

    public MediapipeHelper(Context context) {
        // 初始化assets管理器，以便MediaPipe应用资源
        AndroidAssetUtil.initializeNativeAssetManager(context);
    }

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;

    private BitmapConverter bitmapConverter;

    private boolean handPresence;

    public void startMediapipe(Context context) {
        eglManager = new EglManager(null);
        // 通过加载获取一个帧处理器
        processor = new FrameProcessor(context,
                eglManager.getNativeContext(),
                BINARY_GRAPH_NAME,
                INPUT_VIDEO_STREAM_NAME,
                OUTPUT_VIDEO_STREAM_NAME);
        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);

        // 获取是否检测到手模型输出
        processor.addPacketCallback(
                OUTPUT_HAND_PRESENCE_STREAM_NAME,
                (packet) -> {
                    handPresence = PacketGetter.getBool(packet);
                    if (!handPresence) {
            //            Log.d(TAG, "[TS:" + packet.getTimestamp() + "] Hand presence is false, no hands detected.");
                    }
                });

        // 获取手的关键点模型输出
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    try {
                        LandmarkProto.NormalizedLandmarkList landmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
//                        if (landmarks == null || !handPresence) {
//                            Log.d(TAG, "[TS:" + packet.getTimestamp() + "] No hand landmarks.");
//                            return;
//                        }
//                        // 如果没有检测到手，输出的关键点是无效的
//                        Log.d(TAG,
//                                "[TS:" + packet.getTimestamp()
//                                        + "] #Landmarks for hand: "
//                                        + landmarks.getLandmarkCount());
//                        Log.d(TAG, getLandmarksDebugString(landmarks));
//                        Log.d(TAG, recognizeHandGesture(landmarks));
                        String handGesture = recognizeHandGesture(landmarks);
                        if (handGestureListener != null) {
                            handGestureListener.OnHandGestureRecognization(handGesture);
                        }
                    } catch (InvalidProtocolBufferException e) {
                        Log.e(TAG, "Couldn't Exception received - " + e);
                    }
                });
    }

    public void setSurfaceTexture(Size size, boolean isRotate, SurfaceTexture surfaceTexture) {
        converter.setSurfaceTextureAndAttachToGLContext(
                surfaceTexture,
                isRotate? size.getHeight() : size.getWidth(),
                isRotate? size.getWidth() : size.getHeight());
    }


    // 解析关键点
    private static String getLandmarksDebugString(LandmarkProto.NormalizedLandmarkList landmarks) {
        int landmarkIndex = 0;
        StringBuilder landmarksString = new StringBuilder();
        for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
            landmarksString.append("\t\tLandmark[").append(landmarkIndex).append("]: (").append(landmark.getX()).append(", ").append(landmark.getY()).append(", ").append(landmark.getZ()).append(")\n");
            ++landmarkIndex;
        }
        return landmarksString.toString();
    }

    public void setSurface(Surface surface) {
        processor.getVideoSurfaceOutput().setSurface(surface);
    }

    public void onFrame(Bitmap bitmap) {
        long timeStamp = System.currentTimeMillis();
        processor.onNewFrame(bitmap, timeStamp);
    }

    public void startConverter() {
        converter = new ExternalTextureConverter(eglManager.getContext());
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);

    }

    public void startBitmapConverter() {
        bitmapConverter = new BitmapConverter(eglManager.getContext());
        bitmapConverter.setConsumer(processor);
    }

    public void stopBitmapConverter() {
        bitmapConverter.close();
    }

    public void stopConverter() {
        converter.close();
    }

    public BitmapConverter getBitmapConverter() {
        return bitmapConverter;
    }


    public String  recognizeHandGesture(LandmarkProto.NormalizedLandmarkList landmarks) {
        String handGesture = "No hand";

        if (landmarks == null || !handPresence) {
            return handGesture;
        }

        // finger states
        Boolean thumbIsOpen = false;
        Boolean firstFingerIsOpen = false;
        Boolean secondFingerIsOpen = false;
        Boolean thirdFingerIsOpen = false;
        Boolean fourthFingerIsOpen = false;

        float pseudoFixKeyPoint =  landmarks.getLandmark(2).getX();
        if (landmarks.getLandmark(3).getX() < pseudoFixKeyPoint && landmarks.getLandmark(4).getX() < pseudoFixKeyPoint)
        {
            thumbIsOpen = true;
        }

        pseudoFixKeyPoint = landmarks.getLandmark(6).getY();
        if (landmarks.getLandmark(7).getY() < pseudoFixKeyPoint && landmarks.getLandmark(8).getY() < pseudoFixKeyPoint)
        {
            firstFingerIsOpen = true;
        }

        pseudoFixKeyPoint = landmarks.getLandmark(10).getY();
        if (landmarks.getLandmark(11).getY() < pseudoFixKeyPoint && landmarks.getLandmark(12).getY() < pseudoFixKeyPoint)
        {
            secondFingerIsOpen = true;
        }

        pseudoFixKeyPoint = landmarks.getLandmark(14).getY();
        if (landmarks.getLandmark(15).getY() < pseudoFixKeyPoint && landmarks.getLandmark(16).getY() < pseudoFixKeyPoint)
        {
            thirdFingerIsOpen = true;
        }

        pseudoFixKeyPoint = landmarks.getLandmark(18).getY();
        if (landmarks.getLandmark(19).getY() < pseudoFixKeyPoint && landmarks.getLandmark(20).getY() < pseudoFixKeyPoint)
        {
            fourthFingerIsOpen = true;
        }

        Log.e(TAG, "is finger open[" + thumbIsOpen + "," + firstFingerIsOpen + "," + secondFingerIsOpen + "," + thirdFingerIsOpen + "," + fourthFingerIsOpen);


// Hand gesture recognition
        if (thumbIsOpen && firstFingerIsOpen && secondFingerIsOpen && thirdFingerIsOpen && fourthFingerIsOpen) {
            handGesture = "FIVE";
        }
        else if (!thumbIsOpen && firstFingerIsOpen && secondFingerIsOpen && thirdFingerIsOpen && fourthFingerIsOpen) {
            handGesture = "FOUR";
        }
        else if (thumbIsOpen && firstFingerIsOpen && secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
            handGesture = "TREE";
        }
        else if (thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
            handGesture = "TWO";
        }
        else if (!thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
            handGesture = "ONE";
        }
        else if (!thumbIsOpen && firstFingerIsOpen && secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
            handGesture = "YEAH";
        }
        else if (!thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && fourthFingerIsOpen) {
            handGesture = "ROCK";
        }
        else if (thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && fourthFingerIsOpen) {
            handGesture = "SPIDERMAN";
        }
        else if (!thumbIsOpen && !firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
            handGesture = "FIST";
        }
        else if (!firstFingerIsOpen && secondFingerIsOpen && thirdFingerIsOpen && fourthFingerIsOpen && isThumbNearFirstFinger(landmarks.getLandmark(4), landmarks.getLandmark(8))) {
            handGesture = "OK";
        }
        else {
     //       Log.e(TAG, "no hand gesture");
        }


        return handGesture;
    }

    private double getEuclideanDistanceAB(float a_x, float a_y, float b_x, float b_y)
    {
        double dist = Math.pow(a_x - b_x, 2) + Math.pow(a_y - b_y, 2);
        return Math.sqrt(dist);
    }

    private Boolean isThumbNearFirstFinger(LandmarkProto.NormalizedLandmark point1, LandmarkProto.NormalizedLandmark point2)
    {
        double distance = getEuclideanDistanceAB(point1.getX(), point1.getY(), point2.getX(), point2.getY());
        return distance < 0.1;
    }

    public void setHandGestureListener(OnHandGestureListener listener) {
        handGestureListener = listener;
    }

    public static interface OnHandGestureListener {
        void OnHandGestureRecognization(@Nullable String handGesture);
    }

}
