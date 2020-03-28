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
    Button cropButton;
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
        cropButton = findViewById(R.id.cropButton);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        selectedImage.setMaxHeight(width);
    }

    public void toStyleTransfer(View view) {
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

        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE) {
            imageUri = data.getData();
            imageCrop(imageUri);
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            resultUri = result.getUri();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                bitmap = Bitmap.createScaledBitmap(
                        bitmap, 600, 600, false);
                saveBitmap(bitmap);
                selectedImage.setImageURI(resultUri);
                cropButton.setEnabled(true);
            } catch (IOException e) {
            }
        }
    }

    private void imageCrop(Uri sourceUri) {
        CropImage.activity(sourceUri)
                .setGuidelines(CropImageView.Guidelines.ON)
                .setAspectRatio(1, 1)
                .setRequestedSize(600, 600)
                .start(this);
    }

    public String saveBitmap(Bitmap bitmap) throws IOException{
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
