package com.ssoliwal.image.demo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ssoliwal.image.FFImageView;

import java.io.IOException;

/**
 * This is main activity for this demo app.
 *
 * @author Shailesh Soliwal
 */

public class MainActivity extends Activity {
    private final int PHOTO_GALLERY = 0;
    private static final int REQUEST_STORAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        requestStoragePermission();

        FFImageView sv2 = (FFImageView) findViewById(R.id.image);
        if (sv2 != null) {
            sv2.setBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.sample));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        FFImageView sv2 = (FFImageView) findViewById(R.id.image);
        if (sv2 != null) {
            sv2.clear();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // action with ID action_refresh was selected
            case R.id.rotate:
                FFImageView sv2 = (FFImageView) findViewById(R.id.image);
                if (sv2 != null) {
                    sv2.rotateImage(90, true);
                    sv2.invalidate();
                }
                break;
            case R.id.gallery:
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_PICK);
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PHOTO_GALLERY);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == PHOTO_GALLERY) {
                Uri selectedImageUri = data.getData();
                int rotation = 0;

                //get real path to find orientation degree
                String realPath = RealPathUtil.getRealPath(getApplicationContext(), selectedImageUri);
                if (realPath != null && (realPath.endsWith(".jpg") || realPath.endsWith(".png") || realPath.endsWith(".jpeg") || realPath.endsWith(".bmp"))) {
                    ExifInterface exifInterface = null;
                    try {
                        exifInterface = new ExifInterface(realPath);
                        int degree = Integer.parseInt(exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION));
                        if (degree == ExifInterface.ORIENTATION_NORMAL)
                            rotation = 0;
                        else if (degree == ExifInterface.ORIENTATION_ROTATE_90)
                            rotation = -270;
                        else if (degree == ExifInterface.ORIENTATION_ROTATE_180)
                            rotation = -180;
                        else if (degree == ExifInterface.ORIENTATION_ROTATE_270)
                            rotation = -90;

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    showImageFormatError();
                    return;
                }

                final FFImageView sv2 = (FFImageView) findViewById(R.id.image);
                if (sv2 != null) {
                    try {
                        byte[] bytes = Utils.readBytes(getApplicationContext(), selectedImageUri);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 1;
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        sv2.clear();
                        sv2.setBitmap(bitmap);
                        sv2.invalidate();

                        final int finalRotation = rotation;
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                sv2.rotateImage(finalRotation, false);
                            }
                        }, 200);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void showImageFormatError() {
        Utils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
                builder.setMessage("Image Corrupoted or not supported")
                        .setCancelable(false)
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });
                final AlertDialog alert = builder.create();
                alert.show();

                Utils.keepDialogOrientation(alert);
            }
        });
    }

    private void requestStoragePermission() {
        System.out.println("StoragePerm::" + ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE));

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        } else {
            // permission has not been granted yet. Request it directly.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                } else {
                    // permission denied, boo!
                }
                return;
            }
        }
    }
}