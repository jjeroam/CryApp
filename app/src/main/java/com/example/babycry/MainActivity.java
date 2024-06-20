package com.example.babycry;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;


import androidx.appcompat.app.AppCompatActivity;

import com.example.babycry.audio.CryInterpreter;
import com.example.babycry.helper.cam;


public class MainActivity extends AppCompatActivity {

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    public void onRecord(View view){
        Intent intent = new Intent(this, CryInterpreter.class);
        intent.putExtra("name", "CryInterpreter");
        startActivity(intent);
    }

    public void onMonitor(View view){
        Intent intent = new Intent(this, cam.class);
        intent.putExtra("name", "CryInterpreter");
        startActivity(intent);
    }


}