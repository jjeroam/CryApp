package com.example.babycry.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.babycry.R;
import com.google.android.material.snackbar.Snackbar;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MonitorFragment extends Fragment implements View.OnClickListener {

    private HandlerThread streamThread;
    private android.os.Handler streamHandler;
    private ImageView monitor;
    private Button connectButton;

    private static final int ID_CONNECT = 200;
    private boolean isStreaming = false;

    private View rootView; // Needed for showing Snackbar

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_monitor, container, false);

        connectButton = rootView.findViewById(R.id.connect);
        connectButton.setOnClickListener(this);

        monitor = rootView.findViewById(R.id.monitor);

        streamThread = new HandlerThread("http-stream");
        streamThread.start();
        streamHandler = new StreamHandler(streamThread.getLooper());

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
                streamHandler.sendEmptyMessage(ID_CONNECT);
                connectButton.setText("Stop Monitoring");
                showOnScreenNotification("Connecting to the PI");
                vibratePhone();
            } else {
                isStreaming = false;
                connectButton.setText("Monitor Now");
                showOnScreenNotification("Monitoring Stopped: The stream has stopped.");
                vibratePhone();
            }
        }
    }

    private class StreamHandler extends android.os.Handler {
        public StreamHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ID_CONNECT) {
                streamVideo();
            }
        }
    }

    private void streamVideo() {
        String streamUrl = "http://192.168.254.145:8081/";

        try {
            URL url = new URL(streamUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                MJPEGInputStream mjpegInputStream = new MJPEGInputStream(inputStream);

                while (isStreaming) {
                    Bitmap frame = mjpegInputStream.readFrame();
                    if (frame != null) {
                        getActivity().runOnUiThread(() -> monitor.setImageBitmap(frame));
                    } else {
                        break;
                    }
                }

                mjpegInputStream.close();
            } else {
                Log.e("MonitorFragment", "Stream connection failed: " + connection.getResponseCode());
                getActivity().runOnUiThread(() -> showOnScreenNotification("Connection Failed: Unable to connect to the stream."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            getActivity().runOnUiThread(() -> showOnScreenNotification("Error: Could not connect to the stream."));
        }
    }

    private void showOnScreenNotification(String message) {
        if (rootView != null && getActivity() != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        }
    }

    private void vibratePhone() {
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(200); // Vibrate for 200ms
        }
    }
}
