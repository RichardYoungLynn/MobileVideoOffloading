package com.wzt.yolov5;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.UseCase;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;

import com.facebook.network.connectionclass.ConnectionClassManager;
import com.facebook.network.connectionclass.ConnectionQuality;
import com.facebook.network.connectionclass.DeviceBandwidthSampler;
import com.google.gson.JsonArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import wseemann.media.FFmpegMediaMetadataRetriever;

import static android.os.Environment.getExternalStorageDirectory;

public class MainActivity extends AppCompatActivity {

    public static int YOLOV5S = 1;
    public static int YOLOV4_TINY = 2;
    public static int MOBILENETV2_YOLOV3_NANO = 3;
    public static int YOLO_FASTEST_XL = 4;

    public static int USE_MODEL = YOLOV5S;
    public static boolean USE_GPU = false;

    private static final String TAG = MainActivity.class.getSimpleName();

    public static CameraX.LensFacing CAMERA_ID = CameraX.LensFacing.BACK;

    private static final int REQUEST_CAMERA = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int REQUEST_PICK_VIDEO = 3;
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA
    };
    private Toolbar toolbar;
    private ImageView resultImageView;
    private TextView tvNMS;
    private TextView tvThreshold;
    private SeekBar nmsSeekBar;
    private SeekBar thresholdSeekBar;
    private TextView tvNMNThreshold;
    private TextView tvInfo;
    private Button btnPhoto;
    private Button btnVideo;
    private double threshold = 0.3, nms_threshold = 0.7;
    private TextureView viewFinder;
    private SeekBar sbVideo;
    private SeekBar sbVideoSpeed;

    protected float videoSpeed = 1.0f;
    protected long videoCurFrameLoc = 0;
    public static int VIDEO_SPEED_MAX = 20 + 1;
    public static int VIDEO_SPEED_MIN = 1;

    private AtomicBoolean detectCamera = new AtomicBoolean(false);
    private AtomicBoolean detectPhoto = new AtomicBoolean(false);
    private AtomicBoolean detectVideo = new AtomicBoolean(false);

    private long startTime = 0;
    private long endTime = 0;
    private int width;
    private int height;

    double total_fps = 0;
    int fps_count = 0;

    protected Bitmap mutableBitmap;

    ExecutorService detectService = Executors.newSingleThreadExecutor();

    FFmpegMediaMetadataRetriever mmr;

    private int localPeopleNum=0;
    private int serverPeopleNum=0;
    private Boolean registered = false;
    private BatteryReceiver batteryReceiver = null;
    private double batteryRemainingLevel=0;
    private ConnectionChangedListener connectionChangedListener = new ConnectionChangedListener();
    private double bandwidthQuality=0;
    private Boolean getBandWidthStateEnd=false;
    private Boolean detectOnUbuntuOneTime=false;
    private double localPeopleConfidenceSum=0;
    private double serverPeopleConfidenceSum=0;
    private double localProcessTime=0;
    private double serverProcessAndTransmissionTime=0;
    private ArrayList<String> contents = new ArrayList<String>();

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        getSupportActionBar().hide();

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_CAMERA,
                    REQUEST_CAMERA
            );
            finish();
        }

        initModel();
        initViewID();
        initViewListener();
    }

    private void initModel() {
        if (USE_MODEL == YOLOV5S) {
            YOLOv5.init(getAssets(), USE_GPU);
        } else if (USE_MODEL == YOLOV4_TINY) {
            YOLOv4.init(getAssets(), 0, USE_GPU);
        } else if (USE_MODEL == MOBILENETV2_YOLOV3_NANO) {
            YOLOv4.init(getAssets(), 1, USE_GPU);
        } else if (USE_MODEL == YOLO_FASTEST_XL) {
            YOLOv4.init(getAssets(), 2, USE_GPU);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initViewID() {
        toolbar = findViewById(R.id.object_detection_tool_bar);
        resultImageView = findViewById(R.id.object_detection_imageView);
        tvNMNThreshold = findViewById(R.id.valTxtView);
        tvInfo = findViewById(R.id.tv_info);
        tvNMS = findViewById(R.id.txtNMS);
        tvThreshold = findViewById(R.id.txtThresh);
        nmsSeekBar = findViewById(R.id.nms_seek);
        thresholdSeekBar = findViewById(R.id.threshold_seek);
        btnPhoto = findViewById(R.id.photo_button);
        btnVideo = findViewById(R.id.btn_video);
        viewFinder = findViewById(R.id.view_finder);
        sbVideo = findViewById(R.id.sb_video);
        sbVideo.setVisibility(View.GONE);
        sbVideoSpeed = findViewById(R.id.sb_video_speed);
//        sbVideoSpeed.setMin(VIDEO_SPEED_MIN);
        sbVideoSpeed.setMax(VIDEO_SPEED_MAX);
        sbVideoSpeed.setVisibility(View.GONE);
    }

    private void initViewListener() {
        toolbar.setNavigationIcon(R.drawable.actionbar_dark_back_icon);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (USE_MODEL == MOBILENETV2_YOLOV3_NANO || USE_MODEL == YOLO_FASTEST_XL) {
            nmsSeekBar.setEnabled(false);
            thresholdSeekBar.setEnabled(false);
            tvNMS.setVisibility(View.GONE);
            tvThreshold.setVisibility(View.GONE);
            nmsSeekBar.setVisibility(View.GONE);
            thresholdSeekBar.setVisibility(View.GONE);
            tvNMNThreshold.setVisibility(View.GONE);
        } else if (USE_MODEL == YOLOV5S) {
            threshold = 0.3f;
            nms_threshold = 0.7f;
        }
        nmsSeekBar.setProgress((int) (nms_threshold * 100));
        thresholdSeekBar.setProgress((int) (threshold * 100));
        final String format = "THR: %.2f, NMS: %.2f";
        tvNMNThreshold.setText(String.format(Locale.ENGLISH, format, threshold, nms_threshold));
        nmsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                nms_threshold = i / 100.f;
                tvNMNThreshold.setText(String.format(Locale.ENGLISH, format, threshold, nms_threshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        thresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                threshold = i / 100.f;
                tvNMNThreshold.setText(String.format(Locale.ENGLISH, format, threshold, nms_threshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        btnPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int permission = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE);
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            MainActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            777
                    );
                } else {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("image/*");
                    startActivityForResult(intent, REQUEST_PICK_IMAGE);
                }
            }
        });

        btnVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int permission = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE);
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            MainActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            777
                    );
                } else {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("video/*");
                    startActivityForResult(intent, REQUEST_PICK_VIDEO);
                }
            }
        });

        resultImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (detectVideo.get() || detectPhoto.get()) {
                    detectPhoto.set(false);
                    detectVideo.set(false);
                    sbVideo.setVisibility(View.GONE);
                    sbVideoSpeed.setVisibility(View.GONE);
                    startCamera();
                }
            }
        });

        viewFinder.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                updateTransform();
            }
        });

        viewFinder.post(new Runnable() {
            @Override
            public void run() {
                startCamera();
            }
        });

        sbVideoSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                videoSpeed = i;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(MainActivity.this, "Video Speed:" + seekBar.getProgress(), Toast.LENGTH_SHORT).show();
            }
        });

        sbVideo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                videoCurFrameLoc = i;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                videoCurFrameLoc = seekBar.getProgress();
            }
        });
    }

    @SuppressLint("RestrictedApi")
    private void startCamera() {
        CameraX.unbindAll();
        // 1. preview
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setLensFacing(CAMERA_ID)
//                .setTargetAspectRatio(Rational.NEGATIVE_INFINITY)  // 宽高比
                .setTargetResolution(new Size(480, 640))  // 分辨率
                .build();

        Preview preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener() {
            @Override
            public void onUpdated(Preview.PreviewOutput output) {
                ViewGroup parent = (ViewGroup) viewFinder.getParent();
                parent.removeView(viewFinder);
                parent.addView(viewFinder, 0);

                viewFinder.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            }
        });
        DetectAnalyzer detectAnalyzer = new DetectAnalyzer();
        CameraX.bindToLifecycle((LifecycleOwner) this, preview, gainAnalyzer(detectAnalyzer));
    }

    private void updateTransform() {
        Matrix matrix = new Matrix();
        // Compute the center of the view finder
        float centerX = viewFinder.getWidth() / 2f;
        float centerY = viewFinder.getHeight() / 2f;

        float[] rotations = {0, 90, 180, 270};
        // Correct preview output to account for display rotation
        float rotationDegrees = rotations[viewFinder.getDisplay().getRotation()];

        matrix.postRotate(-rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix);
    }

    private UseCase gainAnalyzer(DetectAnalyzer detectAnalyzer) {
        ImageAnalysisConfig.Builder analysisConfigBuilder = new ImageAnalysisConfig.Builder();
        analysisConfigBuilder.setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE);
        analysisConfigBuilder.setTargetResolution(new Size(480, 640));  // 输出预览图像尺寸
        ImageAnalysisConfig config = analysisConfigBuilder.build();
        ImageAnalysis analysis = new ImageAnalysis(config);
        analysis.setAnalyzer(detectAnalyzer);
        return analysis;
    }

    private class DetectAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(ImageProxy image, final int rotationDegrees) {
            detectOnModel(image, rotationDegrees);
        }
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        byte[] nv21 = imagetToNV21(image);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private byte[] imagetToNV21(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ImageProxy.PlaneProxy y = planes[0];
        ImageProxy.PlaneProxy u = planes[1];
        ImageProxy.PlaneProxy v = planes[2];
        ByteBuffer yBuffer = y.getBuffer();
        ByteBuffer uBuffer = u.getBuffer();
        ByteBuffer vBuffer = v.getBuffer();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];
        // U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    private void detectOnModel(ImageProxy image, final int rotationDegrees) {
        if (detectCamera.get() || detectPhoto.get() || detectVideo.get()) {
            return;
        }
        detectCamera.set(true);
        startTime = System.currentTimeMillis();
        final Bitmap bitmapsrc = imageToBitmap(image);  // 格式转换
        if (detectService == null) {
            detectCamera.set(false);
            return;
        }
        detectService.execute(new Runnable() {
            @Override
            public void run() {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                width = bitmapsrc.getWidth();
                height = bitmapsrc.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(bitmapsrc, 0, 0, width, height, matrix, false);

                detectAndDraw(bitmap);
                showResultOnUI();
            }
        });
    }

    protected void showResultOnUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                detectCamera.set(false);
                resultImageView.setImageBitmap(mutableBitmap);
                endTime = System.currentTimeMillis();
                long dur = endTime - startTime;
                float fps = (float) (1000.0 / dur);
                total_fps = (total_fps == 0) ? fps : (total_fps + fps);
                fps_count++;
                String modelName = getModelName();
                tvInfo.setText(String.format(Locale.CHINESE,
                        "%s\nSize: %dx%d\nTime: %.3f s\nFPS: %.3f\nAVG_FPS: %.3f",
                        modelName, height, width, dur / 1000.0, fps, (float) total_fps / fps_count));
            }
        });
    }

    protected Bitmap drawBoxRects(Bitmap mutableBitmap, Box[] results) {
        localPeopleNum=0;
        localPeopleConfidenceSum=0;
        if (results == null || results.length <= 0) {
            return mutableBitmap;
        }
        Canvas canvas = new Canvas(mutableBitmap);
//        Log.e(MainActivity.class.getSimpleName(),"ImageWidth="+mutableBitmap.getWidth());
//        Log.e(MainActivity.class.getSimpleName(),"ImageHeight="+mutableBitmap.getHeight());
        final Paint boxPaint = new Paint();
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4 * mutableBitmap.getWidth() / 800.0f);
        boxPaint.setTextSize(30 * mutableBitmap.getWidth() / 800.0f);
        for (Box box : results) {
            if (box.label==0){
                localPeopleNum++;
                localPeopleConfidenceSum+=box.getScore();
            }
            boxPaint.setColor(box.getColor());
            boxPaint.setStyle(Paint.Style.FILL);
            canvas.drawText(box.getLabel() + String.format(Locale.CHINESE, " %.3f", box.getScore()), box.x0 + 3, box.y0 + 30 * mutableBitmap.getWidth() / 1000.0f, boxPaint);
            boxPaint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(box.getRect(), boxPaint);
//            Log.e(MainActivity.class.getSimpleName(),"WidthPercent="+box.x0*1.0f/mutableBitmap.getWidth());
//            Log.e(MainActivity.class.getSimpleName(),"HeightPercent="+box.y0*1.0f/mutableBitmap.getHeight());
        }
        return mutableBitmap;
    }

    protected Bitmap detectAndDraw(Bitmap image) {
        long start=System.currentTimeMillis();
        Box[] result = null;
        if (USE_MODEL == YOLOV5S) {
            result = YOLOv5.detect(image, threshold, nms_threshold);
        } else if (USE_MODEL == YOLOV4_TINY || USE_MODEL == MOBILENETV2_YOLOV3_NANO || USE_MODEL == YOLO_FASTEST_XL) {
            result = YOLOv4.detect(image, threshold, nms_threshold);
        }
        localProcessTime=(System.currentTimeMillis()-start)/1000.0;
        if (result == null) {
            detectCamera.set(false);
            return image;
        }
        if (USE_MODEL == YOLOV5S || USE_MODEL == YOLOV4_TINY || USE_MODEL == MOBILENETV2_YOLOV3_NANO || USE_MODEL == YOLO_FASTEST_XL) {
            mutableBitmap = drawBoxRects(image, result);
        }
//        for(int i=0;i<result.length;i++){
//            Log.e(TAG,"x0="+result[i].x0);
//            Log.e(TAG,"y0="+result[i].y0);
//            Log.e(TAG,"x1="+result[i].x1);
//            Log.e(TAG,"y1="+result[i].y1);
//            Log.e(TAG,"score="+result[i].score);
//            Log.e(TAG,"label="+result[i].label);
//        }
        return mutableBitmap;
    }

    protected String getModelName() {
        String modelName = "";
        if (USE_MODEL == YOLOV5S) {
            modelName = "YOLOv5s";
        } else if (USE_MODEL == YOLOV4_TINY) {
            modelName = "YOLOv4-tiny";
        } else if (USE_MODEL == MOBILENETV2_YOLOV3_NANO) {
            modelName = "MobileNetV2-YOLOv3-Nano";
        } else if (USE_MODEL == YOLO_FASTEST_XL) {
            modelName = "YOLO-Fastest-xl";
        }
        return USE_GPU ? "[ GPU ] " + modelName : "[ CPU ] " + modelName;
    }

    @Override
    protected void onDestroy() {
        detectCamera.set(false);
        detectVideo.set(false);
        if (detectService != null) {
            detectService.shutdown();
            detectService = null;
        }
        if (mmr != null) {
            mmr.release();
        }
        CameraX.unbindAll();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConnectionClassManager.getInstance().register(connectionChangedListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConnectionClassManager.getInstance().remove(connectionChangedListener);
    }

    @Override
    protected void onStop() {
        if (registered){
            unregisterReceiver(batteryReceiver);
        }
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera Permission!", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_IMAGE) {
            // photo
            runByPhoto(requestCode, resultCode, data);
        } else if (requestCode == REQUEST_PICK_VIDEO) {
            // video
            runByVideo(requestCode, resultCode, data);
        } else {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    public void runByPhoto(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Photo error", Toast.LENGTH_SHORT).show();
            return;
        }
        if (detectVideo.get()) {
            Toast.makeText(this, "Video is running", Toast.LENGTH_SHORT).show();
            return;
        }
        detectPhoto.set(true);
        final Bitmap image = getPicture(data.getData());
        if (image == null) {
            Toast.makeText(this, "Photo is null", Toast.LENGTH_SHORT).show();
            return;
        }
        CameraX.unbindAll();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true);
                width = image.getWidth();
                height = image.getHeight();

//                Log.e(TAG,"Detect on ubuntu!!!!!!!!!!!");
//                try {
//                    detectOnUbuntu(mutableBitmap);
//                } catch (FileNotFoundException e) {
//                    e.printStackTrace();
//                }
//                Log.e(TAG,"Detect end!!!!!!!!!!!");

                Log.e(TAG,"调用detectOnAndroid方法");
                getBatteryLevel();
                getBandWidthState();
                detectOnAndroid(mutableBitmap);
                Log.e(TAG, "PeopleNum="+localPeopleNum);
                Log.e(TAG, "BatteryRemainingLevel="+batteryRemainingLevel);
                Log.e(TAG, "BandwidthQuality="+bandwidthQuality);
                Log.e(TAG, "LocalPeopleConfidenceSum="+localPeopleConfidenceSum);
                Log.e(TAG, "LocalProcessTime="+localProcessTime);
                showResultOnUI();
                Log.e(TAG,"detectOnAndroid方法执行结束");

//                mutableBitmap = detectAndDraw(mutableBitmap);
//                sendState();
//                final long dur = System.currentTimeMillis() - start;
//                Log.e(TAG,"Detect end!!!!!!!!!!!");
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        String modelName = getModelName();
////                        Log.e(TAG,"Show result!!!!!!!!!!!");
//                        resultImageView.setImageBitmap(mutableBitmap);
//                        tvInfo.setText(String.format(Locale.CHINESE, "%s\nSize: %dx%d\nTime: %.3f s\nFPS: %.3f",
//                                modelName, height, width, dur / 1000.0, 1000.0f / dur));
//                    }
//                });
            }
        }, "photo detect");
        thread.start();
    }

    public void runByVideo(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Video error", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            detectOnVideo(getExternalStorageDirectory() + "/DCIM/Camera/footage1.mp4");

//            原方法
//            Uri uri = data.getData();
//            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
//            if (cursor != null) {
//                cursor.moveToFirst();
//                // String imgNo = cursor.getString(0); // 编号
//                String v_path = cursor.getString(1); // 文件路径
//                String v_size = cursor.getString(2); // 大小
//                String v_name = cursor.getString(3); // 文件名
//                detectOnVideo(v_path);
//            } else {
//                Toast.makeText(this, "Video is null", Toast.LENGTH_SHORT).show();
//            }

//            方法1
//            Uri uri = data.getData();
//            String[] filePathColumn = { MediaStore.Video.Media.DATA };
//            Cursor cursor = getContentResolver().query(uri, filePathColumn, null, null, null);
//            if (cursor != null) {
//                cursor.moveToFirst();
//                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
//                String v_path = cursor.getString(columnIndex);
//                cursor.close();
//                detectOnVideo(v_path);
//            } else {
//                Toast.makeText(this, "Video is null", Toast.LENGTH_SHORT).show();
//            }

//            方法2
//            Uri uri = data.getData();
//            detectOnVideo(uri.getPath());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Video is null", Toast.LENGTH_SHORT).show();
        }
    }

    public void detectOnVideo(final String path) {
        if (detectVideo.get()) {
            Toast.makeText(this, "Video is running", Toast.LENGTH_SHORT).show();
            return;
        }
        detectVideo.set(true);
//        Toast.makeText(MainActivity.this, "FPS is not accurate!", Toast.LENGTH_SHORT).show();
        sbVideo.setVisibility(View.VISIBLE);
        sbVideoSpeed.setVisibility(View.VISIBLE);
        CameraX.unbindAll();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                mmr = new FFmpegMediaMetadataRetriever();
                mmr.setDataSource(path);
                Log.e("MainActivity", path);
                String dur = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);  // ms
                String sfps = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE);  // fps
