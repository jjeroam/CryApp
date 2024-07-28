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

public class BurpingRecommendations extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_burping_recommendations);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set the text for the TextView with ID textreco
        TextView textReco = findViewById(R.id.burptextreco);
        textReco.setText("1. Hold them upright, to make your baby burp try to gently bounce and rock them while rubbing their back. \n\n" +
                "2. Start with the baby in an upright position and drape them over your shoulder. Baby’s head should be at your shoulder level, with their child resting on your shoulder, gently pat, rub and massage their back to encourage the trapped air out. \n\n" +
                "3. Try laying them on their stomach on your lap, be mindful of supporting the baby, and steady them with one hand, while gently patting and rubbing their back with the other.\n\n" +
                "4. Sitting the baby upright on your lap facing away from you. Lean their weight slightly forward against one of your hands, making sure to support their chest and head, and gently pat their back with your other hand.");
    }
}
