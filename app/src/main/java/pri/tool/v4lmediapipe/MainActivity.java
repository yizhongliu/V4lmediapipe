package pri.tool.v4lmediapipe;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

import pri.tool.v4l2camera.IDataCallback;
import pri.tool.v4l2camera.IStateCallback;
import pri.tool.v4l2camera.V4L2Camera;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";

    private static final int MAGIC_TEXTURE_ID = 10;

    V4L2Camera adCamera;
    CameraStateCallback cameraStateCallback;
    CameraDataCallback cameraDataCallback;

    private int previewWidth = 640;
    private int previewHeight = 480;

    ImageView imageView;

    SurfaceTexture surfaceTexture;
    Surface surface;
    private SurfaceView previewDisplayView;


    MediapipeHelper mediapipeHelper;

    CustomFrameAvailableListner customFrameAvailableListner;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    //    imageView = findViewById(R.id.imageview);
//        previewDisplayView = new SurfaceView(this);
//        setupPreviewDisplayView();


        mediapipeHelper = MediapipeHelper.getInstance(this);
        mediapipeHelper.setHandGestureListener(onHandGestureListener);
        mediapipeHelper.startMediapipe(this);
//
//        mediapipeHelper.startConverter();

//        initCamera();

    }

    @Override
    public void onResume() {
        Log.d("thread", "onResume: " + android.os.Process.myTid());
        super.onResume();
        mediapipeHelper.startBitmapConverter();
        setCustomFrameAvailableListner(mediapipeHelper.getBitmapConverter());
        initCamera();
  //      previewDisplayView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        mediapipeHelper.stopBitmapConverter();
    }

    public void initCamera() {

        cameraStateCallback = new CameraStateCallback();
        adCamera = new V4L2Camera();
        adCamera.init(cameraStateCallback, this);
        adCamera.open();
    }

    class CameraStateCallback implements IStateCallback {

        @Override
        public void onOpened() {
            Log.d(TAG, "onOpened");

            Size chooseSize = adCamera.chooseOptimalSize(previewWidth, previewHeight);
            if (chooseSize != null) {
                previewWidth = chooseSize.getWidth();
                previewHeight = chooseSize.getHeight();

            }

//            surfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
//            surface = new Surface(surfaceTexture);
//            adCamera.setSurface(surface);

            cameraDataCallback = new CameraDataCallback();
            adCamera.startPreview(cameraDataCallback);

   //         mediapipeHelper.setSurfaceTexture(chooseSize, false, surfaceTexture);


        }

        @Override
        public void onError(int error) {

        }
    }

    class CameraDataCallback implements IDataCallback {

        @Override
        public void onDataCallback(byte[] data, int dataType, int width, int height) {
            Log.e(TAG, "onDataCallbakck  dataType:" + dataType + ", width:" + width + ", height:" + height + ", data.lenght:" + data.length);
            //处理camera preview 数据

                    try{
                        YuvImage image = new YuvImage(data, ImageFormat.YUY2, width, height, null);
                        if(image!=null){
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            image.compressToJpeg(new Rect(0, 0, width, height), 80, stream);

                            Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

                       //     mediapipeHelper.onFrame(bmp);

                            if(bmp==null || customFrameAvailableListner == null)
                                return;
                            Log.d(TAG,"Writing frame");
                            customFrameAvailableListner.onFrame(bmp);


                            stream.close();
                        } else {
                            Log.e(TAG, "image is null");
                        }
                    }catch(Exception ex){
                        Log.e("Sys","Error:"+ex.getMessage());
                    }




               //     imageView.setImageBitmap(bitmap);
               //     Log.e(TAG, "send mediapipe before");
               //     mediapipeHelper.onFrame(bitmap);
               //     Log.e(TAG, "send mediapipe after");

        }
    }

    MediapipeHelper.OnHandGestureListener onHandGestureListener = new MediapipeHelper.OnHandGestureListener() {
        @Override
        public void OnHandGestureRecognization(@Nullable String handGesture) {
            Log.e(TAG, "OnHandGesture :" + handGesture);
        }
    };

    public void setCustomFrameAvailableListner(CustomFrameAvailableListner customFrameAvailableListner){
        this.customFrameAvailableListner = customFrameAvailableListner;
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                mediapipeHelper.setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                           //     setCustomFrameAvailableListner(mediapipeHelper.getBitmapConverter());
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                mediapipeHelper.setSurface(null);
                            }
                        });
    }
}