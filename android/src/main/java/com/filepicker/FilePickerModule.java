package com.filepicker;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;

public class FilePickerModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    static final int REQUEST_LAUNCH_FILE_CHOOSER = 2;

    private final ReactApplicationContext mReactContext;

    private Callback mCallback;
    WritableMap response;

    public FilePickerModule(ReactApplicationContext reactContext) {
        super(reactContext);

        reactContext.addActivityEventListener(this);

        mReactContext = reactContext;
    }

    private boolean permissionsCheck(Activity activity) {
        int readPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        int writePermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int cameraPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);
        if (writePermission != PackageManager.PERMISSION_GRANTED
                || cameraPermission != PackageManager.PERMISSION_GRANTED
                || readPermission != PackageManager.PERMISSION_GRANTED) {
            String[] PERMISSIONS = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            };
            ActivityCompat.requestPermissions(activity, PERMISSIONS, 1);
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "FilePickerManager";
    }

    @ReactMethod
    public void showFilePicker(final ReadableMap options, final Callback callback) {
        boolean showCustomDialog = false;
        if(options != null && options.hasKey("showCustomDialog")  && options.getType("showCustomDialog") == ReadableType.Boolean){
            showCustomDialog = options.getBoolean("showCustomDialog");
        }
        Activity currentActivity = getCurrentActivity();
        response = Arguments.createMap();

        if (!permissionsCheck(currentActivity)) {
            response.putBoolean("didRequestPermission", true);
            response.putString("option", "launchFileChooser");
            callback.invoke(response);
            return;
        }

        if (currentActivity == null) {
            response.putString("error", "can't find current Activity");
            callback.invoke(response);
            return;
        }

        _launchFileChooser(callback, showCustomDialog);
    }

    private void _launchFileChooser(final Callback callback, boolean showCustomDialog){

        int requestCode;
        Intent libraryIntent;
        response = Arguments.createMap();
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            response.putString("error", "can't find current Activity");
            callback.invoke(response);
            return;
        }

        requestCode = REQUEST_LAUNCH_FILE_CHOOSER;
        libraryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        libraryIntent.setType("*/*");
        libraryIntent.addCategory(Intent.CATEGORY_OPENABLE);

        if (libraryIntent.resolveActivity(mReactContext.getPackageManager()) == null) {
            response.putString("error", "Cannot launch file library");
            callback.invoke(response);
            return;
        }

        mCallback = callback;

        if(showCustomDialog){
            openMediaSelector(getCurrentActivity());
        }else{
            try {
                currentActivity.startActivityForResult(Intent.createChooser(libraryIntent, "Select file to Upload"), requestCode);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    // NOTE: Currently not reentrant / doesn't support concurrent requests
    @ReactMethod
    public void launchFileChooser(final Callback callback) {
        this._launchFileChooser(callback, false);
    }

    /**
     * Detect the available intent and open a new dialog.
     * @param context
     */
    public void openMediaSelector(Activity context){


        Intent camIntent = new Intent("android.media.action.IMAGE_CAPTURE");
        Intent gallIntent=new Intent(Intent.ACTION_GET_CONTENT);
        gallIntent.setType("*/*");
        gallIntent.addCategory(Intent.CATEGORY_OPENABLE);


        // look for available intents
        List<ResolveInfo> info=new ArrayList<ResolveInfo>();
        List<Intent> yourIntentsList = new ArrayList<Intent>();
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> listCam = packageManager.queryIntentActivities(camIntent, 0);
        for (ResolveInfo res : listCam) {
            final Intent finalIntent = new Intent(camIntent);
            finalIntent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            yourIntentsList.add(finalIntent);
            info.add(res);
        }
        List<ResolveInfo> listGall = packageManager.queryIntentActivities(gallIntent, 0);
        for (ResolveInfo res : listGall) {
            final Intent finalIntent = new Intent(gallIntent);
            finalIntent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            yourIntentsList.add(finalIntent);
            info.add(res);
        }
        List<ActionElement> actions = new ArrayList<ActionElement>();
        actions.add(new ActionElement("Send vCard", context.getResources().getDrawable(R.drawable.superphone) ));
        for (ResolveInfo res : info) {
            actions.add(new ActionElement( res.loadLabel(context.getPackageManager()).toString() ,  res.loadIcon(context.getPackageManager())));
        }

        // show available intents
        openDialog(context,yourIntentsList,actions);
    }

    private class ActionElement{
        private String label;
        private Drawable imageResource;
        private ActionElement(String label, Drawable imageResource){
            this.label = label;
            this.imageResource = imageResource;
        }

        public Drawable getImageDrawable() {
            return imageResource;
        }

        public void setImageResource(Drawable imageResource) {
            this.imageResource = imageResource;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }


    /**
     * Open a new dialog with the detected items.
     *
     * @param context
     * @param intents
     * @param activitiesInfo
     */
    private  void openDialog(final Activity context, final List<Intent> intents,
                                   List<ActionElement> activitiesInfo) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(context, AlertDialog.THEME_HOLO_LIGHT);
        dialog.setTitle("Select an action");
        dialog.setAdapter(buildAdapter(context, activitiesInfo),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        //id 0 is vCard
                        if(id == 0){
                            response.putString("type", "Send vCard");
                            mCallback.invoke(response);
                        }else{
                            Intent intent = intents.get(id-1);
                            context.startActivityForResult(intent,REQUEST_LAUNCH_FILE_CHOOSER);
                        }
                        //currentActivity.startActivityForResult(Intent.createChooser(libraryIntent, "Select file to Upload"), requestCode);
                    }
                });

        dialog.setNeutralButton("CANCEL",
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }


    /**
     * Build the list of items to show using the intent_listview_row layout.
     * @param context
     * @param activitiesInfo
     * @return
     */
    private static ArrayAdapter<ActionElement> buildAdapter(final Context context, final List<ActionElement> activitiesInfo) {
        return new ArrayAdapter<ActionElement>(context, R.layout.intent_listview_row,R.id.title,activitiesInfo){
            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                final View view = super.getView(position, convertView, parent);
                ActionElement res=activitiesInfo.get(position);
                ImageView image=(ImageView) view.findViewById(R.id.icon);
                image.setImageDrawable(res.getImageDrawable());
                TextView textview=(TextView)view.findViewById(R.id.title);
                textview.setText(res.getLabel());
                return view;
            }
        };
    }


    // R.N > 33
    public void onActivityResult(final Activity activity, final int requestCode, final int resultCode, final Intent data) {
      onActivityResult(requestCode, resultCode, data);
    }

    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {

      //robustness code
      if (mCallback == null || requestCode != REQUEST_LAUNCH_FILE_CHOOSER) {
        return;
      }
      // user cancel
      if (resultCode != Activity.RESULT_OK) {
          response.putBoolean("didCancel", true);
          mCallback.invoke(response);
          return;
      }

      Activity currentActivity = getCurrentActivity();


       if(data.getData() == null) {

           try {
               Bundle extras = data.getExtras();
               // Assume block needs to be inside a Try/Catch block.
               String cachePath = mReactContext.getCacheDir().toString();
               OutputStream fOut = null;
               String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
               File file = new File(cachePath, timeStamp + ".jpg"); // the File to save , append increasing numeric counter to prevent files from getting overwritten.
               fOut = new FileOutputStream(file);

               Bitmap pictureBitmap = (Bitmap) extras.get("data");
               pictureBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
               fOut.flush(); // Not really required
               fOut.close(); // do not forget to close the stream

               Uri uri = Uri.fromFile(file);
               response.putString("uri", uri.toString());
               String path = null;
               path = getPath(currentActivity, uri);
               if (path != null) {
                   response.putString("path", path);
               } else {
                   path = getFileFromUri(currentActivity, uri);
                   if (!path.equals("error")) {
                       response.putString("path", path);
                   }
               }
               response.putString("type", "image/jpeg" );
               response.putString("fileName", uri.getLastPathSegment() );

               mCallback.invoke(response);

           } catch (Exception e) {
               response.putBoolean("didCancel", true);
               mCallback.invoke(response);
           }
       }else{
           Uri uri = data.getData();
           response.putString("uri", data.getData().toString());
           String path = null;
           path = getPath(currentActivity, uri);
           if (path != null) {
               response.putString("path", path);
           } else {
               path = getFileFromUri(currentActivity, uri);
               if (!path.equals("error")) {
                   response.putString("path", path);
               }
           }

           response.putString("type", currentActivity.getContentResolver().getType(uri));
           response.putString("fileName", getFileNameFromUri(currentActivity, uri));

           mCallback.invoke(response);
       }
    }




    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {

            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    private String getFileFromUri(Activity activity, Uri uri){
      //If it can't get path of file, file is saved in cache, and obtain path from there
      try {
        String filePath = activity.getCacheDir().toString();
        String fileName = getFileNameFromUri(activity, uri);
        String path = filePath + "/" + fileName;
        if(!fileName.equals("error") && saveFileOnCache(path, activity, uri)){
          return path;
        }else{
          return "error";
        }
      } catch (Exception e) {
        //Log.d("FilePickerModule", "Error getFileFromStream");
        return "error";
      }
    }

    private String getFileNameFromUri(Activity activity, Uri uri){
      Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
          final int column_index = cursor.getColumnIndexOrThrow("_display_name");
          return cursor.getString(column_index);
      }else{
        return "error";
      }
    }

    private boolean saveFileOnCache(String path, Activity activity, Uri uri){
      //Log.d("FilePickerModule", "saveFileOnCache path: "+path);
      try {
        InputStream is = activity.getContentResolver().openInputStream(uri);
        OutputStream stream = new BufferedOutputStream(new FileOutputStream(path));
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len = 0;
        while ((len = is.read(buffer)) != -1) {
            stream.write(buffer, 0, len);
        }

        if(stream!=null)
            stream.close();

        //Log.d("FilePickerModule", "saveFileOnCache done!");
        return true;

      } catch (Exception e) {
        //Log.d("FilePickerModule", "saveFileOnCache error");
        return false;
      }
    }

    // Required for RN 0.30+ modules than implement ActivityEventListener
    public void onNewIntent(Intent intent) { }

}
