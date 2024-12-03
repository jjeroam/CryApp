package com.example.babycry.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.babycry.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MonitorFragment extends Fragment implements View.OnClickListener {

    private HandlerThread streamThread;
    private android.os.Handler streamHandler;
    private ImageView monitor;
    private Button connectButton;

    private final int ID_CONNECT = 200;
    private boolean isStreaming = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitor, container, false);

        // Initialize the Connect button
        connectButton = view.findViewById(R.id.connect);
        connectButton.setOnClickListener(this);

        // Set up the monitor (ImageView) for video
        monitor = view.findViewById(R.id.monitor);

        // Set up the streaming thread
        streamThread = new HandlerThread("http-stream");
        streamThread.start();
        streamHandler = new StreamHandler(streamThread.getLooper());

        return view;
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
                // Start streaming
                isStreaming = true;
                streamHandler.sendEmptyMessage(ID_CONNECT);
                connectButton.setText("Stop Monitoring"); // Change button text
                Toast.makeText(getContext(), "Streaming started", Toast.LENGTH_SHORT).show();
            } else {
                // Stop streaming
                isStreaming = false;
                connectButton.setText("Monitor Now"); // Change button text
                Toast.makeText(getContext(), "Streaming stopped", Toast.LENGTH_SHORT).show();
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
        String streamUrl = "http://192.168.8.100:8081/";

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
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Failed to connect to stream", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            e.printStackTrace();
            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Error connecting to stream", Toast.LENGTH_SHORT).show());
        }
    }
}
