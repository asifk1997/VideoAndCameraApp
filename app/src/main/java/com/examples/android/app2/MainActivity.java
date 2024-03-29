package com.examples.android.app2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION_RESULT=0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT=1;
    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice=camera;
            if (mIsRecording)
            {
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                startRecord();
                mMediaRecorder.start();
            }
            else
            {
                startPreview();
            }
            //startPreview();
            //Toast.makeText(getApplicationContext(),"Camera connection made!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice=null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice=null;
        }
    };
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize;
    private Size mVideoSize;
    private int mTotalRotation;
    private MediaRecorder mMediaRecorder;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private ImageButton mRecordImageButton;
    private boolean mIsRecording=false;
    private File mVideoFolder;
    private String mVideoFileName;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,0);
        ORIENTATIONS.append(Surface.ROTATION_90,90);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);
    }


    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size o1, Size o2) {
            return Long.signum((long)o1.getWidth()*o1.getHeight()/
                    (long)o2.getWidth()*o2.getHeight());
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createVideoFolder();
        mMediaRecorder = new MediaRecorder();
        mTextureView = (TextureView)findViewById(R.id.textureView);
        mRecordImageButton = (ImageButton)findViewById(R.id.videoOnlineImageButton);
        mRecordImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsRecording)
                {
                    mIsRecording= false;
                    mRecordImageButton.setImageResource(R.mipmap.video_green);
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    startPreview();
                }
                else
                {
                    checkWriteStoragePermission();

                }
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable())
        {
            setupCamera(mTextureView.getWidth(),mTextureView.getHeight());
            connectCamera();
        }
        else
        {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode== REQUEST_CAMERA_PERMISSION_RESULT)
        {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(getApplicationContext(),"Application wont run without camera servces",Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT)
        {
            if (grantResults[0]==PackageManager.PERMISSION_GRANTED)
            {
                mIsRecording= true;
                mRecordImageButton.setImageResource(R.mipmap.video_red);
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Toast.makeText(this,"permission successfully granted!",Toast.LENGTH_SHORT).show();
            }
            else
            {
                Toast.makeText(this,"App needs to save video to run",Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause()
    {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocas)
    {
        super.onWindowFocusChanged(hasFocas);
        View decorView = getWindow().getDecorView();
        if (hasFocas)
        {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
    }

    private void setupCamera(int width,int height)
    {
        CameraManager cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId: cameraManager.getCameraIdList())
            {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT)
                {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                mTotalRotation = sensorToDeviceRotation(cameraCharacteristics,deviceOrientation);
                boolean swapRotation = mTotalRotation == 90 || mTotalRotation ==270;
                int rotatedWidth=width;
                int rotatedHeight=height;
                if (swapRotation)
                {
                    rotatedWidth=height;
                    rotatedHeight=width;
                }
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedWidth,rotatedHeight);
                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class),rotatedWidth,rotatedHeight);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void connectCamera()
    {
        CameraManager cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.M)
            {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                {
                    cameraManager.openCamera(mCameraId,mCameraDeviceStateCallback,mBackgroundHandler);
                }
                else
                {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                    {
                        Toast.makeText(this,"Video app required access to camera",Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSION_RESULT);
                }
            }
            else
            {
                cameraManager.openCamera(mCameraId,mCameraDeviceStateCallback,mBackgroundHandler);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecord()
    {
        try {
            setupMediaRecorder();
            SurfaceTexture surfaceTexture  = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            Surface recordSurface = mMediaRecorder.getSurface();
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(mCaptureRequestBuilder.build(),null,null );
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            },null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void startPreview(){
        SurfaceTexture surfaceTexture  = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(mCaptureRequestBuilder.build(),null,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(getApplicationContext(),"Unable to setup camera preview",Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera()
    {
        if (mCameraDevice!=null)
        {
            mCameraDevice.close();
            mCameraDevice=null;
        }
    }

    private void startBackgroundThread()
    {
        mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread()
    {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread=null;
            mBackgroundHandler=null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics,int deviceOrientation)
    {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation+deviceOrientation+360)%360;
    }

    private static Size chooseOptimalSize(Size[] choices,int width,int height)
    {
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option:choices)
        {
            if (option.getHeight() == option.getWidth()* height/ width &&
                    option.getWidth()>=width && option.getHeight()>=height)
            {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size()>0)
        {
            return Collections.min(bigEnough,new CompareSizeByArea());
        }
        else
        {
            return choices[0];
        }
    }

    private void createVideoFolder()
    {
        File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        mVideoFolder = new File(movieFile,"camera2VideoImage");
        if (!mVideoFolder.exists())
        {
            mVideoFolder.mkdirs();
        }
    }

    private File createVideoFileName() throws IOException
    {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "VIDEO_"+timestamp+"_";
        File videoFile = File.createTempFile(prepend,".mp4",mVideoFolder);
        mVideoFileName = videoFile.getAbsolutePath();
        return  videoFile;
    }

    private void checkWriteStoragePermission()
    {
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.M)
        {
            if (ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED)
            {
                mIsRecording= true;
                mRecordImageButton.setImageResource(R.mipmap.video_red);
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                startRecord();
                mMediaRecorder.start();
            }
            else
            {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                {
                    Toast.makeText(this,"app needs to be able to save videos",Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }
        }
        else
        {
            mIsRecording= true;
            mRecordImageButton.setImageResource(R.mipmap.video_red);
            try {
                createVideoFileName();
            } catch (IOException e) {
                e.printStackTrace();
            }
            startRecord();
            mMediaRecorder.start();
        }
    }

    private void setupMediaRecorder() throws IOException
    {
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mVideoFileName);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(),mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }
}
