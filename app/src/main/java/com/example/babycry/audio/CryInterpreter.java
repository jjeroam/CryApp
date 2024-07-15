package com.example.babycry.audio;

import android.graphics.Color;
import android.media.AudioRecord;
import android.util.Log;
import android.view.View;

import com.example.babycry.R;
import com.example.babycry.helper.AudioHelper;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

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
        private static final String MODEL_PATH = "rfgbmmetamodel.tflite";
        private static final float PROBABILITY_THRESHOLD = 0.20f;

        private AudioClassifier classifier;
        private TensorAudio tensor;
        private AudioRecord record;
        private TimerTask timerTask;
        private BarChart barChart;
        private List<BarEntry> barEntries = new ArrayList<>();
        private List<String> barLabels = new ArrayList<>();

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
                XAxis xAxis = barChart.getXAxis();
                xAxis.setDrawLabels(false); // Remove labels
                xAxis.setDrawGridLines(false); // Remove grid lines
                xAxis.setDrawAxisLine(false); // Remove axis line

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

                timerTask = new TimerTask() {
                        @Override
                        public void run() {
                                Log.d(TAG, "Timer task triggered");

                                int numberOfSamples = tensor.load(record);
                                List<Classifications> output = classifier.classify(tensor);

                                List<Category> finalOutput = new ArrayList<>();
                                for (Classifications classifications : output) {
                                        for (Category category : classifications.getCategories()) {
                                                if (category.getScore() > PROBABILITY_THRESHOLD) {
                                                        finalOutput.add(category);
                                                }
                                        }
                                }

                                finalOutput.sort((o1, o2) -> Float.compare(o2.getScore(), o1.getScore()));

                                runOnUiThread(() -> {
                                        barEntries.clear();
                                        barLabels.clear();

                                        for (int i = 0; i < finalOutput.size(); i++) {
                                                Category category = finalOutput.get(i);
                                                barEntries.add(new BarEntry(i, category.getScore() * 100)); // Convert to percentage
                                                barLabels.add(category.getLabel());
                                        }

                                        BarDataSet barDataSet = new BarDataSet(barEntries, "");
                                        barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);

                                        // Set value formatter to show labels inside the bars
                                        barDataSet.setValueFormatter(new ValueFormatter() {
                                                @Override
                                                public String getBarLabel(BarEntry barEntry) {
                                                        int index = (int) barEntry.getX();
                                                        return barLabels.get(index); // Display label inside the bar
                                                }
                                        });

                                        // Set text color and text size for better visibility
                                        barDataSet.setValueTextColor(Color.BLACK);
                                        barDataSet.setValueTextSize(14f); // Set larger text size

                                        BarData barData = new BarData(barDataSet);
                                        barData.setValueFormatter(new PercentFormatter()); // Format as percentage

                                        // Set dynamic description based on categories
                                        StringBuilder descriptionText = new StringBuilder();
                                        for (String label : barLabels) {
                                                descriptionText.append(label).append(", ");
                                        }
                                        if (descriptionText.length() > 2) {
                                                descriptionText.setLength(descriptionText.length() - 2); // Remove last comma and space
                                        }
                                        Description description = new Description();
                                        description.setText(descriptionText.toString());
                                        description.setTextSize(12f);
                                        barChart.setDescription(description);

                                        barChart.setData(barData);
                                        barChart.invalidate(); // Refresh the chart
                                });
                        }
                };

                new Timer().scheduleAtFixedRate(timerTask, 1, 1000);
        }

        @Override
        public void stopRecording(View view) {
                super.stopRecording(view);
                if (timerTask != null) {
                        timerTask.cancel();
                }
                if (record != null) {
                        record.stop();
                }
        }
}
