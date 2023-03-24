package com.example.litterdetection;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import net.gotev.uploadservice.protocols.multipart.MultipartUploadRequest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    public File root_folder;

    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int PermissionRequestCode = 1;
    private static final int SELECTION_CHOICE_FROM_ALBUM_REQUEST_CODE = 2; // album selection requestCode
    private static final int EDIT_CHOICE_FROM_ALBUM_REQUEST_CODE = 3; // album selection requestCode
    private static final int REQUEST_IMAGE_CAPTURE = 4;
    private static final int REQUEST_TAKE_PHOTO = 5;
    private static final int REQUEST_OPEN_EDIT_ROOT_FOLDER = 6;


    private Uri currentPhotoUri;
    private String ImageName;
    private String currentPhotoPath;

    private Button mainCapture;
    private Button mainUpload;
    private Button mainEdit;

    private static final int pic_id = 123;

    public static final String UPLOAD_URL = "http://129.241.152.251/PlastOPol/Api.php?apicall=upload";

    String currentPhotoPathCapture;



    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        if (checkPermissions()){


            mainCapture= findViewById(R.id.MainCaptureButton);
            mainCapture.setOnClickListener(clickListener);

            mainUpload=findViewById(R.id.MainUploadButton);
            mainUpload.setOnClickListener(clickListener);

            mainEdit=findViewById(R.id.MainEditButton);
            mainEdit.setOnClickListener(clickListener);
        }


       File externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        root_folder = new File(externalFilesDir, "LitterDetection");

        if(!root_folder.exists()){
            root_folder.mkdir();
        }


    }

    private boolean checkPermissions() {
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : PERMISSIONS) {
            result = ContextCompat.checkSelfPermission(this, p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            } else {Log.d("PERMISSIONS", "Permission already granted: " + p);}
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), PermissionRequestCode);
            return false;
        }
        Toast.makeText(this, "All Permission Granted.", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionRequestCode) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission Granted.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permission Denied.", Toast.LENGTH_SHORT).show();
                    closeNow();
                }
            }
        }
    }

    private void closeNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity();
        } else {
            finish();
        }
    }

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.MainCaptureButton:
                    OpenCamera();
                    break;
                case R.id.MainUploadButton:
                    selectionChoiceFromAlbum();
                    break;
                case R.id.MainEditButton:
                    editChoiceFromAlbum();
                    break;
                default:
                    break;
            }
        }
    };

    private void OpenCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
        }

    }

    private File createImageFile() throws IOException {

        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMAG_" + timeStamp + "_";
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                root_folder      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPathCapture = image.getAbsolutePath();
        return image;
    }

    public static Uri getImageContentUri(Context context, File imageFile) {

        String filePath = imageFile.getAbsolutePath();

        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,

                new String[] { MediaStore.Images.Media._ID }, MediaStore.Images.Media.DATA + "=? ",

                new String[] { filePath }, null);

        if (cursor != null && cursor.moveToFirst()) {

            @SuppressLint("Range") int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));

            Uri baseUri = Uri.parse("content://media/external/images/media");

            return Uri.withAppendedPath(baseUri, "" + id);

        } else {

            if (imageFile.exists()) {

                ContentValues values = new ContentValues();

                values.put(MediaStore.Images.Media.DATA, filePath);

                return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            } else {

                return null;

            }

        }

    }


    private void selectionChoiceFromAlbum() {

        Intent choiceFromAlbumIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        choiceFromAlbumIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        //choiceFromAlbumIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        //choiceFromAlbumIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        choiceFromAlbumIntent.setType("*/*");
        startActivityForResult(choiceFromAlbumIntent, SELECTION_CHOICE_FROM_ALBUM_REQUEST_CODE);
    }

    private void editChoiceFromAlbum() {

        //Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        //intent.setType("*/*");
        //intent.addCategory(Intent.CATEGORY_OPENABLE);
        //startActivityForResult(intent, EDIT_CHOICE_FROM_ALBUM_REQUEST_CODE);

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");


       /* Uri externalFilesDirUri = FileProvider.getUriForFile(
                this, "com.example.litterdetection.provider", root_folder);
        Log.d("file","open failed"+externalFilesDirUri.toString());
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, externalFilesDirUri);*/

        try {
            startActivityForResult(intent, EDIT_CHOICE_FROM_ALBUM_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Log.d("file","open failed");
            e.printStackTrace();
        }


    }

    private void openFile(Uri directoryUri) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, directoryUri);
        startActivityForResult(intent, EDIT_CHOICE_FROM_ALBUM_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            // 通过返回码判断是哪个应用返回的数据
            switch (requestCode) {
                case EDIT_CHOICE_FROM_ALBUM_REQUEST_CODE:

                    Uri selectedFile = data.getData();
                    String filePath = getRealPathFromUri(selectedFile);

                    if (filePath == null){
                        filePath = selectedFile.getPath();
                    }

                    if (filePath.startsWith("/document/primary:")) {
                        filePath = filePath.replace("/document/primary:", "/sdcard/");
                    }

                    Intent intent = new Intent(this, EditActivity.class);
                    intent.putExtra("bitmap_file_path", filePath);
                    startActivity(intent);


                    Log.d("file", "chosen file path is " + filePath);
                    break;
                // upload image
                case SELECTION_CHOICE_FROM_ALBUM_REQUEST_CODE:
                    ClipData clipData = data.getClipData();

                    if(clipData != null && clipData.getItemCount() > 0) {
                        for (int i=0; i<clipData.getItemCount(); i=i+2){
                            ClipData.Item data_1 = clipData.getItemAt(i);
                            String upload_path_1 = data_1.getUri().toString();
                            ClipData.Item data_2 = clipData.getItemAt(i=1);
                            String upload_path_2 = data_2.getUri().toString();
//                            Uri item1 = clipData.getItemAt(i).getUri();
//                            Uri upload_file = DocumentFile.fromSingleUri(this, item1).getUri();
//                            String upload_path_1=getFilePathFromUri(this, upload_file);
//
//                            Uri item2 = clipData.getItemAt(i+1).getUri();
//                            Uri json_file = DocumentFile.fromSingleUri(this, item2).getUri();
//                            String upload_path_2=getFilePathFromUri(this, json_file);
//
//                            if (upload_path_1 == null){
//                                upload_path_1=getFilePathFromDocumentUri(this, upload_file);
//                            }
//                            if (upload_path_2 == null){
//                                upload_path_2=getFilePathFromDocumentUri(this, json_file);
//                            }
//                            if (upload_path_1 == null){
//                                upload_path_1=upload_file.getPath();
//                            }
//                            if (upload_path_2 == null){
//                                upload_path_2=json_file.getPath();
//                            }
                            uploadMultipart(upload_path_1,upload_path_2);
                        }
                    }
                    break;
                case REQUEST_TAKE_PHOTO:
                    Bundle extras = data.getExtras();
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    String photoFilePath = passBitmapToEdit(imageBitmap);

                    Log.d("taking pictures", "the path is " + photoFilePath);

                    // Pass the file path to the next activity
                    Intent photoIntent = new Intent(this, EditActivity.class);
                    photoIntent.putExtra("bitmap_file_path", photoFilePath);
                    startActivity(photoIntent);

                    break;
                default:
                    break;
            }
        }
    }

    private static String getAbsolutePathFromUri(Context context, Uri uri) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File file = null;
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            String fileName = getFileName(context, uri);
            file = new File(context.getCacheDir(), fileName);
            outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[4 * 1024]; // or other buffer size
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (file != null) {
            return file.getAbsolutePath();
        } else {
            return null;
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf(File.separator);
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private static String getFilePathFromUri(Context context, Uri uri) {
        String filePath = null;
        if ("content".equals(uri.getScheme())) {
            String[] projection = { "_data" };
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow("_data");
                    filePath = cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if ("file".equals(uri.getScheme())) {
            filePath = uri.getPath();
        }
        if (filePath != null) {
            File file = new File(filePath);
            if (!file.exists()) {
                return null;
            }
        }
        return filePath;
    }

    public static String getFilePathFromDocumentUri(Context context, Uri uri) {
        String documentId = DocumentsContract.getDocumentId(uri);
        String[] split = documentId.split(":");
        String type = split[0];
        String path = split[1];
        Log.d("path", path);

        if ("primary".equalsIgnoreCase(type)) {
            File externalStorageRoot = Environment.getExternalStorageDirectory();
            String externalStorageRootPath = externalStorageRoot.getAbsolutePath();
            return externalStorageRootPath+'/'+ path;
        }

        DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
        if (documentFile != null) {
            return documentFile.getUri().getPath();
        }
        return null;
    }

    private String passBitmapToEdit(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > height) {
            // Crop the image to make it a square
            int difference = width - height;
            bitmap = Bitmap.createBitmap(
                    bitmap,
                    difference / 2,
                    0,
                    height,
                    height
            );
        } else if (height > width) {
            // Crop the image to make it a square
            int difference = height - width;
            bitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    difference / 2,
                    width,
                    width
            );
        }

        // Create a file to store the bitmap
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMAG_"+timeStamp+".jpg";

        File outputFile = new File(root_folder, imageFileName);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(outputFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return outputFile.getAbsolutePath();
    }
    private String getRealPathFromUri(Uri uri) {

        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null) {
            int columnIndex = cursor.getColumnIndex(projection[0]);
            cursor.moveToFirst();
            String result = cursor.getString(columnIndex);
            cursor.close();
            return result;
        }
        return null;
    }

    private void uploadMultipart(String filePath, String jsonPath) {
        try {
            Log.d("file path", "Main file path is "+filePath);
            new MultipartUploadRequest(this, UPLOAD_URL)
                    .setMethod("POST")
                    .addFileToUpload(filePath, "image")
                    .addFileToUpload(jsonPath, "desc")
                    .setMaxRetries(0)
                    .startUpload();

        } catch (Exception exc) {
            Log.d("Upload start error", "upload error" + exc.getMessage() + exc);
        }
        finally {
            Toast.makeText(getBaseContext(), "Image Uploaded",Toast.LENGTH_SHORT).show();
        }
    }


}