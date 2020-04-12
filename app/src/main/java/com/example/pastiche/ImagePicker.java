package com.example.pastiche;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImagePicker extends AppCompatActivity {
    ImageView selectedImage;
    Button pickImageButton;
    Button continueButton;
    private static final int PICK_IMAGE = 1;
    public Uri imageUri;
    public Uri resultUri;
    public String bitmapPath;
    public Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_picker);

        selectedImage = findViewById(R.id.selectedImage);
        pickImageButton = findViewById(R.id.pickImageButton);
        continueButton = findViewById(R.id.continueButton);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        selectedImage.setMaxHeight(width);
    }

    public void toStyleTransfer(View view) {
        // go to style transfer activity with bitmap ready
        Intent intent = new Intent(ImagePicker.this, StyleTransfer.class);
        intent.putExtra("bitmapPath", bitmapPath);
        ImagePicker.this.startActivity(intent);
    }

    public void openGallery(View view) {
        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(gallery, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // if image is selected from gallery, send to cropper
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            imageUri = data.getData();
            imageCrop(imageUri);
            // once cropped, save bitmap, allow user to continue to style transfer
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            resultUri = result.getUri();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                bitmap = Bitmap.createScaledBitmap(
                        bitmap, 600, 600, false);
                saveBitmap(bitmap);
                selectedImage.setImageURI(resultUri);
                continueButton.setEnabled(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void imageCrop(Uri sourceUri) {
        // only allow square crops
        CropImage.activity(sourceUri)
                .setGuidelines(CropImageView.Guidelines.ON)
                .setAspectRatio(1, 1)
                .setRequestedSize(600, 600)
                .start(this);
    }

    public String saveBitmap(Bitmap bitmap) throws IOException {
        // save bitmap
        OutputStream fOut = null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "-ContentImage";
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
        System.out.println(bitmapPath);
        return bitmapPath;
    }
}