//                String sWidth = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);  // w
//                String sHeight = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);  // h
                String rota = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);  // rotation
                int duration = Integer.parseInt(dur);
                float fps = Float.parseFloat(sfps);
                float rotate = 0;
                if (rota != null) {
                    rotate = Float.parseFloat(rota);
                }
                sbVideo.setMax(duration * 1000);
                float frameDis = 1.0f / fps * 1000 * 1000 * videoSpeed;
                videoCurFrameLoc = 0;
                int count = 0;
                while (detectVideo.get() && (videoCurFrameLoc) < (duration * 1000)) {
                    videoCurFrameLoc = (long) (videoCurFrameLoc + frameDis);
                    sbVideo.setProgress((int) videoCurFrameLoc);
                    final Bitmap b = mmr.getFrameAtTime(videoCurFrameLoc, FFmpegMediaMetadataRetriever.OPTION_CLOSEST);
                    if (b == null) {
                        continue;
                    }
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotate);
                    width = b.getWidth();
                    height = b.getHeight();
                    final Bitmap bitmap = Bitmap.createBitmap(b, 0, 0, width, height, matrix, false);

//                    Log.e(TAG,"videoCurFrameLoc = " + videoCurFrameLoc);

//                    if(count % 2 == 0){
//                        Log.e(TAG,"Detect on android begin!!!!!!!!!!!");
//                        detectOnAndroid(bitmap);
//                        showResultOnUI();
//                        Log.e(TAG,"Detect on android end!!!!!!!!!!!");
//                        count++;
//                    }else{
//                        try {
//                            Log.e(TAG,"Detect on ubuntu begin!!!!!!!!!!!");
//                            detectOnUbuntu(bitmap);
//                            Log.e(TAG,"Detect on ubuntu end!!!!!!!!!!!");
//                        } catch (FileNotFoundException e) {
//                            e.printStackTrace();
//                        }
//                        count++;
//                    }

