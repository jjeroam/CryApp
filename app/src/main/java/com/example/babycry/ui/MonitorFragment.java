package com.example.babycry.ui;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.babycry.R;
import com.example.babycry.helper.DatabaseHelper;
import com.google.gson.Gson;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MonitorFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "MonitorFragment";
    private static final String CRY_MODEL_PATH = "crymodel44.tflite";
    private static final String YAMNET_MODEL_PATH = "yamnet.tflite";

    private HandlerThread streamThread;
    private Handler streamHandler;
    private ImageView monitor;
    private Button connectButton, recordPiButton;
    private boolean isStreaming = false;
    private MediaPlayer mediaPlayer;
    private AudioClassifier cryClassifier;
    private AudioClassifier yamnetClassifier;
    private TensorAudio tensor;
    private View rootView;
    private DatabaseHelper dbHelper;
    private String raspberryPiIp = "192.168.254.151";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_monitor, container, false);

        connectButton = rootView.findViewById(R.id.connect);
        recordPiButton = rootView.findViewById(R.id.record_pi);
        monitor = rootView.findViewById(R.id.monitor);

        connectButton.setOnClickListener(this);
        recordPiButton.setOnClickListener(this);

        Button ipSettingsButton = rootView.findViewById(R.id.ip_settings_button);
        ipSettingsButton.setOnClickListener(v -> showIpInputDialog());

        dbHelper = new DatabaseHelper(getContext());

        streamThread = new HandlerThread("http-stream");
        streamThread.start();
        streamHandler = new StreamHandler(streamThread.getLooper());

        try {
            yamnetClassifier = AudioClassifier.createFromFile(requireContext(), YAMNET_MODEL_PATH);
            cryClassifier = AudioClassifier.createFromFile(requireContext(), CRY_MODEL_PATH);
            tensor = cryClassifier.createInputTensorAudio();
        } catch (Exception e) {
            showAlertDialog("Model Load Error", "Error loading model: " + e.getMessage());
        }

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isStreaming = false;
        streamThread.quitSafely();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.connect) {
            if (!isStreaming) {
                isStreaming = true;
                streamHandler.sendEmptyMessage(200);
                startAudioStream();
                connectButton.setText("Stop Monitoring");
                showAlertDialog("Connecting", "Connecting to the Raspberry Pi...");
            } else {
                isStreaming = false;
                stopAudioStream();
                connectButton.setText("Monitor Now");
                showAlertDialog("Monitoring Stopped", "The monitoring has been stopped.");
            }
        } else if (v.getId() == R.id.record_pi) {
            startTimedRecording();
        }
    }

    private class StreamHandler extends Handler {
        public StreamHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            streamVideo();
        }
    }

    private void showIpInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Set Raspberry Pi IP Address");

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_ip_input, null);
        TextView ipInput = dialogView.findViewById(R.id.ip_edit_text);
        ipInput.setText(raspberryPiIp);

        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            raspberryPiIp = ipInput.getText().toString().trim();
            showAlertDialog("IP Updated", "New IP: " + raspberryPiIp);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }

    private void streamVideo() {
        try {
            URL url = new URL("http://" + raspberryPiIp + ":8080/stream.mjpg");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                MJPEGInputStream mjpegInputStream = new MJPEGInputStream(inputStream);

                while (isStreaming) {
                    Bitmap frame = mjpegInputStream.readFrame();
                    if (frame != null && getActivity() != null) {
                        getActivity().runOnUiThread(() -> monitor.setImageBitmap(frame));
                    }
                }
                mjpegInputStream.close();
            } else {
                showAlertDialog("Stream Error", "Unable to connect to the stream.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Stream error: ", e);
            showAlertDialog("Stream Connection Error", e.getMessage());
        }
    }

    private void startAudioStream() {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource("http://" + raspberryPiIp + ":8000/mystream");
            mediaPlayer.setLooping(true);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (Exception e) {
            showAlertDialog("Audio Stream Error", e.getMessage());
            Log.e(TAG, "Audio Stream Error: ", e);
        }
    }

    private void stopAudioStream() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void startTimedRecording() {
        recordPiButton.setEnabled(false);
        recordPiButton.setText("Recording...");

        new Thread(() -> {
            try {
                // Step 1: Trigger recording on Raspberry Pi
                URL recordUrl = new URL("http://" + raspberryPiIp + ":8080/record-audio");
                HttpURLConnection recordConn = (HttpURLConnection) recordUrl.openConnection();
                recordConn.setRequestMethod("GET");
                recordConn.setConnectTimeout(10000);
                recordConn.setReadTimeout(15000);
                recordConn.connect();

                if (recordConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    // Wait for the Raspberry Pi to finish recording
                    Thread.sleep(2000); // Optional: tune this if needed
                    recordConn.disconnect();

                    // Step 2: Download cry.wav
                    URL audioUrl = new URL("http://" + raspberryPiIp + ":8080/cry.wav");
                    HttpURLConnection audioConn = (HttpURLConnection) audioUrl.openConnection();
                    audioConn.setRequestMethod("GET");
                    audioConn.setConnectTimeout(10000);
                    audioConn.setReadTimeout(15000);
                    audioConn.connect();

                    if (audioConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = audioConn.getInputStream();
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] temp = new byte[1024];
                        int read;
                        while ((read = inputStream.read(temp)) != -1) {
                            buffer.write(temp, 0, read);
                        }
                        inputStream.close();
                        audioConn.disconnect();

                        byte[] audioData = buffer.toByteArray();

                        // Step 3: Use YAMNet to check if it's a cry
                        boolean isCry = classifyWithYamnet(audioData);
                        if (isCry) {
                            float[] floatData = convertToFloatArray(audioData);
                            tensor.load(floatData);
                            List<Classifications> results = cryClassifier.classify(tensor);
                            List<Category> categories = results.get(0).getCategories();

                            getActivity().runOnUiThread(() -> {
                                showClassificationResults(categories);
                                showAlertDialog("Sound Detected", "Classification complete. View the results.");
                            });

                            saveRecordingHistory(categories);
                        } else {
                            getActivity().runOnUiThread(() ->
                                    showAlertDialog("No Cry Detected", "This does not appear to be a cry.")
                            );
                        }
                    } else {
                        showAlertDialog("Download Failed", "Unable to fetch cry.wav from Raspberry Pi.");
                    }

                } else {
                    showAlertDialog("Recording Failed", "Failed to trigger recording on Raspberry Pi.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Recording Error: ", e);
                showAlertDialog("Recording Error", e.getMessage());
            } finally {
                resetRecordButton();
            }
        }).start();
    }


    private boolean classifyWithYamnet(byte[] audioData) {
        try {
            float[] floatData = convertToFloatArray(audioData);
            TensorAudio tensorAudio = yamnetClassifier.createInputTensorAudio();
            tensorAudio.load(floatData);
            List<Classifications> yamnetResults = yamnetClassifier.classify(tensorAudio);
            List<Category> categories = yamnetResults.get(0).getCategories();
            for (Category category : categories) {
                if ((category.getLabel().toLowerCase().contains("baby cry") || category.getLabel().toLowerCase().contains("crying")
                        || category.getLabel().toLowerCase().contains("infant cry")) && category.getScore() >= 0.01f) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "YAMNet Classification Error", e);
        }
        return false;
    }

    private void resetRecordButton() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                recordPiButton.setText("Start Recording");
                recordPiButton.setEnabled(true);
            });
        }
    }

    private float[] convertToFloatArray(byte[] data) {
        short[] shortArray = new short[data.length / 2];
        for (int i = 0; i < shortArray.length; i++) {
            shortArray[i] = (short) ((data[2 * i] & 0xFF) | (data[2 * i + 1] << 8));
        }

        float[] floatArray = new float[shortArray.length];
        for (int i = 0; i < shortArray.length; i++) {
            floatArray[i] = shortArray[i] / (float) Short.MAX_VALUE;
        }

        return floatArray;
    }

    private void showClassificationResults(List<Category> topCategories) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_classification_results, null);

        ProgressBar progressBar1 = dialogView.findViewById(R.id.progress_bar_1);
        ProgressBar progressBar2 = dialogView.findViewById(R.id.progress_bar_2);
        ProgressBar progressBar3 = dialogView.findViewById(R.id.progress_bar_3);

        TextView category1Label = dialogView.findViewById(R.id.category_1_label);
        TextView category2Label = dialogView.findViewById(R.id.category_2_label);
        TextView category3Label = dialogView.findViewById(R.id.category_3_label);

        TextView percentage1 = dialogView.findViewById(R.id.percentage_1);
        TextView percentage2 = dialogView.findViewById(R.id.percentage_2);
        TextView percentage3 = dialogView.findViewById(R.id.percentage_3);

        TextView recommendationsText = dialogView.findViewById(R.id.recommendations_text);

        if (topCategories.size() > 0) {
            Category category1 = topCategories.get(0);
            category1Label.setText(category1.getLabel());
            int progress1 = (int) (category1.getScore() * 100);
            progressBar1.setProgress(progress1);
            percentage1.setText(progress1 + "%");

            if (topCategories.size() > 1) {
                Category category2 = topCategories.get(1);
                category2Label.setText(category2.getLabel());
                int progress2 = (int) (category2.getScore() * 100);
                progressBar2.setProgress(progress2);
                percentage2.setText(progress2 + "%");
            }

            if (topCategories.size() > 2) {
                Category category3 = topCategories.get(2);
                category3Label.setText(category3.getLabel());
                int progress3 = (int) (category3.getScore() * 100);
                progressBar3.setProgress(progress3);
                percentage3.setText(progress3 + "%");
            }

            builder.setView(dialogView);
            builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        }
    }

    private void saveRecordingHistory(List<Category> categories) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("timestamp", System.currentTimeMillis());
        values.put("cry_type", new Gson().toJson(categories));

        db.insert(DatabaseHelper.TABLE_HISTORY, null, values);
    }

    private void showAlertDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
