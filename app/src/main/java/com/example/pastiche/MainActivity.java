package com.example.pastiche;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;

import com.google.firebase.FirebaseApp;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private ConstraintLayout constraintLayout;
    private AnimationDrawable animateGradients;
    static final int REQUEST_TAKE_PHOTO = 1;
    private ImageView appLogo;
    String currentPhotoPath;
    public String bitmapPath;
    public Bitmap bitmap;
    String croppedCameraPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);

        appLogo = findViewById(R.id.appLogo);
        constraintLayout = findViewById(R.id.constraint_layout);

        // animated gradient background
        animateGradients = (AnimationDrawable) constraintLayout.getBackground();
        animateGradients.setEnterFadeDuration(2000);
        animateGradients.setExitFadeDuration(4000);
        animateGradients.start();
    }

    public void toGallery(View view) {
        Intent intent = new Intent(MainActivity.this, ImagePicker.class);
        MainActivity.this.startActivity(intent);
    }

    public void toCamera(View view) {
        dispatchTakePictureIntent();
    }

    public void toStyleTransfer(String bitmapPath) {
        Intent intent = new Intent(MainActivity.this, StyleTransfer.class);
        intent.putExtra("bitmapPath", bitmapPath);
        MainActivity.this.startActivity(intent);
    }

    private void dispatchTakePictureIntent() {
        // take picture with camera
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.pastiche",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }


    public String saveBitmap(Bitmap bitmap) throws IOException {
        // save bitmap for cropping
        OutputStream fOut = null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "-CameraNoCrop";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        fOut = new FileOutputStream(image);

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
        fOut.flush();
        fOut.close();

        bitmapPath = image.getCanonicalPath();
        return bitmapPath;
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void imageCrop(Uri sourceUri) {
        CropImage.activity(sourceUri)
                .setGuidelines(CropImageView.Guidelines.ON)
                .setAspectRatio(1, 1)
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // if picture returned from camera, send to cropper
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            Uri uri = Uri.parse("file://" + currentPhotoPath);
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                saveBitmap(bitmap);
                bitmap.recycle();
                imageCrop(Uri.parse(bitmapPath));
            } catch (IOException e) {
                e.printStackTrace();
            }
            imageCrop(uri);
            // if pictured returned from cropper, resize and send to style transfer activity
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            Uri resultUri = result.getUri();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                bitmap = Bitmap.createScaledBitmap(
                        bitmap, 600, 600, false);
                croppedCameraPhoto = saveBitmap(bitmap);
                bitmap.recycle();
                toStyleTransfer(croppedCameraPhoto);
            } catch (IOException e) {
            }
        }
    }
}