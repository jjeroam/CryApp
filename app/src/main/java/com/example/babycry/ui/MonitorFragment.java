package com.example.babycry.ui;

import static android.content.Context.VIBRATOR_SERVICE;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.babycry.R;
import com.example.babycry.helper.DatabaseHelper;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import org.json.JSONObject;
import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MonitorFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "MonitorFragment";
    private static final String MODEL_PATH = "crymodel44.tflite";

    private HandlerThread streamThread;
    private android.os.Handler streamHandler;
    private ImageView monitor;
    private Button connectButton, recordPiButton;
    private boolean isStreaming = false;
    private MediaPlayer mediaPlayer;
    private AudioClassifier classifier;
    private TensorAudio tensor;
    private View rootView;
    private DatabaseHelper dbHelper;
    private boolean soundPreviouslyDetected = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_monitor, container, false);

        connectButton = rootView.findViewById(R.id.connect);
        recordPiButton = rootView.findViewById(R.id.record_pi);
        monitor = rootView.findViewById(R.id.monitor);

        connectButton.setOnClickListener(this);
        recordPiButton.setOnClickListener(this);

        dbHelper = new DatabaseHelper(getContext());

        streamThread = new HandlerThread("http-stream");
        streamThread.start();
        streamHandler = new StreamHandler(streamThread.getLooper());

        try {
            classifier = AudioClassifier.createFromFile(requireContext(), MODEL_PATH);
            tensor = classifier.createInputTensorAudio();
        } catch (Exception e) {
            showOnScreenNotification("Error loading model: " + e.getMessage());
        }

        pollSoundDetection();  // Start polling for sound detection when the fragment is created

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
                showOnScreenNotification("Connecting to the PI");
            } else {
                isStreaming = false;
                stopAudioStream();
                connectButton.setText("Monitor Now");
                showOnScreenNotification("Monitoring Stopped.");
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

    private void streamVideo() {
        try {
            URL url = new URL("http://192.168.254.151:8080/stream.mjpg");
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
                showOnScreenNotification("Unable to connect to the stream.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Stream error: ", e);
            showOnScreenNotification("Stream connection error.");
        }
    }

    private void startAudioStream() {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource("http://192.168.254.151:8000/mystream");
            mediaPlayer.setLooping(true);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (Exception e) {
            showOnScreenNotification("Audio Error: " + e.getMessage());
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
                // Step 1: Request the Pi to record and send back .wav
                URL url = new URL("http://192.168.254.151:8080/record-audio");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.connect();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = conn.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] temp = new byte[1024];
                    int read;
                    while ((read = inputStream.read(temp)) != -1) {
                        buffer.write(temp, 0, read);
                    }
                    inputStream.close();

                    byte[] audioData = buffer.toByteArray();
                    float[] floatData = convertToFloatArray(audioData);
                    tensor.load(floatData);

                    List<Classifications> results = classifier.classify(tensor);
                    List<Category> categories = results.get(0).getCategories();
                    getActivity().runOnUiThread(() -> showClassificationDialog(results));
                    saveRecordingHistory(categories);
                } else {
                    showOnScreenNotification("Failed to get recording.");
                }

                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Recording Error: ", e);
                showOnScreenNotification("Error recording from Pi.");
            } finally {
                resetRecordButton();
            }
        }).start();
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

    private void showClassificationDialog(List<Classifications> results) {
        if (getActivity() == null) return;

        List<Category> categories = results.get(0).getCategories();

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_classification_chart, null);

        TextView recommendationText = dialogView.findViewById(R.id.recommendationText);

        recommendationText.setText(getRecommendations(categories.get(0).getLabel()));

        builder.setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void saveRecordingHistory(List<Category> categories) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Gson gson = new Gson();
        String resultsJson = gson.toJson(categories);

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_TIMESTAMP, System.currentTimeMillis());
        values.put(DatabaseHelper.COLUMN_RESULTS, resultsJson);

        db.insert(DatabaseHelper.TABLE_HISTORY, null, values);
    }

    private String getRecommendations(String category) {
        switch (category.toLowerCase()) {
            case "tired":
                return "Try swaddling, rocking gently, using a pacifier, or playing soft sounds.";
            case "hungry":
                return "Check for feeding, offer milk or formula.";
            default:
                return "Check baby's comfort, try soothing techniques.";
        }
    }

    private void pollSoundDetection() {
        Handler handler = new Handler();
        Runnable pollRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        URL url = new URL("http://192.168.254.151:8080/poll-sound");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");

                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String response = reader.readLine();
                        JSONObject json = new JSONObject(response);

                        boolean detected = json.getBoolean("sound_detected");

                        if (detected && !soundPreviouslyDetected) {
                            soundPreviouslyDetected = true;
                            getActivity().runOnUiThread(() -> {
                                triggerVibration();
                                playNotificationSound();
                                showPersistentNotification();
                            });
                        } else if (!detected) {
                            soundPreviouslyDetected = false;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(pollRunnable);
    }

    private void triggerVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Vibrator vibrator = (Vibrator) getActivity().getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(3000, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } else {
            Vibrator vibrator = (Vibrator) getActivity().getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(3000); // Legacy support for older versions
            }
        }
    }

    private void playNotificationSound() {
        Ringtone ringtone = RingtoneManager.getRingtone(getContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        ringtone.play();
    }

    private void showPersistentNotification() {
        // Create a custom Toast
        Toast toast = Toast.makeText(getContext(), "Check out your baby, he/she might need your help", Toast.LENGTH_LONG);

        // Set custom view for the Toast (center and larger text)
        View customView = getLayoutInflater().inflate(R.layout.custom_toast_layout, null);
        TextView textView = customView.findViewById(R.id.toast_text);
        textView.setText("Check out your baby, he/she might need your help");

        toast.setView(customView);
        toast.setGravity(Gravity.CENTER, 0, 0); // Position in the center of the screen
        toast.show();
    }


    private void showOnScreenNotification(String message) {
        if (getActivity() != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        }
    }
}
