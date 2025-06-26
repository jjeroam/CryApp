package com.example.babycry.ui;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InterpreterFragment extends Fragment {

    private static final String TAG = InterpreterFragment.class.getSimpleName();
    private static final String CRY_CLASSIFICATION_MODEL_PATH = "balAugCNN.tflite";
    private static final String YAMNET_MODEL_PATH = "yamnet.tflite";
    private static final int RECORDING_DURATION_MS = 5000;
    private static final float SCORE_THRESHOLD = 0.00f;

    private AudioClassifier cryClassificationClassifier;
    private AudioClassifier yamnetClassifier;
    private TensorAudio tensor;
    private AudioRecord record;

    private DatabaseHelper dbHelper;
    private Button startButton;
    private CircularCountdownView countdownView;
    private CountDownTimer countDownTimer;
    private AlertDialog processingDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_interpreter, container, false);
        startButton = view.findViewById(R.id.buttonStartRecording);
        countdownView = view.findViewById(R.id.circularCountdownView);

        dbHelper = new DatabaseHelper(getContext());

        startButton.setOnClickListener(v -> startRecording());

        return view;
    }

    private void startRecording() {
        startButton.setEnabled(false);
        countdownView.setVisibility(View.VISIBLE);
        countdownView.setProgress(100);

        countDownTimer = new CountDownTimer(RECORDING_DURATION_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int progress = (int) (millisUntilFinished / (float) RECORDING_DURATION_MS * 100);
                countdownView.setProgress(progress);
            }

            @Override
            public void onFinish() {
                countdownView.setVisibility(View.VISIBLE);
                stopRecordingAndClassifyCry();
            }
        }.start();

        try {
            yamnetClassifier = AudioClassifier.createFromFile(getContext(), YAMNET_MODEL_PATH);
            cryClassificationClassifier = AudioClassifier.createFromFile(getContext(), CRY_CLASSIFICATION_MODEL_PATH);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model: " + e.getMessage());
            return;
        }

        tensor = cryClassificationClassifier.createInputTensorAudio();
        record = cryClassificationClassifier.createAudioRecord();
        record.startRecording();
    }

    private void stopRecordingAndClassifyCry() {
        getActivity().runOnUiThread(() -> {
            processingDialog = new AlertDialog.Builder(getContext())
                    .setTitle("Processing...")
                    .setMessage("Classifying the cry, please wait.")
                    .setCancelable(false)
                    .show();

        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            boolean containsCry = false;
            try {
                AudioClassifier yamnet = AudioClassifier.createFromFile(getContext(), YAMNET_MODEL_PATH);
                TensorAudio yamnetTensor = yamnet.createInputTensorAudio();
                AudioRecord yamnetRecord = yamnet.createAudioRecord();
                yamnetRecord.startRecording();

                Thread.sleep(RECORDING_DURATION_MS);

                yamnetTensor.load(yamnetRecord);
                yamnetRecord.stop();

                List<Classifications> yamnetResults = yamnet.classify(yamnetTensor);
                for (Classifications classifications : yamnetResults) {
                    for (Category category : classifications.getCategories()) {
                        if ((category.getLabel().toLowerCase().contains("baby cry") || category.getLabel().toLowerCase().contains("crying")
                                || category.getLabel().toLowerCase().contains("infant cry")) && category.getScore() >= 0.01f) {
                            containsCry = true;
                            break;
                        }
                    }
                    if (containsCry) break;
                }

            } catch (Exception e) {
                Log.e(TAG, "YAMNet classification failed: " + e.getMessage());
            }

            if (!containsCry) {
                getActivity().runOnUiThread(() -> {
                    // Dismiss the processing dialog first
                    if (processingDialog != null && processingDialog.isShowing()) {
                        processingDialog.dismiss();
                    }

                    // Then show the alert
                    new AlertDialog.Builder(getContext())
                            .setTitle("No Cry Detected")
                            .setMessage("No cry detected. Try recording again.")
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .setCancelable(false)
                            .show();

                    // Re-enable the button and hide countdown
                    startButton.setEnabled(true);
                    countdownView.setVisibility(View.VISIBLE);
                });
                return;
            }

            try {
                AudioClassifier cryClassifier = AudioClassifier.createFromFile(getContext(), CRY_CLASSIFICATION_MODEL_PATH);
                TensorAudio tensor = cryClassifier.createInputTensorAudio();
                AudioRecord record = cryClassifier.createAudioRecord();
                record.startRecording();

                Thread.sleep(RECORDING_DURATION_MS);

                tensor.load(record);
                record.stop();

                List<Classifications> output = cryClassifier.classify(tensor);
                List<Category> finalOutput = new ArrayList<>();
                for (Classifications classifications : output) {
                    for (Category category : classifications.getCategories()) {
                        if (category.getScore() >= SCORE_THRESHOLD) {
                            finalOutput.add(category);
                        }
                    }
                }

                getActivity().runOnUiThread(() -> {
                    if (processingDialog != null && processingDialog.isShowing()) {
                        processingDialog.dismiss();
                    }

                    finalOutput.sort((c1, c2) -> Float.compare(c2.getScore(), c1.getScore()));
                    List<Category> topCategories = finalOutput.size() > 3 ? finalOutput.subList(0, 3) : finalOutput;

                    showClassificationResults(topCategories);
                    saveRecordingHistory(topCategories);

                    Toast.makeText(getContext(), "Result saved", Toast.LENGTH_SHORT).show();
                    startButton.setEnabled(true);
                    countdownView.setVisibility(View.VISIBLE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Cry classification failed: " + e.getMessage());
                getActivity().runOnUiThread(() -> {
                    if (processingDialog != null && processingDialog.isShowing()) {
                        processingDialog.dismiss();
                    }
                    Toast.makeText(getContext(), "Classification failed", Toast.LENGTH_SHORT).show();
                    startButton.setEnabled(true);
                    countdownView.setVisibility(View.VISIBLE);
                });
            }

            executor.shutdown();
        });
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
            recommendationsText.setText(getRecommendationsForCategory(category1.getLabel()));
        }

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

        builder.setView(dialogView)
                .setCancelable(true)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
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

    private String getRecommendationsForCategory(String category) {
        switch (category.toLowerCase().trim()) {
            case "tired":
                return "1. Swaddle your baby to help them sleep...";
            case "hungry":
                return "1. Feed your baby immediately when you notice signs of hunger...";
            case "belly_pain":
                return "1. Bicycle the baby’s legs to relieve gas...";
            case "burping":
                return "1. Hold them upright to make your baby burp...";
            case "discomfort":
                return "1. Make sure your baby is comfortable...";
            default:
                return "No recommendations available.";
        }
    }
}
