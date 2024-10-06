package com.mordechay.androidCustomUpdater;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements Callback<Structure> {

    private static final String API_URL = "http://192.168.29.141:8000/";
    private TextView user_text_view;
    private LinearLayout btn_lnr_layout;
    private ProgressBar progressBar;


    private ArrayList<Button> button_list;
    private Button select_button;
    private Drawable drwBtn;
    private String[] url_list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        user_text_view = findViewById(R.id.user_text_view);
        btn_lnr_layout = findViewById(R.id.button_linear_layout);

        progressBar = findViewById(R.id.progressBar);


        updateInfo();
    }

    private void updateInfo() {
        // יצירת אובייקט Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_URL) // ה-Base URL של ה-API
                .addConverterFactory(GsonConverterFactory.create()) // שימוש ב-Gson להמרת JSON
                .build();

        // יצירת אובייקט של השירות
        ApiEvent apiService = retrofit.create(ApiEvent.class);

        // ביצוע הבקשה
        Call<Structure> call = apiService.getVersionInfo();
        call.enqueue(this);
    }


    // מטפל בתשובה מה-API
    @Override
    public void onResponse(@NonNull Call<Structure> call, @NonNull Response<Structure> response) {
        if (response.isSuccessful()) {
            Structure version_structure = response.body();
            assert version_structure != null;
            url_list = version_structure.getUrl_download();

            MainActivity.this.runOnUiThread(() -> {
                user_text_view.setText(version_structure.getVersion_information());
                btn_lnr_layout.removeAllViews();
                button_list = new ArrayList<>();
                for (int i = 0; i < version_structure.getButton_texts().length; i++) {
                    button_list.add(new Button(MainActivity.this));
                    button_list.get(i).setText(version_structure.getButton_texts()[i]);
                    int finalI = i;
                    button_list.get(i).setOnClickListener(v -> handleBtnDownload(finalI));
                    btn_lnr_layout.addView(button_list.get(i));
                }
            });
        } else {
            Log.e("MainActivity", "Response failed with code: " + response.code());
        }
    }


    @Override
    public void onFailure(@NonNull Call<Structure> call, @NonNull Throwable t) {
        Log.e("API Error", "Failed to execute request", t);  // כתוב לוג על השגיאה

        // בדוק האם מדובר בבעיה עם ה-UI כדי למנוע קריסות
        if (!MainActivity.this.isFinishing()) {
            MainActivity.this.runOnUiThread(() -> {
                // הצג הודעת שגיאה ידידותית למשתמש
                Toast.makeText(MainActivity.this, "Request failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            });
            updateInfo();
        }
    }


    private void downloadFile(String url) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                // Get the file size
                long fileSize = response.body().contentLength();
                long totalBytesRead = 0;

                // Save to a temporary file
                File tempFile = new File(getCacheDir(), "update.zip");
                try (InputStream inputStream = response.body().byteStream();
                     FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[2048];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        // Update progress bar
                        int progress = (int) (totalBytesRead * 100 / fileSize);
                        runOnUiThread(() -> progressBar.setProgress(progress));
                    }
                }

                select_button.setOnClickListener(v -> handleBtnUnzip(tempFile));
                runOnUiThread(() -> {
                    selectButton(select_button, true, "התקן");
                });
            }

            private void handleBtnUnzip(File zipFile) {
                // שים את ההתקנה ב-Worker Thread כדי למנוע חסימת ה-UI Thread
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                executorService.execute(() -> {
                    runOnUiThread(() -> {
                        selectButton(select_button, false, "מתקין...");
                    });
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    extractZip(zipFile);
                });
            }

        });
    }

    private void extractZip(File zipFile) {
        File outputDir = new File(getCacheDir(), "extracted");
        outputDir.mkdirs();

        try (ZipFile zip = new ZipFile(zipFile)) {
            // חישוב גודל הקובץ הכולל
            Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
            long totalSize = 0;
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                totalSize += entry.getSize();
            }

            // איפוס הפרוגרס
            progressBar.setProgress(0);
            progressBar.setMax(100);

            // התחלת החילוץ עם מעקב אחרי ההתקדמות
            long extractedSize = 0;
            entries = zip.getEntries(); // איתחול מחדש של הרשימה
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File outputFile = new File(outputDir, entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    try (InputStream inputStream = zip.getInputStream(entry);
                         FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                        byte[] buffer = new byte[2048];
                        int bytesRead;

                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            extractedSize += bytesRead;

                            // חישוב אחוזי ההתקדמות ועדכון הפרוגרס
                            int progress = (int) ((extractedSize * 100) / totalSize);
                            progressBar.setProgress(progress);
                        }
                    }
                }
            }

            // הפעלת הפקודה עם הרשאות רוט
            Thread.sleep(2000);
            executeRootCommand(outputDir);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    private void executeRootCommand(File outputDir) {
        runOnUiThread(() -> user_text_view.setText("מתקין...\n"));
        File scriptFile = new File(outputDir, "main.sh");

        try {
            // Make the script executable
            Process chmodProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "chmod +x " + scriptFile.getAbsolutePath()});
            chmodProcess.waitFor();

            // Create and execute the main.sh script
            ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", "sh", scriptFile.getAbsolutePath());
            processBuilder.redirectErrorStream(true);
            Process execProcess = processBuilder.start();

            // Handle output in real-time
            BufferedReader reader = new BufferedReader(new InputStreamReader(execProcess.getInputStream()));
            AtomicReference<String> lineRef = new AtomicReference<>(); // Use AtomicReference to hold line

            // Create a separate thread for reading output to avoid blocking
            new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lineRef.set(line); // Set the line value
                        Log.d("ShellOutput", line);
                        String finalLine = line;
                        runOnUiThread(() -> {
                            String tmpText = user_text_view.getText().toString();
                            user_text_view.setText(tmpText + "\n" + finalLine);
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Wait for the process to finish
            execProcess.waitFor();

            runOnUiThread(() -> {
                String text = user_text_view.getText().toString();
                user_text_view.setText(text + "\nההתקנה הסתיימה");
                select_button.setOnClickListener(v -> finish());
                selectButton(select_button, true, "סיום");
            });

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleBtnDownload(int index) {
        select_button = button_list.get(index);
        for (int i = 0; i < button_list.size(); i++) {
            if (i != index)
                btn_lnr_layout.removeView(button_list.get(i));
        }
        button_list = null;

        runOnUiThread(() -> {
            String text = select_button.getText().toString();
            selectButton(select_button, false, "המתן להורדת הגרסה שנבחרה:\n" + text);
        });
        downloadFile(url_list[index]);
    }

    private void selectButton(Button btn, boolean enable, String text) {
        btn.setText(text);
        if (!enable) {
            drwBtn = btn.getBackground();
            btn.setBackgroundColor(Color.rgb(51, 153, 255));
            btn.setEnabled(false);
        } else {
            btn.setBackground(drwBtn);
            btn.setEnabled(true);
        }
    }
}