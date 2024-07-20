package com.example.babycry.audio;

import android.content.Intent;
import android.graphics.Color;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.babycry.R;
import com.example.babycry.Recommendations.BellypainRecommendations;
import com.example.babycry.Recommendations.BurpingRecommendations;
import com.example.babycry.Recommendations.DiscomfortRecommendations;
import com.example.babycry.Recommendations.HungryRecommendations;
import com.example.babycry.Recommendations.TiredRecommendations;
import com.example.babycry.helper.AudioHelper;
import com.example.babycry.helper.HistoryActivity;
import com.example.babycry.helper.RecordingHistory;
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

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CryInterpreter extends AudioHelper implements OnChartValueSelectedListener {

        private static final String TAG = CryInterpreter.class.getSimpleName();
        private static final String MODEL_PATH = "rfgbmmetamodel.tflite";
        private static final float PROBABILITY_THRESHOLD = 0.20f;
        private static final int RECORDING_DURATION_MS = 5000; // 10 seconds

        private AudioClassifier classifier;
        private TensorAudio tensor;
        private AudioRecord record;
        private Handler handler;
        private BarChart barChart;
        private List<BarEntry> barEntries = new ArrayList<>();
        private List<String> barLabels = new ArrayList<>();
        private XAxis xAxis; // Declare XAxis instance

        private List<RecordingHistory> history = new ArrayList<>();

        @Override
        public void startRecording(View view) {
                super.startRecording(view);

                // Initialize BarChart
                barChart = findViewById(R.id.barChart);

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

                // Load the model from the assets folder
                try {
                        classifier = AudioClassifier.createFromFile(view.getContext(), MODEL_PATH);
                } catch (IOException e) {
                        Log.e(TAG, "Error loading model: " + e.getMessage());
                        return;
                }

                // Create an audio recorder
                tensor = classifier.createInputTensorAudio();
                record = classifier.createAudioRecord();
                record.startRecording();

                handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(this::stopRecordingAndProcess, RECORDING_DURATION_MS);

                // Set the chart value selected listener
                barChart.setOnChartValueSelectedListener(this);
        }

        private void stopRecordingAndProcess() {
                if (record != null) {
                        record.stop();
                }

                int numberOfSamples = tensor.load(record);
                List<Classifications> output = classifier.classify(tensor);

                List<Category> finalOutput = new ArrayList<>();
                for (Classifications classifications : output) {
                        finalOutput.addAll(classifications.getCategories());
                }

                finalOutput.sort((o1, o2) -> Float.compare(o2.getScore(), o1.getScore()));

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

                        for (int i = 0; i < finalOutput.size(); i++) {
                                Category category = finalOutput.get(i);
                                barEntries.add(new BarEntry(i, category.getScore() * 100)); // Convert to percentage
                                barLabels.add(category.getLabel());
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

                        // Save history
                        history.add(new RecordingHistory(System.currentTimeMillis(), topCategories));

                        // Show history saved message
                        Toast.makeText(this, "Recording history saved", Toast.LENGTH_SHORT).show();


                        // Re-enable the start recording button after processing
                        View view = null;
                        stopRecording(view);
                });
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
