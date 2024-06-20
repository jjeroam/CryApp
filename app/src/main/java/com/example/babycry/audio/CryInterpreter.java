package com.example.babycry.audio;

import android.media.AudioRecord;
import android.util.Log;
import android.view.View;

import com.example.babycry.helper.AudioHelper;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CryInterpreter extends AudioHelper {

        private static final String TAG = CryInterpreter.class.getSimpleName();

        private static final String MODEL_PATH = "crymodel44.tflite";
        private static final float PROBABILITY_THRESHOLD = 0.59f;

        private AudioClassifier classifier;
        private TensorAudio tensor;
        private AudioRecord record;
        private TimerTask timerTask;

        public void startRecording(View view) {
                super.startRecording(view);

                // Load the model from the assets folder
                try {
                        classifier = AudioClassifier.createFromFile(this, MODEL_PATH);
                } catch (IOException e) {
                        Log.e(TAG, "Error loading model: " + e.getMessage());
                }

                // Create an audio recorder
                tensor = classifier.createInputTensorAudio();

                // Create and start recording
                record = classifier.createAudioRecord();
                record.startRecording();

                timerTask = new TimerTask() {
                        @Override
                        public void run() {
                                Log.d(TAG, "Timer task triggered");

                                // Classify audio data
                                int numberOfSamples = tensor.load(record);
                                List<Classifications> output = classifier.classify(tensor);

                                // Filter out classifications with low probability
                                List<Category> finalOutput = new ArrayList<>();
                                for (Classifications classifications : output) {
                                        for (Category category : classifications.getCategories()) {
                                                if (category.getScore() > PROBABILITY_THRESHOLD) {
                                                        finalOutput.add(category);
                                                }
                                        }
                                }

                                // Sort the results
                                finalOutput.sort((o1, o2) -> (int) (o1.getScore() - o2.getScore()));

                                // Create a multiline string with the filtered results
                                StringBuilder outputStr = new StringBuilder();
                                for (Category category : finalOutput) {
                                        outputStr.append(category.getLabel())
                                                .append(": ").append(category.getScore()).append("\n");
                                }

                                // Update the UI
                                runOnUiThread(() -> {
                                        if (finalOutput.isEmpty()) {
                                                outputTextView.setText("Could not identify");
                                        } else {
                                                outputTextView.setText(outputStr.toString());
                                        }
                                });
                        }
                };

                new Timer().scheduleAtFixedRate(timerTask, 1, 1000);
        }

        public void stopRecording(View view) {
                super.stopRecording(view);

                timerTask.cancel();
                record.stop();
        }
}