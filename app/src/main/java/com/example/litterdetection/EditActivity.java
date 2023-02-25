package com.example.litterdetection;


import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lib_task_api.Detector;
import com.example.lib_task_api.TFLiteObjectDetectionAPIModel;

import org.json.JSONException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;




public class EditActivity extends AppCompatActivity {

    //elements
    private ImageView imageView;
    private Button saveButton;
    private Button cancelButton;
    private Button deletebox;
    private Button savecalculation;

    // parameter settings for detector
    private Detector detector;
    private int cropSize;
    private static float CONFIDENTIAL_LEVEL = 0.5f;
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private List<Detector.Recognition> results;
    private ArrayList<RectF>  rectresults = new ArrayList<>();
    private ArrayList<RectF>  deletedRec = new ArrayList<>();
    private ArrayList<String>  validCalculatiion = new ArrayList<>();
    private ArrayList<Integer>  deleteCalculatiion = new ArrayList<>();
    private ArrayList<Integer>  updateCalculatiion = new ArrayList<>();
    private Map<RectF, String> calculateClassification = new HashMap<RectF, String>();

    //parameters for picture viewing
    private Bitmap photo;
    private Bitmap photoAlterBitmap;
    private float downx;
    private float downy;
    private float upx;
    private  float upy;

    // Painting settings
    private Canvas canvas;
    private Paint redSolid = new Paint();
    private Paint redDash = new Paint();
    private Paint blueSolid = new Paint();
    private Paint blueDash = new Paint();
    private Paint redText = new Paint();
    private Paint blueText = new Paint();

    //editing the analyzed results
    private static final int select_box = 10;
    private static final int editing_calculated = 20;
    private static final int draw_rectf=30;
    private static final int editing_rectf=40;
    public static final int OUTSIDE = 0;
    public static final int INSIDE = 1;
    public static final int LEFT_TOP = 2;
    public static final int RIGHT_TOP = 3;
    public static final int RIGHT_BOTTOM = 4;
    public static final int LEFT_BOTTOM = 5;
    public static final int TOP_EDGE = 6;
    public static final int BOTTOM_EDGE = 7;
    public static final int LEFT_EDGE = 8;
    public static final int RIGHT_EDGE = 9;
    public static float offset =40;

    private int mouse_flag = select_box;
    private float[] positions = new float[4];
    private RectF oval = new RectF();
    private int ovalNumber = 0;
    private RectF ovalEditing = new RectF();
    private  RectF ovalDrawing = new RectF();
    private  String classification;

    //parameters when saving images
    private int annotationNum =0;
    private JSONArray jsonlist = new JSONArray();
    private JSONObject jsonObj = new JSONObject();
    private File storageDir;
    private String file_output_directory ;

    private String fileName;
    private String oldBitmapPath;



    @RequiresApi(api = Build.VERSION_CODES.Q)
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        File externalFilesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        storageDir = new File(externalFilesDir, "LitterDetection");
        file_output_directory = storageDir.toString();


        //set the elements
        imageView = findViewById(R.id.Editview);
        saveButton=findViewById(R.id.EditSave);
        cancelButton=findViewById(R.id.EditCancel);
        deletebox=findViewById(R.id.DeleteBox);
        savecalculation=findViewById(R.id.SaveCalculation);
        Button saveeditButton = findViewById(R.id.saveediting);
        Button canceleditButton = findViewById(R.id.cancelediting);
        Spinner spinner = (Spinner) findViewById(R.id.Selection);
        Button reselectButton=findViewById(R.id.reselect);

        //set the pains
        blueSolid.setColor(Color.BLUE);
        blueSolid.setStyle(Paint.Style.STROKE);
        blueSolid.setStrokeWidth(10);

        blueDash.setColor(Color.BLUE);
        blueDash.setStyle(Paint.Style.STROKE);
        blueDash.setStrokeWidth(10);
        blueDash.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        redSolid.setColor(Color.RED);
        redSolid.setStyle(Paint.Style.STROKE);
        redSolid.setStrokeWidth(10);

