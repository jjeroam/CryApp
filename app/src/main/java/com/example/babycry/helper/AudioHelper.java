package com.example.babycry.helper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;

import com.example.babycry.R;

public class AudioHelper extends BaseHelper{

    public final static int REQUEST_RECORD_AUDIO = 2033;

//    protected BarChart outputChart;
//
//    private List<String> xValues = Arrays.asList("Discomfort", "Burping", "Hungry", "Belly Pain", "Tired");
    protected Button startRecordingButton;
    protected Button stopRecordingButton;

    @SuppressLint("ObsoleteSdkInt")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_helper);

//        outputChart = findViewById(R.id.barChart);
//        outputChart.getAxisRight().setDrawLabels(false);

        startRecordingButton = findViewById(R.id.buttonStartRecording);
        stopRecordingButton = findViewById(R.id.buttonStopRecording);

        stopRecordingButton.setEnabled(false);

//        ArrayList<BarEntry> entries = new ArrayList<>();
//        entries.add(new BarEntry(0, 24.3f));
//        entries.add(new BarEntry(1, 40.5f));
//        entries.add(new BarEntry(2, 33.2f));
//        entries.add(new BarEntry(3, 52.1f));
//        entries.add(new BarEntry(4, 32.5f));

//        YAxis yAxis = outputChart.getAxisLeft();
//        yAxis.setAxisMinimum(0f);
//        yAxis.setAxisMaximum(100f);
//        yAxis.setAxisLineColor(Color.TRANSPARENT);
//        yAxis.setGridColor(Color.TRANSPARENT);
//
//        BarDataSet dataSet = new BarDataSet(entries, "Classification");
//        dataSet.setColors(ColorTemplate.PASTEL_COLORS);

//        BarData barData = new BarData(dataSet);
//        outputChart.setData(barData);
//
//        outputChart.getDescription().setEnabled(false);
//        outputChart.invalidate();
//
//        outputChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xValues));
//        outputChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
//        outputChart.getXAxis().setGranularity(1f);
//        outputChart.getXAxis().setGranularityEnabled(true);
//        outputChart.getXAxis().setGridColor(Color.TRANSPARENT);
//        outputChart.getXAxis().setAxisLineColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            }
        }
    }

    public void startRecording(View view) {
        startRecordingButton.setEnabled(false);
        stopRecordingButton.setEnabled(true);
    }

    public void stopRecording(View view) {
        startRecordingButton.setEnabled(true);
        stopRecordingButton.setEnabled(false);
    }
}