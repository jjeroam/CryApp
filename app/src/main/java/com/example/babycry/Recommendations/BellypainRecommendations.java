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

public class BellypainRecommendations extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_bellypain_recommendations);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set the text for the TextView with ID textreco
        TextView textReco = findViewById(R.id.bellytextreco);
        textReco.setText("1. Is your crying baby also wriggling, arching their back or pumping their legs, this is a sign of baby gas, try to bicycle their legs and push them up to the chest to help relieve the gas.\n\n" +
                "2. Some babies also experience an upset stomach when transitioning from breast milk to formula.\n\n" +
                "3. If your little eater seems to get extra fussy after mealtimes, it could be related to their diet.\n\n" +
                "4. Giving smaller, more frequent feeds and holding the baby upright for 15-20 minutes after each feeding can help reduce reflux or spitting up. ");
    }
}