//                    if((videoCurFrameLoc) < (duration * 1000) / 2){
//                        Log.e(TAG,"Detect on android begin!!!!!!!!!!!");
//                        detectOnAndroid(bitmap);
//                        showResultOnUI();
//                        Log.e(TAG,"Detect on android end!!!!!!!!!!!");
//                    }else{
//                        try {
//                            Log.e(TAG,"Detect on ubuntu begin!!!!!!!!!!!");
//                            detectOnUbuntu(bitmap);
//                            Log.e(TAG,"Detect on ubuntu end!!!!!!!!!!!");
//                        } catch (FileNotFoundException e) {
//                            e.printStackTrace();
//                        }
//                    }

//                    try {
//                        Log.e(TAG,"调用detectOnUbuntu方法");
//                        detectOnUbuntu(bitmap);
//                        showResultOnUI();
//                        Log.e(TAG,"detectOnUbuntu方法执行结束");
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    }

//                    Log.e(TAG,"调用detectOnAndroid方法");
//                    getBatteryLevel();
//                    getBandWidthStateEnd=false;
//                    getBandWidthState();
//                    while(!getBandWidthStateEnd){}
//                    detectOnAndroid(bitmap);
//                    Log.e(TAG, "PeopleNum="+peopleNum);
//                    Log.e(TAG, "BatteryRemainingLevel="+batteryRemainingLevel);
//                    Log.e(TAG, "BandwidthQuality="+bandwidthQuality);
//                    Log.e(TAG, "LocalPeopleConfidenceSum="+localPeopleConfidenceSum);
//                    Log.e(TAG, "LocalProcessTime="+localProcessTime);
//                    String content=peopleNum+" "+batteryRemainingLevel+" "+bandwidthQuality+" "+localPeopleConfidenceSum+" "+localProcessTime;
//                    contents.add(content);
//                    showResultOnUI();
//                    Log.e(TAG,"detectOnAndroid方法执行结束");

                    detectOnUbuntuOneTime=false;
                    Log.e(TAG,"调用detectOnUbuntu方法");
                    try {
                        detectOnUbuntu(bitmap);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    while(!detectOnUbuntuOneTime){}
                    String content=serverPeopleNum+" "+serverPeopleConfidenceSum+" "+serverProcessAndTransmissionTime;
                    contents.add(content);
                    Log.e(TAG,"detectOnUbuntu方法执行结束");

                    frameDis = 1.0f / fps * 1000 * 1000 * videoSpeed;
                }
                mmr.release();
                Log.e(TAG, "开始写入文件");
                FileOperation.initData(contents);
                Log.e(TAG, "结束写入文件");
                if (detectVideo.get()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sbVideo.setVisibility(View.GONE);
                            sbVideoSpeed.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Video end!", Toast.LENGTH_LONG).show();
                        }
                    });
                }
                detectVideo.set(false);
            }
        }, "video detect");
        thread.start();
