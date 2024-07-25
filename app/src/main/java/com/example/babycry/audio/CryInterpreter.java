package com.example.babycry.audio;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.babycry.R;
import com.example.babycry.Recommendations.BellypainRecommendations;
import com.example.babycry.Recommendations.BurpingRecommendations;
import com.example.babycry.Recommendations.DiscomfortRecommendations;
import com.example.babycry.Recommendations.HungryRecommendations;
import com.example.babycry.Recommendations.TiredRecommendations;
import com.example.babycry.helper.AudioHelper;
import com.example.babycry.helper.DatabaseHelper;
import com.example.babycry.helper.HistoryActivity;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.gson.Gson;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CryInterpreter extends AudioHelper implements OnChartValueSelectedListener {

        private static final String TAG = CryInterpreter.class.getSimpleName();
        private static final String CRY_CLASSIFICATION_MODEL_PATH = "rfmetamodel.tflite";
        private static final float PROBABILITY_THRESHOLD = 0.20f;
        private static final int RECORDING_DURATION_MS = 5000; // 5 seconds

        private AudioClassifier cryClassificationClassifier;
        private TensorAudio tensor;
        private AudioRecord record;
        private Handler handler;
        private BarChart barChart;
        private List<BarEntry> barEntries = new ArrayList<>();
        private List<String> barLabels = new ArrayList<>();
        private XAxis xAxis; // Declare XAxis instance

        private DatabaseHelper dbHelper; // DatabaseHelper instance
        private Button startButton; // Reference to the start recording button

        @Override
        public void startRecording(View view) {
                super.startRecording(view);

                // Initialize BarChart
                barChart = findViewById(R.id.barChart);
                startButton = findViewById(R.id.buttonStartRecording); // Find the start button

                if (barChart == null) {
                        Log.e(TAG, "BarChart is null. Ensure the correct view is passed.");
                        return;
                }

                // Customize the X-axis
                xAxis = barChart.getXAxis();
                xAxis.setDrawLabels(true); // Display labels
                xAxis.setDrawGridLines(false); // Remove grid lines
                xAxis.setDrawAxisLine(false); // Remove axis line
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); // Set position of X-axis labels
                xAxis.setGranularity(1f); // Ensure labels are displayed at every tick

                // Customize the left Y-axis
                YAxis leftAxis = barChart.getAxisLeft();
                leftAxis.setDrawLabels(false); // Remove labels
                leftAxis.setDrawGridLines(false); // Remove grid lines
                leftAxis.setDrawAxisLine(false); // Remove axis line

                // Customize the right Y-axis
                YAxis rightAxis = barChart.getAxisRight();
                rightAxis.setDrawLabels(false); // Remove labels
                rightAxis.setDrawGridLines(false); // Remove grid lines
                rightAxis.setDrawAxisLine(false); // Remove axis line

                // Remove description
                barChart.getDescription().setEnabled(false);

                // Remove legend
                Legend legend = barChart.getLegend();
                legend.setEnabled(false);

                // Load the cry classification model from the assets folder
                try {
                        cryClassificationClassifier = AudioClassifier.createFromFile(view.getContext(), CRY_CLASSIFICATION_MODEL_PATH);
                } catch (IOException e) {
                        Log.e(TAG, "Error loading cry classification model: " + e.getMessage());
                        return;
                }

                // Create an audio recorder
                tensor = cryClassificationClassifier.createInputTensorAudio();
                record = cryClassificationClassifier.createAudioRecord();
                record.startRecording();

                handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(this::stopRecordingAndClassifyCry, RECORDING_DURATION_MS);

                // Set the chart value selected listener
                barChart.setOnChartValueSelectedListener(this);

                dbHelper = new DatabaseHelper(view.getContext()); // Initialize DatabaseHelper

                // Disable the start button to prevent multiple recordings at the same time
                startButton.setEnabled(false);
        }

        private void stopRecordingAndClassifyCry() {
                if (record != null) {
                        record.stop();
                }

                // Load audio data into tensor
                tensor.load(record);

                // Classify the audio data
                List<Classifications> output = cryClassificationClassifier.classify(tensor);

                List<Category> finalOutput = new ArrayList<>();
                for (Classifications classifications : output) {
                        finalOutput.addAll(classifications.getCategories());
                }

                finalOutput.sort((o1, o2) -> Float.compare(o2.getScore(), o1.getScore()));

                // Debug log
                for (Category category : finalOutput) {
                        Log.d(TAG, "Category: " + category.getLabel() + " Score: " + category.getScore());
                }

                runOnUiThread(() -> {
                        barEntries.clear();
                        barLabels.clear();

                        List<Category> topCategories = new ArrayList<>();
                        for (int i = 0; i < finalOutput.size(); i++) {
                                Category category = finalOutput.get(i);
                                barEntries.add(new BarEntry(i, category.getScore() * 100)); // Convert to percentage
                                barLabels.add(category.getLabel());
                                if (i < 2) { // Store only the top 2 categories
                                        topCategories.add(category);
                                }
                        }

                        BarDataSet barDataSet = new BarDataSet(barEntries, "".toUpperCase());
                        barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);

                        // Set value formatter to show percentage values on the bars
                        barDataSet.setValueFormatter(new PercentFormatter());

                        // Set text color and text size for better visibility
                        barDataSet.setValueTextColor(Color.BLACK);
                        barDataSet.setValueTextSize(14f); // Set larger text size

                        BarData barData = new BarData(barDataSet);

                        // Apply custom value formatter for X-axis labels
                        xAxis.setValueFormatter(new ValueFormatter() {
                                @Override
                                public String getFormattedValue(float value) {
                                        int index = (int) value;
                                        return index >= 0 && index < barLabels.size() ? barLabels.get(index) : "".toUpperCase();
                                }
                        });

                        barChart.setData(barData);
                        barChart.invalidate(); // Refresh the chart

                        // Save history to database
                        saveRecordingHistory(topCategories);

                        // Show history saved message
                        Toast.makeText(this, "Result saved", Toast.LENGTH_SHORT).show();

                        // Re-enable the start recording button after processing
                        startButton.setEnabled(true);
                        stopRecording(null);
                });
        }

        private void saveRecordingHistory(List<Category> topCategories) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                Gson gson = new Gson();
                String resultsJson = gson.toJson(topCategories);

                ContentValues values = new ContentValues();
                values.put(DatabaseHelper.COLUMN_TIMESTAMP, System.currentTimeMillis());
                values.put(DatabaseHelper.COLUMN_RESULTS, resultsJson);

                db.insert(DatabaseHelper.TABLE_HISTORY, null, values);
        }

        @Override
        public void stopRecording(View view) {
                super.stopRecording(view);
                if (handler != null) {
                        handler.removeCallbacksAndMessages(null);
                }
                if (record != null) {
                        record.stop();
                }
        }

        @Override
        public void onValueSelected(Entry e, Highlight h) {
                int xIndex = (int) e.getX();
                String selectedCategory = barLabels.get(xIndex);

                Log.d(TAG, "Selected Category: " + selectedCategory); // Log the selected category

                Intent intent = null;
                switch (selectedCategory.toLowerCase().trim()) {
                        case "tired":
                                intent = new Intent(this, TiredRecommendations.class);
                                break;
                        case "hungry":
                                intent = new Intent(this, HungryRecommendations.class);
                                break;
                        case "belly_pain":
                                intent = new Intent(this, BellypainRecommendations.class);
                                break;
                        case "burping":
                                intent = new Intent(this, BurpingRecommendations.class);
                                break;
                        case "discomfort":
                                intent = new Intent(this, DiscomfortRecommendations.class);
                                break;
                        default:
                                Log.e(TAG, "Unknown category: " + selectedCategory);
                                return;
                }

                if (intent != null) {
                        startActivity(intent);
                }
        }

        @Override
        public void onNothingSelected() {
                // Do nothing
        }

        public void showHistory(View view) {
                Intent intent = new Intent(this, HistoryActivity.class);
                startActivity(intent);
        }
}
