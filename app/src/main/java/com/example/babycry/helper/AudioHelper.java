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

public class AudioHelper extends BaseHelper {

    public final static int REQUEST_RECORD_AUDIO = 2033;
    protected Button startRecordingButton;

    @SuppressLint("ObsoleteSdkInt")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_helper);

        startRecordingButton = findViewById(R.id.buttonStartRecording);

        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            }
        }
    }

    public void startRecording(View view) {
        startRecordingButton.setEnabled(false); // Disable the button to prevent multiple clicks
    }

    public void stopRecording(View view) {
        startRecordingButton.setEnabled(true); // Re-enable the button after recording is done
    }
}
