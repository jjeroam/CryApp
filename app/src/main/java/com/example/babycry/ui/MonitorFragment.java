package com.example.babycry.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.babycry.R;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MonitorFragment extends Fragment implements View.OnClickListener {

    private HandlerThread stream_thread, flash_thread, rssi_thread;
    private Handler stream_handler, flash_handler, rssi_handler;
    private Button flash_button;
    private ImageView monitor;
    private TextView rssi_text;
    private EditText ip_text;

    private final int ID_CONNECT = 200;
    private final int ID_FLASH = 201;
    private final int ID_RSSI = 202;

    private boolean flash_on_off = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitor, container, false);

        view.findViewById(R.id.connect).setOnClickListener(this);
        view.findViewById(R.id.flash).setOnClickListener(this);
        flash_button = view.findViewById(R.id.flash);
        monitor = view.findViewById(R.id.monitor);
        rssi_text = view.findViewById(R.id.rssi);
        ip_text = view.findViewById(R.id.ip);

        ip_text.setText("192.168.254.168");

        stream_thread = new HandlerThread("http");
        stream_thread.start();
        stream_handler = new HttpHandler(stream_thread.getLooper());

        flash_thread = new HandlerThread("http");
        flash_thread.start();
        flash_handler = new HttpHandler(flash_thread.getLooper());

        rssi_thread = new HandlerThread("http");
        rssi_thread.start();
        rssi_handler = new HttpHandler(rssi_thread.getLooper());

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stream_thread.quit();
        flash_thread.quit();
        rssi_thread.quit();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.connect) {
            if (!TextUtils.isEmpty(ip_text.getText())) {
                stream_handler.sendEmptyMessage(ID_CONNECT);
                rssi_handler.sendEmptyMessage(ID_RSSI);
            } else {
                Toast.makeText(getContext(), "Please enter IP address", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.flash) {
            flash_handler.sendEmptyMessage(ID_FLASH);
        }
    }

    private class HttpHandler extends Handler {
        public HttpHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ID_CONNECT:
                    VideoStream();
                    break;
                case ID_FLASH:
                    SetFlash();
                    break;
                case ID_RSSI:
                    GetRSSI();
                    break;
                default:
                    break;
            }
        }
    }

    private void SetFlash() {
        flash_on_off ^= true;

        String flash_url;
        if (flash_on_off) {
            flash_url = "http://" + ip_text.getText() + ":80/led?var=flash&val=1";
        } else {
            flash_url = "http://" + ip_text.getText() + ":80/led?var=flash&val=0";
        }

        try {
            URL url = new URL(flash_url);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");
            huc.setConnectTimeout(1000 * 5);
            huc.setReadTimeout(1000 * 5);
            huc.setDoInput(true);
            huc.connect();
            if (huc.getResponseCode() == 200) {
                InputStream in = huc.getInputStream();
                InputStreamReader isr = new InputStreamReader(in);
                BufferedReader br = new BufferedReader(isr);
                br.readLine(); // to read the response if necessary
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void GetRSSI() {
        rssi_handler.sendEmptyMessageDelayed(ID_RSSI, 500);

        String rssi_url = "http://" + ip_text.getText() + ":80/RSSI";

        try {
            URL url = new URL(rssi_url);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");
            huc.setConnectTimeout(1000 * 5);
            huc.setReadTimeout(1000 * 5);
            huc.setDoInput(true);
            huc.connect();
            if (huc.getResponseCode() == 200) {
                InputStream in = huc.getInputStream();
                InputStreamReader isr = new InputStreamReader(in);
                BufferedReader br = new BufferedReader(isr);
                final String data = br.readLine();
                if (!TextUtils.isEmpty(data)) {
                    getActivity().runOnUiThread(() -> rssi_text.setText(data));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void VideoStream() {
        String stream_url = "http://" + ip_text.getText() + ":81/stream";

        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(stream_url);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");
            huc.setConnectTimeout(1000 * 5);
            huc.setReadTimeout(1000 * 5);
            huc.setDoInput(true);
            huc.connect();

            if (huc.getResponseCode() == 200) {
                InputStream in = huc.getInputStream();
                InputStreamReader isr = new InputStreamReader(in);
                BufferedReader br = new BufferedReader(isr);

                String data;
                int len;
                byte[] buffer;

                while ((data = br.readLine()) != null) {
                    if (data.contains("Content-Type:")) {
                        data = br.readLine();
                        len = Integer.parseInt(data.split(":")[1].trim());
                        bis = new BufferedInputStream(in);
                        buffer = new byte[len];
                        int t = 0;
                        while (t < len) {
                            t += bis.read(buffer, t, len - t);
                        }
                        Bytes2ImageFile(buffer, getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/0A.jpg");
                        final Bitmap bitmap = BitmapFactory.decodeFile(getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/0A.jpg");

                        getActivity().runOnUiThread(() -> monitor.setImageBitmap(bitmap));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
                if (fos != null) {
                    fos.close();
                }
                stream_handler.sendEmptyMessageDelayed(ID_CONNECT, 3000);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void Bytes2ImageFile(byte[] bytes, String fileName) {
        try {
            File file = new File(fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes, 0, bytes.length);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