        redDash.setColor(Color.RED);
        redDash.setStyle(Paint.Style.STROKE);
        redDash.setStrokeWidth(10);
        redDash.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        redText = new Paint();
        redText.setColor(Color.RED);
        redText.setTextSize(50);

        blueText = new Paint();
        blueText.setColor(Color.BLUE);
        blueText.setTextSize(50);

        //build object detector
        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            this,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            //LOGGER.e(e, "Exception initializing Detector!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        Intent intent = getIntent();
        oldBitmapPath = intent.getStringExtra("bitmap_file_path");

        File oldBitmap= new File(oldBitmapPath);
        fileName = oldBitmap.getName();

        // readImageFromPath(filePath);

        Bitmap originalBitmap = BitmapFactory.decodeFile(oldBitmapPath);

        photo =  originalBitmap.copy(Bitmap.Config.ARGB_8888, true);

        int width = photo.getWidth();
        int height = photo.getHeight();
        if (width > height) {
            // Crop the image to make it a square
            int difference = width - height;
            photo = Bitmap.createBitmap(
                    photo,
                    difference / 2,
                    0,
                    height,
                    height
            );
        } else if (height > width) {
            // Crop the image to make it a square
            int difference = height - width;
            photo = Bitmap.createBitmap(
                    photo,
                    0,
                    difference / 2,
                    width,
                    width
            );
        }

        DisplayMetrics metrics2 = getResources().getDisplayMetrics();
        int screenWidth = metrics2.widthPixels;
        int screenHeight = metrics2.heightPixels;

        // Determine the correct scale factor
        int targetWidth = screenWidth;
        int targetHeight = screenHeight;
        int bitmapWidth = photo.getWidth();
        int bitmapHeight = photo.getHeight();
        float scaleFactor = Math.min((float) targetWidth / bitmapWidth, (float) targetHeight / bitmapHeight);

        // Scale the Bitmap
        Matrix sacleMatrix = new Matrix();
        sacleMatrix.postScale(scaleFactor, scaleFactor);
        photo = Bitmap.createBitmap(photo, 0, 0, bitmapWidth, bitmapHeight, sacleMatrix, true);

        photoAlterBitmap = photo.copy(Bitmap.Config.ARGB_8888, true);


        canvas = new Canvas(photoAlterBitmap);
        Matrix matrix = new Matrix();
        canvas.drawBitmap(photo,matrix,redSolid);

