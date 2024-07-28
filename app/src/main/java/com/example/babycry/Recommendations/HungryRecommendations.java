package com.example.babycry.Recommendations;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.babycry.R;

public class HungryRecommendations extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hungry_recommendations);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set the text for the TextView with ID textreco
        TextView textReco = findViewById(R.id.hungrytextreco);
        textReco.setText("1. Feed your baby the moment you notice the signs of hunger. Responding early will help you avoid having to deal with long bouts of crying.\n\n" +
                "2. If a baby starts eating solid food, be very careful of choking hazards so keep the texture smooth and runny to help your infant enjoy the process of learning to eat.\n\n" +
                "3. When feeding your baby, make sure it’s an enjoyable experience. Seat baby in a safe high chair and minimize distractions. Allow sufficient time to complete the meal and do not force your baby if he is not hungry or interested in eating.");
    }
}
