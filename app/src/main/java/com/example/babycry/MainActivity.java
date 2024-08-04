package com.example.babycry;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.github.mikephil.charting.charts.BarChart;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BarChart barChart = findViewById(R.id.barChart);

        // Set the no data text
        barChart.setNoDataText("No cry detected");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false); // Hide default title

        BottomNavigationView navView = findViewById(R.id.nav_view);

        // Obtain NavHostFragment
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
        if (navHostFragment != null) {
            // Obtain NavController from NavHostFragment
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(navView, navController);
        } else {
            throw new IllegalStateException("NavHostFragment not found");
        }
    }
}