package com.example.babycry.ui;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.babycry.R;
import com.example.babycry.helper.DatabaseHelper;
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

public class InterpreterFragment extends Fragment implements OnChartValueSelectedListener {

    private static final String TAG = InterpreterFragment.class.getSimpleName();
    private static final String CRY_CLASSIFICATION_MODEL_PATH = "crymodel44.tflite";
    private static final int RECORDING_DURATION_MS = 5000; // 5 seconds

    private AudioClassifier cryClassificationClassifier;
    private TensorAudio tensor;
    private AudioRecord record;
    private Handler handler;
    private BarChart barChart;
    private List<BarEntry> barEntries = new ArrayList<>();
    private List<String> barLabels = new ArrayList<>();
    private XAxis xAxis;

    private DatabaseHelper dbHelper;
    private Button startButton;
    private ImageView infoIcon;
    private CircularCountdownView countdownView;
    private CountDownTimer countDownTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_interpreter, container, false);

        barChart = view.findViewById(R.id.barChart);
        startButton = view.findViewById(R.id.buttonStartRecording);
        infoIcon = view.findViewById(R.id.infoIcon);
        countdownView = view.findViewById(R.id.circularCountdownView);

        // Customize the X-axis
        xAxis = barChart.getXAxis();
        xAxis.setDrawLabels(true);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);

        // Customize the left Y-axis
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawLabels(false);
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawAxisLine(false);

        // Customize the right Y-axis
        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setDrawLabels(false);
        rightAxis.setDrawGridLines(false);
        rightAxis.setDrawAxisLine(false);

        // Remove description and legend
        barChart.getDescription().setEnabled(false);
        Legend legend = barChart.getLegend();
        legend.setEnabled(false);

        dbHelper = new DatabaseHelper(getContext());

        startButton.setOnClickListener(v -> startRecording());

        infoIcon.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Tap the bar to show recommendations that might help the baby", Toast.LENGTH_LONG).show();
        });

        return view;
    }

    private void startRecording() {
        startButton.setEnabled(false);
        countdownView.setVisibility(View.VISIBLE);
        countdownView.setProgress(100); // Set initial progress to 100%

        countDownTimer = new CountDownTimer(RECORDING_DURATION_MS, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int progress = (int) (millisUntilFinished / (float) RECORDING_DURATION_MS * 100);
                countdownView.setProgress(progress);
            }

            @Override
            public void onFinish() {
                countdownView.setVisibility(View.GONE);
                stopRecordingAndClassifyCry();
            }
        }.start();

        // Load the cry classification model
        try {
            cryClassificationClassifier = AudioClassifier.createFromFile(getContext(), CRY_CLASSIFICATION_MODEL_PATH);
        } catch (IOException e) {
            Log.e(TAG, "Error loading cry classification model: " + e.getMessage());
            return;
        }

        tensor = cryClassificationClassifier.createInputTensorAudio();
        record = cryClassificationClassifier.createAudioRecord();
        record.startRecording();

        handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this::stopRecordingAndClassifyCry, RECORDING_DURATION_MS);

        barChart.setOnChartValueSelectedListener(this);
    }

    private void stopRecordingAndClassifyCry() {
        if (record != null) {
            record.stop();
        }

        tensor.load(record);

        List<Classifications> output = cryClassificationClassifier.classify(tensor);
        List<Category> finalOutput = new ArrayList<>();
        for (Classifications classifications : output) {
            finalOutput.addAll(classifications.getCategories());
        }

        for (Category category : finalOutput) {
            Log.d(TAG, "Category: " + category.getLabel() + " Score: " + category.getScore());
        }

        getActivity().runOnUiThread(() -> {
            barEntries.clear();
            barLabels.clear();

            for (int i = 0; i < finalOutput.size(); i++) {
                Category category = finalOutput.get(i);
                barEntries.add(new BarEntry(i, category.getScore() * 100)); // Convert to percentage
                barLabels.add(category.getLabel());
            }

            BarDataSet barDataSet = new BarDataSet(barEntries, "".toUpperCase());
            barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);
            barDataSet.setValueFormatter(new PercentFormatter());
            barDataSet.setValueTextColor(Color.BLACK);
            barDataSet.setValueTextSize(14f);

            BarData barData = new BarData(barDataSet);

            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int index = (int) value;
                    return index >= 0 && index < barLabels.size() ? barLabels.get(index) : "".toUpperCase();
                }
            });

            barChart.setData(barData);
            barChart.invalidate();

            saveRecordingHistory(finalOutput);

            Toast.makeText(getContext(), "Result saved", Toast.LENGTH_SHORT).show();
            startButton.setEnabled(true);
            countdownView.setVisibility(View.GONE);
        });
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

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        int xIndex = (int) e.getX();
        String selectedCategory = barLabels.get(xIndex);

        Log.d(TAG, "Selected Category: " + selectedCategory);

        String recommendations = getRecommendationsForCategory(selectedCategory);

        // Create and show the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_recommendations, null);

        TextView recommendationsText = dialogView.findViewById(R.id.recommendationsText);
        recommendationsText.setText(recommendations);

        Button closeDialogButton = dialogView.findViewById(R.id.closeDialogButton);

        AlertDialog dialog = builder.setView(dialogView).create();

        closeDialogButton.setOnClickListener(v -> {
            // Dismiss the dialog
            dialog.dismiss();
        });

        dialog.show();
    }

    private String getRecommendationsForCategory(String category) {
        switch (category.toLowerCase().trim()) {
            case "tired":
                return "1. Swaddling a baby helps them to get into sleep. Swaddling them may stop them from startling themselves awake when their legs and arms jerk involuntarily. Also, swaddling them may help in letting them feel safe and cozy.\n\n" +
                        "2. Letting them use a pacifier can help in lulling them to sleep.\n\n" +
                        "3. Holding a baby close to you and having them hear your heartbeat may also help in letting them sleep.\n\n" +
                        "4. Softly rocking your baby is also one of the ways to lull them to sleep.\n\n" +
                        "5. Some babies can also easily sleep with soft hushes and lullabies.\n\n" +
                        "6. Making a gentle shushing sound directly into the baby's ear, which is similar to the noises they heard in the womb can help them to sleep.\n\n" +
                        "7. Try swinging or gently jiggling the baby to get them to calm down (while always taking care to support the baby's head and neck).";
            case "hungry":
                return "1. Feed your baby the moment you notice the signs of hunger. Responding early will help you avoid having to deal with long bouts of crying.\n\n" +
                        "2. If a baby starts eating solid food, be very careful of choking hazards so keep the texture smooth and runny to help your infant enjoy the process of learning to eat.\n\n" +
                        "3. When feeding your baby, make sure it’s an enjoyable experience. Seat baby in a safe high chair and minimize distractions. Allow sufficient time to complete the meal and do not force your baby if he is not hungry or interested in eating.";
            case "belly_pain":
                return "1. Is your crying baby also wriggling, arching their back or pumping their legs, this is a sign of baby gas, try to bicycle their legs and push them up to the chest to help relieve the gas.\n\n" +
                        "2. Some babies also experience an upset stomach when transitioning from breast milk to formula.\n\n" +
                        "3. If your little eater seems to get extra fussy after mealtimes, it could be related to their diet.\n\n" +
                        "4. Giving smaller, more frequent feeds and holding the baby upright for 15-20 minutes after each feeding can help reduce reflux or spitting up.";
            case "burping":
                return "1. Hold them upright, to make your baby burp try to gently bounce and rock them while rubbing their back.\n\n" +
                        "2. Start with the baby in an upright position and drape them over your shoulder. Baby’s head should be at your shoulder level, with their child resting on your shoulder, gently pat, rub and massage their back to encourage the trapped air out.\n\n" +
                        "3. Try laying them on their stomach on your lap, be mindful of supporting the baby, and steady them with one hand, while gently patting and rubbing their back with the other.\n\n" +
                        "4. Sitting the baby upright on your lap facing away from you. Lean their weight slightly forward against one of your hands, making sure to support their chest and head, and gently pat their back with your other hand.";
            case "discomfort":
                return "1. Babies may cry if they feel too hot or too cold. Check that your baby’s clothes are not too tight and uncomfortable, too warm or too cold, particularly if the temperature has changed since you dressed them.\n\n" +
                        "2. Do a quick diaper check or perform a “sniff test.” Check to see that they’re dry or that there is no skin irritation caused by a wet nappy, that might be irritating your baby.\n\n" +
                        "3. Inspect for itchy tags or other small things that could be wrong.\n\n" +
                        "4. They might not be in a very comfortable position while breastfeeding. Try changing your baby’s position or attachment to help settle them.";
            default:
                return "No recommendations available.";
        }
    }

    @Override
    public void onNothingSelected() {
        // Do nothing
    }
}
