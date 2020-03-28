package com.example.pastiche;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseCustomLocalModel;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelInterpreterOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class StyleTransfer extends AppCompatActivity {
    private ImageView contentImage;
    private ImageView styledImageView;
    private Button styleButton;
    private Button transferButton;
    public float[][][][] style_bottleneck;
    public String contentImagePath;
    private static final int PICK_IMAGE = 1;
    public Uri imageUri;
    public Uri resultUri;
    public String bitmapPath;
    public Bitmap bitmap;
    public TextView styleTransferInstructions;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_style_transfer);
        contentImage = findViewById(R.id.contentImage);
        styledImageView = findViewById(R.id.styledImage);
        styleButton = findViewById(R.id.style_button);
        transferButton = findViewById(R.id.transfer_button);
        styleTransferInstructions = findViewById(R.id.styleTransferInstructions);

        contentImagePath = getIntent().getStringExtra("bitmapPath");
        contentImage.setImageURI(Uri.parse(contentImagePath));
    }

    private FirebaseModelInterpreter createInterpreter(FirebaseCustomLocalModel localModel) throws FirebaseMLException {
        FirebaseModelInterpreter interpreter;
        FirebaseModelInterpreterOptions options =
                new FirebaseModelInterpreterOptions.Builder(localModel).build();
        interpreter = FirebaseModelInterpreter.getInstance(options);
        return interpreter;
    }

    private FirebaseModelInputOutputOptions createStyleInputOutputOptions(String filename) throws FirebaseMLException, IOException {
        Bitmap bitmap = getBitmap(filename);
        int imageSize = bitmap.getWidth();
        bitmap.recycle();

        FirebaseModelInputOutputOptions inputOutputOptions = new FirebaseModelInputOutputOptions.Builder()
                .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, imageSize, imageSize, 3})
                .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 1, 1, 100})
                .build();
        return inputOutputOptions;
    }

    private FirebaseModelInputOutputOptions createTransferInputOutputOptions(String filename) throws FirebaseMLException, IOException {
        Bitmap bitmap = getBitmap(filename);
        int imageSize = bitmap.getWidth();
        bitmap.recycle();

        FirebaseModelInputOutputOptions inputOutputOptions = new FirebaseModelInputOutputOptions.Builder()
                .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, imageSize, imageSize, 3})
                .setInputFormat(1, FirebaseModelDataType.FLOAT32, new int[]{1, 1, 1, 100})
                .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, imageSize, imageSize, 3})
                .build();

        return inputOutputOptions;
    }

    private float[][][][] bitmapToInputArray(String filename, boolean toGrayscale) throws IOException {
        Bitmap bitmap = getBitmap(filename);
        int imageSize = bitmap.getWidth();
        System.out.println("Size: " + imageSize);
        bitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true);

        int batchNum = 0;
        float[][][][] input = new float[1][imageSize][imageSize][3];

        if (!toGrayscale) {
            for (int x = 0; x < imageSize; x++) {
                for (int y = 0; y < imageSize; y++) {
                    int pixel = bitmap.getPixel(x, y);
                    input[batchNum][x][y][0] = (Color.red(pixel)) / (float) 255;
                    input[batchNum][x][y][1] = (Color.green(pixel)) / (float) 255;
                    input[batchNum][x][y][2] = (Color.blue(pixel)) / (float) 255;
                }
            }
        } else {
            int R, G, B;

            final float GS_RED = (float) 0.299;
            final float GS_GREEN = (float) 0.587;
            final float GS_BLUE = (float) 0.114;

            for (int x = 0; x < imageSize; x++) {
                for (int y = 0; y < imageSize; y++) {
                    int pixel = bitmap.getPixel(x, y);

                    R = Color.red(pixel);
                    G = Color.green(pixel);
                    B = Color.blue(pixel);

                    R = G = B = (int) (GS_RED * R + GS_GREEN * G + GS_BLUE * B);

                    input[batchNum][x][y][0] = R / (float) 255;
                    input[batchNum][x][y][1] = G / (float) 255;
                    input[batchNum][x][y][2] = B / (float) 255;
                }
            }
        }
        bitmap.recycle();
        return input;
    }

    public void runStyleInference(String filename) throws FirebaseMLException, IOException {
        FirebaseCustomLocalModel styleModel = new FirebaseCustomLocalModel.Builder()
                .setAssetFilePath("style_predict_quantized_256.tflite")
                .build();

        FirebaseModelInterpreter firebaseInterpreter = createInterpreter(styleModel);

        float[][][][] input = bitmapToInputArray(filename, false);
        FirebaseModelInputOutputOptions inputOutputOptions = createStyleInputOutputOptions(filename);

        FirebaseModelInputs inputs = new FirebaseModelInputs.Builder()
                .add(input)
                .build();
        firebaseInterpreter.run(inputs, inputOutputOptions)
                .addOnSuccessListener(
                        new OnSuccessListener<FirebaseModelOutputs>() {
                            @Override
                            public void onSuccess(FirebaseModelOutputs result) {
                                style_bottleneck = result.getOutput(0);
                                transferButton.setEnabled(true);
                                styleTransferInstructions.setText(getResources().getString(R.string.style_transfer_instructions_transfer));
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {

                            }
                        });
    }

    public void runTransferInference(View view) throws FirebaseMLException, IOException {
        if (style_bottleneck != null) {
            String filename = getIntent().getStringExtra("bitmapPath");
            Bitmap contentImageBitmap = getBitmap(filename);
            final int imageSize = contentImageBitmap.getWidth();

            FirebaseCustomLocalModel transferModel = new FirebaseCustomLocalModel.Builder()
                    .setAssetFilePath("style_transfer_quantized_dynamic.tflite")
                    .build();
            FirebaseModelInterpreter firebaseInterpreter = createInterpreter(transferModel);
            final float[][][][] content_image = bitmapToInputArray(filename, false);

            FirebaseModelInputOutputOptions inputOutputOptions = createTransferInputOutputOptions(filename);

            FirebaseModelInputs inputs = new FirebaseModelInputs.Builder()
                    .add(content_image)
                    .add(style_bottleneck)
                    .build();

            firebaseInterpreter.run(inputs, inputOutputOptions)
                    .addOnSuccessListener(
                            new OnSuccessListener<FirebaseModelOutputs>() {
                                @Override
                                public void onSuccess(FirebaseModelOutputs result) {
                                    float[][][][] outputImage = result.getOutput(0);
                                    Bitmap styledImage = convertArrayToBitmap(outputImage, imageSize, imageSize);
                                    styledImageView.setVisibility(View.VISIBLE);
                                    contentImage.setVisibility(View.INVISIBLE);
                                    styledImageView.setImageBitmap(styledImage);
                                    styledImageView.setRotation(-90);
                                    styleTransferInstructions.setText(getResources().getString(R.string.style_transfer_instructions_again));
                                    transferButton.setEnabled(false);
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    System.out.println(e);

                                }
                            });
        } else {
            System.out.println("Style Bottleneck not yet created.");
        }
    }

    public static final Bitmap convertArrayToBitmap(float[][][][] imageArray, int imageWidth, int imageHeight) {
        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        Bitmap styledImage = Bitmap.createBitmap(imageWidth, imageHeight, conf);


        for (int x = 0; x < imageArray[0].length; ++x) {
            for (int y = 0; y < imageArray[0][0].length; ++y) {
                int color = Color.rgb(
                        (int) (imageArray[0][x][y][0] * (float) 255),
                        (int) (imageArray[0][x][y][1] * (float) 255),
                        (int) (imageArray[0][x][y][2] * (float) 255)
                );
                styledImage.setPixel(y, x, color);
            }
        }

        return styledImage;
    }

    private Bitmap getBitmap(String filename) {
        Bitmap decoded_bitmap;
        decoded_bitmap = BitmapFactory.decodeFile(filename);

        return decoded_bitmap;
    }

    public void setStyle(View view) {
        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(gallery, PICK_IMAGE);
    }

    private void imageCrop(Uri sourceUri) {
        CropImage.activity(sourceUri)
                .setGuidelines(CropImageView.Guidelines.ON)
                .setAspectRatio(1, 1)
                .setRequestedSize(600, 600)
                .start(this);
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
                        bitmap, 256, 256, false);
                String styleBitmap = saveBitmap(bitmap);
                runStyleInference(styleBitmap);
            } catch (IOException | FirebaseMLException e) {

            }
        }
    }

    public String saveBitmap(Bitmap bitmap) throws IOException {
        OutputStream fOut = null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "-StyleImage";
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
}