//        startCamera();
    }

    public void detectOnAndroid(Bitmap bitmap){
        detectAndDraw(bitmap.copy(Bitmap.Config.ARGB_8888, true));
    }

    public void detectOnUbuntu(Bitmap bitmap) throws FileNotFoundException {
        Bitmap tempBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        File file = new File(Environment.getExternalStorageDirectory(), System.currentTimeMillis()+".jpg");
        FileOutputStream fileOutStream = new FileOutputStream(file);
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutStream);
            fileOutStream.flush();
            fileOutStream.close();
            //保存图片后发送广播通知更新数据库
            Uri uri = Uri.fromFile(file);
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
            //uploadImage(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        OkHttpClient client = new OkHttpClient();

        MultipartBody.Builder requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM);

        RequestBody body = RequestBody.create(MediaType.parse("image/*"), file);
        String filename = file.getName();
        requestBody.addFormDataPart("fileImage", filename, body);

        Log.e(TAG, "向服务器端发送请求");
        long start = System.currentTimeMillis();
        Request request = new Request.Builder().url("http://81.70.252.155:8888/objectdetect/").post(requestBody.build()).tag(this).build();
        client.newBuilder().readTimeout(50000, TimeUnit.MILLISECONDS).build().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG ,"onFailure");
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
//                if (response.isSuccessful()) {
//                    InputStream is = response.body().byteStream();
//                    mutableBitmap = BitmapFactory.decodeStream(is);
////                    String str = response.body().string();
//                    Log.e(TAG, response.message());
//                    showResultOnUI();
//                } else {
//                    Log.e(TAG ,response.message() + " error : body " + response.body().string());
//                }
                long end=System.currentTimeMillis();
                serverProcessAndTransmissionTime=(end-start)/1000.0;
                Log.e(TAG, "服务器端处理时间+传输时间为："+serverProcessAndTransmissionTime);
                String str = response.body().string();
