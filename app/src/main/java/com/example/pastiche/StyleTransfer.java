package com.example.pastiche;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
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
    private Button clearMaskButton;
    public float[][][][] style_bottleneck;
    public String contentImagePath;
    private static final int PICK_IMAGE = 1;
    public Uri imageUri;
    public Uri resultUri;
    public String bitmapPath;
    public Bitmap bitmap;
    public TextView styleTransferInstructions;
    private CanvasView canvasView;
    private Switch invertSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_style_transfer);
        contentImage = findViewById(R.id.contentImage);
        styledImageView = findViewById(R.id.styledImage);
        styleButton = findViewById(R.id.style_button);
        transferButton = findViewById(R.id.transfer_button);
        clearMaskButton = findViewById(R.id.clearButton);
        styleTransferInstructions = findViewById(R.id.styleTransferInstructions);
        invertSwitch = findViewById(R.id.invertSwitch);

        contentImagePath = getIntent().getStringExtra("bitmapPath");
        contentImage.setImageURI(Uri.parse(contentImagePath));
        canvasView = findViewById(R.id.canvasView);
        canvasView.setDrawingCacheEnabled(true);
    }

    private FirebaseModelInterpreter createInterpreter(FirebaseCustomLocalModel localModel) throws FirebaseMLException {
        // create Firebase Model Interpreter
        FirebaseModelInterpreter interpreter;
        FirebaseModelInterpreterOptions options =
                new FirebaseModelInterpreterOptions.Builder(localModel).build();
        interpreter = FirebaseModelInterpreter.getInstance(options);
        return interpreter;
    }

    private FirebaseModelInputOutputOptions createStyleInputOutputOptions(String filename) throws FirebaseMLException {
        // choose Firebase Model Interpreter options for Style Network
        Bitmap bitmap = getBitmap(filename);
        int imageSize = bitmap.getWidth();
        bitmap.recycle();

        // inputs and outputs based on style network input and output
        FirebaseModelInputOutputOptions inputOutputOptions = new FirebaseModelInputOutputOptions.Builder()
                .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, imageSize, imageSize, 3})
                .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 1, 1, 100})
                .build();
        return inputOutputOptions;
    }

    private FirebaseModelInputOutputOptions createTransferInputOutputOptions(String filename) throws FirebaseMLException {
        // choose Firebase Model Interpreter options for Style Transfer Network
        Bitmap bitmap = getBitmap(filename);
        int imageSize = bitmap.getWidth();
        bitmap.recycle();

        // inputs and outputs based on style transfer network inputs and output
        FirebaseModelInputOutputOptions inputOutputOptions = new FirebaseModelInputOutputOptions.Builder()
                .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, imageSize, imageSize, 3})
                .setInputFormat(1, FirebaseModelDataType.FLOAT32, new int[]{1, 1, 1, 100})
                .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, imageSize, imageSize, 3})
                .build();

        return inputOutputOptions;
    }

    private float[][][][] bitmapToInputArray(String filename) {
        // convert bitmap image to array to allow correct format for networks
        Bitmap bitmap = getBitmap(filename);
        int imageSize = bitmap.getWidth();
        System.out.println("Size: " + imageSize);
        bitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true);

        int batchNum = 0;
        float[][][][] input = new float[1][imageSize][imageSize][3];

        for (int x = 0; x < imageSize; x++) {
            for (int y = 0; y < imageSize; y++) {
                int pixel = bitmap.getPixel(x, y);
                input[batchNum][x][y][0] = (Color.red(pixel)) / (float) 255;
                input[batchNum][x][y][1] = (Color.green(pixel)) / (float) 255;
                input[batchNum][x][y][2] = (Color.blue(pixel)) / (float) 255;
            }
        }
        bitmap.recycle();
        return input;
    }

    // run style network
    public void runStyleInference(String filename) throws FirebaseMLException {
        // load style network model
        FirebaseCustomLocalModel styleModel = new FirebaseCustomLocalModel.Builder()
                .setAssetFilePath("style_predict_quantized_256.tflite")
                .build();

        // interpreter for style network created
        FirebaseModelInterpreter firebaseInterpreter = createInterpreter(styleModel);

        // input image converted to array
        float[][][][] input = bitmapToInputArray(filename);

        // options set for interpreter
        FirebaseModelInputOutputOptions inputOutputOptions = createStyleInputOutputOptions(filename);

        // add style network input
        FirebaseModelInputs inputs = new FirebaseModelInputs.Builder()
                .add(input)
                .build();

        // run interpreter
        firebaseInterpreter.run(inputs, inputOutputOptions)
                .addOnSuccessListener(
                        new OnSuccessListener<FirebaseModelOutputs>() {
                            @Override
                            public void onSuccess(FirebaseModelOutputs result) {
                                // if successful, set style bottleneck vector to output, allow style transfer
                                style_bottleneck = result.getOutput(0);
                                transferButton.setEnabled(true);
                                styleTransferInstructions.setText(getResources().getString(R.string.style_transfer_instructions_transfer));
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                e.printStackTrace();
                            }
                        });
    }

    // run style transfer  network
    public void runTransferInference(View view) throws FirebaseMLException {
        // make sure style bottleneck vector is created
        if (style_bottleneck != null) {
            styleTransferInstructions.setText(getResources().getString(R.string.style_transfer_loading));

            String filename = getIntent().getStringExtra("bitmapPath");
            final Bitmap contentImageBitmap = getBitmap(filename);
            final int imageSize = contentImageBitmap.getWidth();

            // load style transfer network model
            FirebaseCustomLocalModel transferModel = new FirebaseCustomLocalModel.Builder()
                    .setAssetFilePath("style_transfer_quantized_dynamic.tflite")
                    .build();

            // interpreter for style transfer network created
            FirebaseModelInterpreter firebaseInterpreter = createInterpreter(transferModel);

            // input image converted to array
            final float[][][][] content_image = bitmapToInputArray(filename);

            // options set for interpreter
            FirebaseModelInputOutputOptions inputOutputOptions = createTransferInputOutputOptions(filename);

            // add style transfer network inputs
            FirebaseModelInputs inputs = new FirebaseModelInputs.Builder()
                    .add(content_image)
                    .add(style_bottleneck)
                    .build();

            // run interpreter
            firebaseInterpreter.run(inputs, inputOutputOptions)
                    .addOnSuccessListener(
                            new OnSuccessListener<FirebaseModelOutputs>() {
                                @Override
                                public void onSuccess(FirebaseModelOutputs result) {
                                    // if successful, add potential mask, and display image
                                    float[][][][] outputImage = result.getOutput(0);
                                    Bitmap styledImage = convertArrayToBitmap(outputImage, imageSize, imageSize);
                                    styledImage = RotateAndFlipStyleImage(styledImage, 90);
                                    try {
                                        Bitmap preMask, path, mask, finalMaskedStyledImage, maskBitmap;
                                        if (!invertSwitch.isChecked()) {
                                            preMask = contentImageBitmap;
                                        } else {
                                            preMask = styledImage;
                                        }
                                        path = canvasView.getDrawingCache();
                                        mask = getBitmap(saveBitmap(path, "-Mask"));

                                        finalMaskedStyledImage = Bitmap.createBitmap(imageSize, imageSize, Bitmap.Config.ARGB_8888);
                                        maskBitmap = Bitmap.createScaledBitmap(mask, imageSize, imageSize, true);

                                        Canvas canvas = new Canvas(finalMaskedStyledImage);

                                        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

                                        canvas.drawBitmap(preMask, 0, 0, null);
                                        canvas.drawBitmap(maskBitmap, 0, 0, paint);

                                        paint.setXfermode(null);
                                        paint.setStyle(Paint.Style.STROKE);

                                        contentImage.setImageBitmap(finalMaskedStyledImage);
                                        canvasView.clearCanvas();

                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    clearMaskButton.setVisibility(View.INVISIBLE);
                                    styledImageView.setVisibility(View.VISIBLE);
                                    if (!invertSwitch.isChecked()) {
                                        styledImageView.setImageBitmap(styledImage);
                                    } else {
                                        styledImageView.setImageBitmap(contentImageBitmap);
                                    }
                                    canvasView.setVisibility(View.INVISIBLE);

                                    styleTransferInstructions.setText(getResources().getString(R.string.style_transfer_instructions_again));
                                    transferButton.setEnabled(false);
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    e.printStackTrace();
                                }
                            });
        } else {
            System.out.println("Style Bottleneck not yet created.");
        }
    }

    public static final Bitmap convertArrayToBitmap(float[][][][] imageArray, int imageWidth, int imageHeight) {
        // convert array to bitmap image
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
        // only allow square crops
        CropImage.activity(sourceUri)
                .setGuidelines(CropImageView.Guidelines.ON)
                .setAspectRatio(1, 1)
                .setRequestedSize(600, 600)
                .start(this);
    }

    public static Bitmap RotateAndFlipStyleImage(Bitmap styledImage, float angle) {
        // rotate and flip styledimage after output and conversion

        Matrix matrix = new Matrix();
        // rotate image
        matrix.postRotate(angle);
        float cx = styledImage.getWidth() / 2f;
        float cy = styledImage.getHeight() / 2f;

        // flip image
        matrix.postScale(-1, 1, cx, cy);
        return Bitmap.createBitmap(styledImage, 0, 0, styledImage.getWidth(), styledImage.getHeight(), matrix, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // if style image selected, send to cropper
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE) {
            imageUri = data.getData();
            imageCrop(imageUri);
            // once cropped, save bitmap, run style network inference
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            resultUri = result.getUri();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                bitmap = Bitmap.createScaledBitmap(
                        bitmap, 256, 256, false);
                String styleBitmap = saveBitmap(bitmap, "-StyleImage");
                runStyleInference(styleBitmap);
            } catch (IOException | FirebaseMLException e) {
                e.printStackTrace();
            }
        }
    }

    public String saveBitmap(Bitmap bitmap, String type) throws IOException {
        // save bitmap image
        OutputStream fOut = null;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + type;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        fOut = new FileOutputStream(image);

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
        fOut.flush();
        fOut.close();

        bitmapPath = image.getCanonicalPath();
        return bitmapPath;
    }

    public void clearCanvas(View view) {
        canvasView.clearCanvas();
    }
}