        Bitmap analyseImage=null;
        try {
            analyseImage = analyzePhoto(photoAlterBitmap);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        imageView.setImageBitmap(analyseImage);
        imageView.setOnTouchListener(touchListener);

        savecalculation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mouse_flag = draw_rectf;
                savecalculation.setVisibility(View.GONE);
                deletebox.setVisibility(View.GONE);
                reselectButton.setVisibility(View.GONE);
                canceleditButton.setVisibility(View.VISIBLE);
                saveeditButton.setVisibility(View.VISIBLE);

                canvas = new Canvas(photoAlterBitmap);

                for (final RectF rec : rectresults) {

                    Log.d("made next", "made next");

                    String left = Float.toString(rec.left);
                    String right = Float.toString(rec.right);
                    String top = Float.toString(rec.top);
                    String bottom = Float.toString(rec.bottom);
                    String location_box = left + ',' + right + ',' + top + ',' + bottom;

                    canvas.drawRect(rec, redSolid);
                    if (!calculateClassification.keySet().contains(rec)) {
                        calculateClassification.put(rec,"Recognized");
                    }
                    canvas.drawText(calculateClassification.get(rec), rec.left, rec.top, redText);


                    if (updateCalculatiion.contains(rectresults.indexOf(rec)) == false) {
                        JSONObject emps = new JSONObject();
                        emps.put("BOX_ID", annotationNum);
                        emps.put("type", "generated");
                        emps.put("Edited", "Delete");
                        emps.put("Location", location_box);
                        emps.put("waste_type", calculateClassification.get(rec));
                        jsonlist.add(emps);
                    } else {
                        canvas.drawRect(rec, redSolid);
                        canvas.drawText(calculateClassification.get(rec), rec.left, rec.top, redText);
                        JSONObject emps = new JSONObject();
                        emps.put("BOX_ID", annotationNum);
                        emps.put("type", "generated");
                        emps.put("Edited", "No");
                        emps.put("Location", location_box);
                        emps.put("waste_type", calculateClassification.get(rec));
                        jsonlist.add(emps);
                    }

                    annotationNum = annotationNum + 1;
                }

                for(final RectF rec : deletedRec){
                    String left = Float.toString(rec.left);
                    String right = Float.toString(rec.right);
                    String top = Float.toString(rec.top);
                    String bottom = Float.toString(rec.bottom);
                    String location_box = left + ',' + right + ',' + top + ',' + bottom;

                    JSONObject emps = new JSONObject();
                    emps.put("BOX_ID", annotationNum);
                    emps.put("type", "generated");
                    emps.put("Edited", "Delete");
                    emps.put("Location", location_box);
                    emps.put("waste_type", calculateClassification.get(rec));
                    jsonlist.add(emps);

                    annotationNum = annotationNum + 1;
                }

                imageView.setImageBitmap(photoAlterBitmap);

            }

        });

        reselectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mouse_flag == editing_calculated){
                    Bitmap croppedBitmap = photoAlterBitmap.copy(photoAlterBitmap.getConfig(), true);
                    final Canvas canvas = new Canvas(croppedBitmap);

                    for(final RectF rec : rectresults){
                        canvas.drawRect(rec, redSolid);
                        if (calculateClassification.keySet().contains(rec)) {
                            canvas.drawText(calculateClassification.get(rec), rec.left, rec.top, redText);
                        }
                        else {
                            canvas.drawText("Recognized", rec.left, rec.top, redText);
                        }
                    }
                    imageView.setImageBitmap(croppedBitmap);

                    mouse_flag=select_box;
                }
            }
        });

        deletebox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteCalculatiion.add(ovalNumber);
                deletedRec.add(ovalEditing);

                validCalculatiion.remove(ovalNumber);

                rectresults.remove(ovalNumber);

                Bitmap croppedBitmap = photoAlterBitmap.copy(photoAlterBitmap.getConfig(), true);
                final Canvas canvas = new Canvas(croppedBitmap);

                for(final RectF rec : rectresults){
                        canvas.drawRect(rec, redSolid);
                    if (calculateClassification.keySet().contains(rec)) {
                        canvas.drawText(calculateClassification.get(rec), rec.left, rec.top, redText);
                    }
                    else {
                        canvas.drawText("Recognized", rec.left, rec.top, redText);
                    }
                }
                imageView.setImageBitmap(croppedBitmap);

                //photoAlterBitmap = croppedBitmap;
                mouse_flag=select_box;
            }
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                classification=(String) parent.getItemAtPosition(position);
                if(positions[0]!=0 && classification != "Labels"){
                    Bitmap croppedBitmap=photoAlterBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    canvas = new Canvas(croppedBitmap);
                    oval.set(positions[0], positions[1],positions[2], positions[3]);
                    canvas.drawRect(oval, blueDash);
                    canvas.drawText(classification, positions[0], positions[1], blueText);
                    imageView.setImageBitmap(croppedBitmap);
                }
                if(mouse_flag == editing_calculated ){
                    Bitmap croppedBitmap=photoAlterBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    canvas = new Canvas(croppedBitmap);

                    for(final RectF rec : rectresults){
                        if(deletedRec.contains(rec)==false){
                            if(rec == ovalEditing){
                                canvas.drawRect(rec, redDash);
                                canvas.drawText(classification, rec.left, rec.top, redText);
                                calculateClassification.put(rec,classification);
                            }
                            else{
                                canvas.drawRect(rec, redSolid);
                                if (calculateClassification.keySet().contains(rec)) {
                                    canvas.drawText(calculateClassification.get(rec), rec.left, rec.top, redText);
                                }
                                else {
                                    canvas.drawText("Recognized", rec.left, rec.top, redText);
                                }
                            }
                        }
                    }


                    //ovalEditing.set(positions[0], positions[1],positions[2], positions[3]);

                    imageView.setImageBitmap(croppedBitmap);

                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        saveeditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                canvas = new Canvas(photoAlterBitmap);
                oval.set(positions[0], positions[1],positions[2], positions[3]);
                canvas.drawRect(oval, blueSolid);
                canvas.drawText(classification, positions[0], positions[1], blueText);
                imageView.setImageBitmap(photoAlterBitmap);

                canvas.save();
                mouse_flag=draw_rectf;

                annotationNum=annotationNum+1;
                String left = Float. toString(oval.left);
                String right = Float. toString(oval.right);
                String top = Float. toString(oval.top);
                String bottom = Float. toString(oval.bottom);
                String location_box=left+','+right+','+top+','+bottom;

                JSONObject emps = new JSONObject();
                emps.put("BOX_ID", annotationNum);
                emps.put("type","User_defined");
                emps.put("Edited", "No");
                emps.put("Location", location_box);
                emps.put("waste_type", classification);
                jsonlist.add(emps);

                annotationNum=annotationNum+1;
            }
        });

        canceleditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageView.setImageBitmap(photoAlterBitmap);
                mouse_flag=draw_rectf;
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(EditActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


    }

    private Bitmap analyzePhoto(Bitmap photo1) throws JSONException {

        Bitmap croppedBitmap = photo1.copy(photo1.getConfig(), true);

        results = detector.recognizeImage(croppedBitmap);

        final Canvas canvas = new Canvas(croppedBitmap);

        final List<Detector.Recognition> mappedRecognitions = new ArrayList<Detector.Recognition>();

        for (final Detector.Recognition result : results) {
            final RectF location = result.getLocation();
            //final String id=result.getId();
            Log.d("classfication","classfication is"+location.left);

            if (location != null && result.getConfidence() >= CONFIDENTIAL_LEVEL) {

                rectresults.add(location);

                canvas.drawRect(location, redSolid);
                float textleft=(location.left+location.right)/2;
                float texttop=location.top;
                canvas.drawText("Recognized", textleft, texttop, redText);

                result.setLocation(location);
                mappedRecognitions.add(result);
                validCalculatiion.add(result.getId());
            }
        }


        //imageView.setImageBitmap(croppedBitmap);
        return croppedBitmap;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private int readImageInformation(Uri uri) {

        int degree = 0;
        String imagefile= getFilePathFromUrl(this,uri);

        ExifInterface exifInterface = null;
        try {
            exifInterface = new ExifInterface(imagefile);
        } catch (IOException ex) {
            //Log.e("---->", ex.getMessage());
        }

        if (exifInterface != null) {
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                // We only recognize a subset of orientation tag values.
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
                    default:
                        break;
                }
            }

            String latValue = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String lngValue = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            String CreatedTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            String latRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String lngRef = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
            Log.d("values","CreatedTime is "+CreatedTime);

            jsonObj.put("latitude", convertRationalLatLonToFloat(latValue, latRef));
            jsonObj.put("longitude",convertRationalLatLonToFloat(lngValue, lngRef));
            jsonObj.put("createTime",CreatedTime);
        }
        return degree;



    }

    public static String getFilePathFromUrl(Context context, Uri uri) {
        String path = null;
        String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            String projection[] = {MediaStore.Images.ImageColumns.DATA};
            Cursor c = context.getContentResolver().query(uri, projection, null, null, null);
            if (c != null && c.moveToFirst()) {
                path = c.getString(0);
            }
            if (c != null)
                c.close();
        } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            path = uri.getPath();
        }
        return path;
    }

    public static float convertRationalLatLonToFloat(String rationalString, String ref) {
        if (rationalString==null || ref==null) {
            return 0;
        }

        try {
            String[] parts = rationalString.split(",");

            String[] pair;
            pair = parts[0].split("/");
            double degrees = parseDouble(pair[0].trim(), 0)
                    / parseDouble(pair[1].trim(), 1);

            pair = parts[1].split("/");
            double minutes = parseDouble(pair[0].trim(), 0)
                    / parseDouble(pair[1].trim(), 1);

            pair = parts[2].split("/");
            double seconds = parseDouble(pair[0].trim(), 0)
                    / parseDouble(pair[1].trim(), 1);

            double result = degrees + (minutes / 60.0) + (seconds / 3600.0);
            if (("S".equals(ref) || "W".equals(ref))) {
                return (float) -result;
            }
            return (float) result;
        } catch (NumberFormatException e) {
            return 0;
        } catch (ArrayIndexOutOfBoundsException e) {
            return 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    private static double parseDouble(String doubleValue, double defaultValue) {
        try {
            return Double.parseDouble(doubleValue);
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    private static Bitmap rotaingImageView(int angle, Bitmap bitmap) {
        Log.e("TAG","angle==="+angle);
        Bitmap returnBm = null;
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        try {
            returnBm = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
        }
        if (returnBm == null) {
            returnBm = bitmap;
        }
        if (bitmap != returnBm) {
            bitmap.recycle();
        }
        return returnBm;
    }

    private int FingerPosition(float upx, float upy, RectF oval){

        //if (touchEdge()) { return MOVE_ERROR; }

        if (upx<oval.right-offset&& upx>oval.left+offset && upy<oval.bottom-offset&& upy>oval.top+offset) {
            Log.d("position", "inside");
            return INSIDE;
        }

        if(Math.abs(upx-oval.left)<offset && Math.abs(upy-oval.top)<offset){
            return LEFT_TOP;
        }

        if(Math.abs(upx-oval.left)<offset && Math.abs(upy-oval.bottom)<offset){
            return LEFT_BOTTOM;
        }

        if(Math.abs(upx-oval.right)<offset && Math.abs(upy-oval.top)<offset){
            return RIGHT_TOP;
        }

        if(Math.abs(upx-oval.right)<offset && Math.abs(upy-oval.bottom)<offset){
            return RIGHT_BOTTOM;
        }

        if(Math.abs(upy-oval.top)<offset && upx<oval.right-offset&& upx>oval.left+offset){
            return TOP_EDGE;
        }

        if(Math.abs(upy-oval.bottom)<offset && upx<oval.right-offset&& upx>oval.left+offset){
            return BOTTOM_EDGE;
        }

        if(Math.abs(upx-oval.left)<offset && upy<oval.bottom+offset&& upy>oval.top-offset){
            return LEFT_EDGE;
        }

        if(Math.abs(upx-oval.right)<offset && upy<oval.bottom+offset&& upy>oval.top-offset){
            return RIGHT_EDGE;
        }

        return OUTSIDE;
    }

    private View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            Bitmap croppedBitmap = photoAlterBitmap.copy(photoAlterBitmap.getConfig(), true);
            canvas = new Canvas(croppedBitmap);

            switch(mouse_flag){
                case select_box:
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            downx = event.getX();
                            downy = event.getY();
                            break;
                        default:
                            break;
                    }
                    Log.d("result","selection result is " + deletedRec);
                    for(final RectF rec : rectresults){
                        if(deletedRec.contains(rec)==false){
                            if(FingerPosition(downx,downy,rec)==INSIDE){
                                Log.d("position", "position is inside" );
                                canvas.drawRect(rec, redDash);
                                if (calculateClassification.keySet().contains(rec)) {
                                    canvas.drawText(calculateClassification.get(rec), rec.left, rec.top, redText);
                                }
                                else {
                                    canvas.drawText("Recognized", rec.left, rec.top, redText);
                                }
                                ovalEditing=rec;
                                ovalNumber=rectresults.indexOf(ovalEditing);
                                mouse_flag=editing_calculated;
                            }
                            else{
                                canvas.drawRect(rec, redSolid);
                                if (calculateClassification.keySet().contains(rec)) {
                                    canvas.drawText(calculateClassification.get(rec), rec.left, rec.top, redText);
                                }
                                else {
                                    canvas.drawText("Recognized", rec.left, rec.top, redText);
                                }
                            }
                        }
                    }
                    imageView.setImageBitmap(croppedBitmap);
                    break;

                case editing_calculated:
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            downx = event.getX();
                            downy = event.getY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            upx = event.getX();
                            upy = event.getY();
                            switch(FingerPosition(upx,upy,ovalEditing)){
                                case INSIDE:
                                    Log.d("position", "position is inside" );
                                    float Drag_dx = upx - downx;
                                    float Drag_dy = upy - downy;
                                    downx=upx;
                                    downy=upy;
                                    ovalEditing.left=ovalEditing.left +Drag_dx;
                                    ovalEditing.right=ovalEditing.right +Drag_dx;
                                    ovalEditing.bottom=ovalEditing.bottom +Drag_dy;
                                    ovalEditing.top=ovalEditing.top +Drag_dy;
                                    break;

                                case LEFT_EDGE:
                                    float left_dx= upx - downx;
                                    downx=upx;
                                    ovalEditing.left=ovalEditing.left+left_dx;
                                    break;

                                case TOP_EDGE:
                                    float top_dy = upy - downy;
                                    downy=upy;
                                    ovalEditing.top=ovalEditing.top+top_dy;
                                    break;

                                case RIGHT_EDGE:
                                    float right_dx = upx - downx;
                                    downx=upx;
                                    ovalEditing.right=ovalEditing.right+right_dx;
                                    break;

                                case BOTTOM_EDGE:
                                    float bottom_dy = upy - downy;
                                    downy=upy;
                                    ovalEditing.bottom=ovalEditing.bottom+bottom_dy;
                                    break;

                                case LEFT_TOP:
                                    float left_top_dx = upx - downx;
                                    float left_top_dy = upy - downy;
                                    downx=upx;
                                    downy=upy;
                                    ovalEditing.left=ovalEditing.left+left_top_dx;
                                    ovalEditing.top=ovalEditing.top+left_top_dy;
                                    break;

                                case RIGHT_TOP:
                                    float right_top_dx = upx - downx;
                                    float right_top_dy = upy - downy;
                                    downx=upx;
                                    downy=upy;
                                    ovalEditing.right=ovalEditing.right+right_top_dx;
                                    ovalEditing.top=ovalEditing.top+right_top_dy;
                                    break;

                                case LEFT_BOTTOM:
                                    float left_bottom_dx = upx - downx;
                                    float left_bottom_dy = upy - downy;
                                    downx=upx;
                                    downy=upy;
                                    ovalEditing.left=ovalEditing.left+left_bottom_dx;
                                    ovalEditing.bottom=ovalEditing.bottom+left_bottom_dy;
                                    break;

                                case RIGHT_BOTTOM:
                                    float right_bottom_dx = upx - downx;
                                    float right_bottom_dy = upy - downy;
                                    downx=upx;
                                    downy=upy;
                                    ovalEditing.right=ovalEditing.right+right_bottom_dx;
                                    ovalEditing.bottom=ovalEditing.bottom+right_bottom_dy;
                                    break;
                                default: break;
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                                    break;
                        case MotionEvent.ACTION_CANCEL:
                                    break;
                        default:break;
                    }
                    updateCalculatiion.add(ovalNumber);
                    int editingObject=ovalNumber;
                    Log.d("delete result is","delete result is " + deleteCalculatiion);
                    for(final RectF rec : rectresults){
                        if(deletedRec.contains(rec)==false){
                            if(rectresults.indexOf(rec)==ovalNumber){
                                rectresults.set(ovalNumber,ovalEditing);
                                canvas.drawRect(ovalEditing, redDash);
                                calculateClassification.put(ovalEditing,classification);
                                canvas.drawText(classification, ovalEditing.left, ovalEditing.top, redText);
                            }
                            else{
                                canvas.drawRect(rec, redSolid);
                                if (calculateClassification.keySet().contains(rec)) {
                                    canvas.drawText(calculateClassification.get(rec), rec.left, rec.top, redText);
                                }
                                else {
                                    canvas.drawText("Recognized", rec.left, rec.top, redText);
                                }
                            }
                        }
                    }
                    imageView.setImageBitmap(croppedBitmap);
                    break;
                case draw_rectf:
                    croppedBitmap = photoAlterBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    canvas = new Canvas(croppedBitmap);
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downx = event.getX() ;
                            downy = event.getY();
                            positions[0] = downx;
                            positions[1] = downy;
                            break;
                        case MotionEvent.ACTION_MOVE: break;

                        case MotionEvent.ACTION_UP:
                            upx = event.getX();
                            upy = event.getY();
                            positions[2] = upx;
                            positions[3] = upy;

                            ovalDrawing.set(positions[0], positions[1], positions[2], positions[3]);

                            canvas.drawRect(ovalDrawing, blueDash);
                            canvas.drawText(classification, positions[0], positions[1], blueText);
                            imageView.setImageBitmap(croppedBitmap);

                            mouse_flag=editing_rectf;
                            break;

                        case MotionEvent.ACTION_CANCEL: break;

                    }
                    break;
                case editing_rectf:
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downx = event.getX();
                            downy = event.getY();
                            break;

                        case MotionEvent.ACTION_MOVE:
                            croppedBitmap = photoAlterBitmap.copy(Bitmap.Config.ARGB_8888, true);
                            canvas = new Canvas(croppedBitmap);
                            upx = event.getX();
                            upy = event.getY();

                            switch (FingerPosition(upx, upy, ovalDrawing)) {
                                case INSIDE:
                                    Log.d("position", "position is inside" );
                                    float Drag_dx = upx - downx;
                                    float Drag_dy = upy - downy;
                                    downx = upx;
                                    downy = upy;
                                    positions[0] = positions[0] + Drag_dx;
                                    positions[1] = positions[1] + Drag_dy;
                                    positions[2] = positions[2] + Drag_dx;
                                    positions[3] = positions[3] + Drag_dy;
                                    ovalDrawing.set(positions[0], positions[1], positions[2], positions[3]);
                                    break;

                                case LEFT_EDGE:
                                    float left_dx = upx - downx;
                                    downx = upx;
                                    positions[0] = positions[0] + left_dx;
                                    break;

                                case TOP_EDGE:
                                    float top_dy = upy - downy;
                                    downy = upy;
                                    positions[1] = positions[1] + top_dy;
                                    break;

                                case RIGHT_EDGE:
                                    float right_dx = upx - downx;
                                    downx = upx;
                                    positions[2] = positions[2] + right_dx;
                                    break;

                                case BOTTOM_EDGE:
                                    float bottom_dy = upy - downy;
                                    downy = upy;
                                    positions[3] = positions[3] + bottom_dy;
                                    break;

                                case LEFT_TOP:
                                    float left_top_dx = upx - downx;
                                    float left_top_dy = upy - downy;
                                    downx = upx;
                                    downy = upy;
                                    positions[0] = positions[0] + left_top_dx;
                                    positions[1] = positions[1] + left_top_dy;
                                    break;

                                case RIGHT_TOP:
                                    float right_top_dx = upx - downx;
                                    float right_top_dy = upy - downy;
                                    downx = upx;
                                    downy = upy;
                                    positions[2] = positions[2] + right_top_dx;
                                    positions[1] = positions[1] + right_top_dy;
                                    break;

                                case LEFT_BOTTOM:
                                    float left_bottom_dx = upx - downx;
                                    float left_bottom_dy = upy - downy;
                                    downx = upx;
                                    downy = upy;
                                    positions[0] = positions[0] + left_bottom_dx;
                                    positions[3] = positions[3] + left_bottom_dy;
                                    break;

                                case RIGHT_BOTTOM:
                                    float right_bottom_dx = upx - downx;
                                    float right_bottom_dy = upy - downy;
                                    downx = upx;
                                    downy = upy;
                                    positions[2] = positions[2] + right_bottom_dx;
                                    positions[3] = positions[3] + right_bottom_dy;
                                    break;

                            }

                            ovalDrawing.set(positions[0], positions[1], positions[2], positions[3]);
                            canvas.drawRect(ovalDrawing, blueDash);
                            canvas.drawText(classification, positions[0], positions[1], blueText);
                            imageView.setImageBitmap(croppedBitmap);
                            break;

                        case MotionEvent.ACTION_UP:
                            break;

                        case MotionEvent.ACTION_CANCEL:
                            break;
                        default:
                            break;
                    }

            }


            return true;
        }

    };

    private void save() throws IOException {
        canvas.save();
        canvas.restore();

        File folder = new File(file_output_directory);
        if (!folder.exists()) {
            boolean success = folder.mkdirs();
        }


        File file = new File(folder, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            photoAlterBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        try {
            fos.close();
//            Uri image_uri=Uri.fromFile(file);
//            Uri json_uri=Uri.fromFile(json_file);
            //uploadMultipart(image_uri.toString(),json_uri.toString());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        jsonObj.put("AnnotationNumber", annotationNum);
        jsonObj.put("Annotation",jsonlist);




        String FileName = fileName;
        if (FileName.contains("jpg")) {
            FileName = FileName.replace("jpg", "json");
        }

        String json_name = FileName;
        File json_file = new File(folder, json_name);
        FileOutputStream stream = new FileOutputStream(json_file);
        // Write the JSON data to the file
        stream.write("{\"key\":\"value\"}".getBytes());
        stream.close();
        Intent intent = new Intent();
        intent.setClass(EditActivity.this, MainActivity.class);
        startActivity(intent);


 /*       FileWriter jsonWriter=new FileWriter(json_file);
        jsonWriter.write(jsonObj.toJSONString());
        jsonWriter.close();*/

        /*try{
            jsonWriter.write(jsonObj.toJSONString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try{
                jsonWriter.flush();
                jsonWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }*/


        /*File file = new File(folder, fileName);

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                photoAlterBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            try {
                fos.close();
//            Uri image_uri=Uri.fromFile(file);
//            Uri json_uri=Uri.fromFile(json_file);
                //uploadMultipart(image_uri.toString(),json_uri.toString());
                Intent intent = new Intent();
                intent.setClass(EditActivity.this, MainActivity.class);
                startActivity(intent);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }*/



/*
        String file_name = FileName+".jpeg";
        File file = new File(file_output_directory, file_name);*/
//
//        if (!file.exists()){
//
//            if(file.mkdirs()){
//                Log.d("make file","made");
//
//            }
//
//        }

        Toast.makeText(getBaseContext(), "Image Saved" + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();


    }

    @SuppressLint("Range")
    public static Uri getMediaUriFromPath(Context context, String path) {

        Uri mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = context.getContentResolver().query(mediaUri,
                null,
                MediaStore.Images.Media.DISPLAY_NAME + "= ?",
                new String[] {path.substring(path.lastIndexOf("/") + 1)},
                null);

        Uri uri = null;
        if(cursor.moveToFirst()) {
            uri = ContentUris.withAppendedId(mediaUri,
                    cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media._ID)));
        }
        cursor.close();
        return uri;
    }

}