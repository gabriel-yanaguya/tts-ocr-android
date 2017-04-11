package ocr.ocr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.leptonica.android.Binarize;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.PageIterator;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class Main extends AppCompatActivity implements
        TextToSpeech.OnInitListener{

    private String DATA_PATH;
    private TessBaseAPI tessBaseAPI;

    private static final int CAMERA_REQUEST = 1888;
    private TextToSpeech tts;
    private Mat imageMat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DATA_PATH =  getApplicationContext().getFilesDir() + "/tessdata";

        installDataFolders();
        copyAssets();
        initTesseract();
        tts = new TextToSpeech(this, this);

        /* Camera init */
        ImageView imageView = (ImageView) this.findViewById(R.id.cameraCaptured);
        ImageButton photoButton = (ImageButton) this.findViewById(R.id.button);
        photoButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            }
        });
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                    imageMat = new Mat();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        ImageView cameraCaptured = (ImageView) this.findViewById(R.id.cameraCaptured);
        ImageView afterProcessing = (ImageView) this.findViewById(R.id.afterProccessing);
        ImageView imageProcessed = (ImageView) this.findViewById(R.id.processedImage);

        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bitmap photoFromCamera = (Bitmap) data.getExtras().get("data");

            // Mostro a imagem capturada
            cameraCaptured.setImageBitmap(photoFromCamera);

            //Processo a imagem e mostro imagem processada
            Bitmap proccessedBitmap = new Utils().proccessImageBeforeOCR(photoFromCamera, imageMat);
            afterProcessing.setImageBitmap(proccessedBitmap);

            Bitmap bitmapFromTesseract = proccessedBitmap.copy(proccessedBitmap.getConfig(), proccessedBitmap.isMutable());
            tessBaseAPI.setImage(bitmapFromTesseract);
            Bitmap bitmap = WriteFile.writeBitmap(tessBaseAPI.getThresholdedImage());
            imageProcessed.setImageBitmap(bitmap);

            String result = getOCRResult(proccessedBitmap);
            TextView textResult = (TextView)  findViewById(R.id.resultText);
            textResult.setText(result);
            System.out.println("TEXT EXTRACTED: "+ result);

            /* TTS */
            speakOut(result);

            //Foto após procedimento interno do tesseract

            Canvas canvas = new Canvas(bitmap);

            Paint paint=new Paint();


            final ResultIterator iterator = tessBaseAPI.getResultIterator();
            iterator.begin();
            do {
                int[] lastBoundingBox = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD);

                Rect rect = new Rect(lastBoundingBox[0], lastBoundingBox[1], lastBoundingBox[2], lastBoundingBox[3]);

                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);

                canvas.drawRect(rect, paint);
                canvas.drawRect(iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD), paint);
            }
            while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD));

            imageProcessed.setImageBitmap(bitmap);
        }
    }

    private void initTesseract(){

        tessBaseAPI = new TessBaseAPI();
        System.out.println(DATA_PATH + "/eng.traineddata");
        tessBaseAPI.init( getApplicationContext().getFilesDir().getAbsolutePath(), "eng");
    }

    public String getOCRResult(Bitmap bitmap) {

        tessBaseAPI.setImage(bitmap);
        String result = tessBaseAPI.getUTF8Text();

        return result;
    }

    public void onDestroy() {
        if (tessBaseAPI != null)
            tessBaseAPI.end();
    }

    private void installDataFolders() {

        createDirIfNotExists("tessdata");

        File file = new File(DATA_PATH);
        if (file.exists()){
            System.out.println("INSTALLATION DIR CREATED");
        }else{
            System.out.println("INSTALLATION DIR NOT SET");
        }
    }

    private boolean createDirIfNotExists(String path) {
        boolean ret = true;

        File file = new File(getApplicationContext().getFilesDir() + "/" + path);

        if (!file.exists()) {
            if (!file.mkdirs()) {
                ret = false;
                System.out.println(getApplicationContext().getFilesDir() + "/" + path + ": DIRECTORY NOT CREATED");
            }
        }
        return ret;
    }

    private void copyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("tessdata");
        } catch (IOException e) {
            Log.e("tag", e.getMessage());
        }

        for(String filename : files) {

            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open("tessdata/"+filename);

                out = new FileOutputStream(DATA_PATH + "/" + filename);
                copyFile(in, out);
                in.close();
                in = null;
                out.flush();
                out.close();
                out = null;
            } catch(Exception e) {
                Log.e("tag", e.getMessage());
            }
        }
    }
    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    @Override
    public void onInit(int status) {

        if (status == TextToSpeech.SUCCESS) {

            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
            } else {
                //speakOut("THE BOOK IS ON THE TABLE");
            }

        } else {
            Log.e("TTS", "Initilization Failed!");
        }

    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private void speakOut(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }
}