//                Log.e(TAG, str);
                JSONArray jsonArray = new JSONArray();
                try {
                    JSONObject jsonObject=new JSONObject(str);
                    jsonArray=jsonObject.getJSONArray("boxes");
                    serverPeopleConfidenceSum=jsonObject.getDouble("ServerPeopleConfidenceSum");
                    serverPeopleNum=jsonObject.getInt("ServerPeopleNum");
                    detectOnUbuntuOneTime=true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
//                Log.e(TAG, "start="+start);
//                Log.e(TAG, "end="+end);
//                Box[] result = new Box[jsonArray.length()];
//                for (int i=0;i<jsonArray.length();i++){
//                    try {
//                        JSONArray temp=jsonArray.getJSONArray(i);
//                        float x0 = (float) (temp.getDouble(1) * tempBitmap.getWidth());
//                        float y0 = (float) (temp.getDouble(2) * tempBitmap.getHeight());
//                        float x1 = (float) (temp.getDouble(3) * tempBitmap.getWidth());
//                        float y1 = (float) (temp.getDouble(4) * tempBitmap.getHeight());
//                        int label = temp.getInt(0);
//                        float score = (float) (temp.getDouble(5));
//                        Box box = new Box(x0, y0, x1, y1, label, score);
//                        result[i] = box;
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//                mutableBitmap = drawBoxRects(bitmap.copy(Bitmap.Config.ARGB_8888, true), result);
//                showResultOnUI();

//                str = str.substring(2, str.length() - 2);
//                String[] lines = str.split("], \\[");
//                int len = lines.length;
//                Box[] result = new Box[len];
//                for (int i = 0; i < len; i++){
////                    Log.e(TAG, lines[i]);
//                    String[] temp = lines[i].split(", ");
//                    float x0 = Float.parseFloat(temp[1]) * tempBitmap.getWidth();
//                    float y0 = Float.parseFloat(temp[2]) * tempBitmap.getHeight();
//                    float x1 = Float.parseFloat(temp[3]) * tempBitmap.getWidth();
//                    float y1 = Float.parseFloat(temp[4]) * tempBitmap.getHeight();
//                    int label = Integer.parseInt(temp[0]);
//                    float score = Float.parseFloat(temp[5]);
//                    Box box = new Box(x0, y0, x1, y1, label, score);
//                    result[i] = box;
//                }
//                mutableBitmap = drawBoxRects(bitmap.copy(Bitmap.Config.ARGB_8888, true), result);
//                showResultOnUI();
            }
        });
    }

    public void getBatteryLevel(){
        Log.e(TAG, "开始获取当前手机电量水平");
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryReceiver = new BatteryReceiver();
        registerReceiver(batteryReceiver, filter);
        registered=true;
        Log.e(TAG, "获取当前手机电量水平结束");
    }

    public void getBandWidthState(){
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("http://81.70.252.155:8888/testBandwidthState/").tag(this).build();
        Log.e(TAG, "开始获取当前网络带宽");
        DeviceBandwidthSampler.getInstance().startSampling();
        client.newBuilder().readTimeout(50000, TimeUnit.MILLISECONDS).build().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                DeviceBandwidthSampler.getInstance().stopSampling();
                Log.e(TAG ,"onFailure：获取当前网络带宽结束");
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                DeviceBandwidthSampler.getInstance().stopSampling();
                Log.e(TAG ,"onResponse："+response.body().string()+"，获取当前网络带宽结束");
                ConnectionQuality connectionQuality = ConnectionClassManager.getInstance().getCurrentBandwidthQuality();
                bandwidthQuality = ConnectionClassManager.getInstance().getDownloadKBitsPerSecond();
                Log.e("TAG","网络带宽结果如下：connectionQuality:"+connectionQuality+" downloadKBitsPerSecond:"+ bandwidthQuality +" kb/s");
                getBandWidthStateEnd=true;
            }
        });
    }

    public void sendState(){
        getBatteryLevel();
        getBandWidthState();
        while(!getBandWidthStateEnd){}
        Log.e(TAG, "PeopleNum="+localPeopleNum);
        Log.e(TAG, "BatteryRemainingLevel="+batteryRemainingLevel);
        Log.e(TAG, "BandwidthQuality="+bandwidthQuality);
        Log.e(TAG, "LocalPeopleConfidenceSum="+localPeopleConfidenceSum);
        Log.e(TAG, "LocalProcessTime="+localProcessTime);
        String content=localPeopleNum+" "+batteryRemainingLevel+" "+bandwidthQuality+" "+localPeopleConfidenceSum+" "+localProcessTime;
        contents.add(content);
        OkHttpClient client = new OkHttpClient();
        FormBody.Builder formBody = new FormBody.Builder();
        formBody.add("PeopleNum",localPeopleNum+"");
        formBody.add("BatteryRemainingLevel",batteryRemainingLevel+"");
        formBody.add("BandwidthQuality",bandwidthQuality+"");
        formBody.add("LocalPeopleConfidenceSum",localPeopleConfidenceSum+"");
        formBody.add("LocalProcessTime",localProcessTime+"");
        Log.e(TAG, "强化学习：向服务器端发送状态");
        Request request = new Request.Builder().url("http://81.70.252.155:8888/trainDQN/").post(formBody.build()).tag(this).build();
        client.newBuilder().readTimeout(50000, TimeUnit.MILLISECONDS).build().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG ,"onFailure");
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.e(TAG, "强化学习：服务器端返回请求");
                String str = response.body().string();
                Log.e(TAG, str);
            }
        });
    }

    public Bitmap getPicture(Uri selectedImage) {
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        if (cursor == null) {
            return null;
        }
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String picturePath = cursor.getString(columnIndex);
        cursor.close();
        Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
        if (bitmap == null) {
            return null;
        }
        int rotate = readPictureDegree(picturePath);
        return rotateBitmapByDegree(bitmap, rotate);
    }

    public int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    public Bitmap rotateBitmapByDegree(Bitmap bm, int degree) {
        Bitmap returnBm = null;
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        try {
            returnBm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(),
                    bm.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        if (returnBm == null) {
            returnBm = bm;
        }
        if (bm != returnBm) {
            bm.recycle();
        }
        return returnBm;
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 获得当前电量
            double current = intent.getExtras().getInt("level");
            // 获得总电量
            double total = intent.getExtras().getInt("scale");
            batteryRemainingLevel = current*1.0 / total;
            Log.e(TAG, "当前手机总电量为："+batteryRemainingLevel);
        }
    }

    private class ConnectionChangedListener implements ConnectionClassManager.ConnectionClassStateChangeListener {
        @Override
        public void onBandwidthStateChange(com.facebook.network.connectionclass.ConnectionQuality bandwidthState) {
            Log.e(TAG, "网络带宽更改为："+bandwidthState.toString());
        }
    }

}