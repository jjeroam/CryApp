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

public class DiscomfortRecommendations extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_discomfort_recommendations);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set the text for the TextView with ID textreco
        TextView textReco = findViewById(R.id.discomforttextreco);
        textReco.setText("1. Babies may cry if they feel too hot or too cold. Check that your baby’s clothes are not too tight and uncomfortable, too warm or too cold, particularly if the temperature has changed since you dressed them.\n\n" +
                "2. Do a quick diaper check or perform a “sniff test.” Check to see that they’re dry or that there is no skin irritation caused by a wet nappy, that might be irritating your baby.\n\n" +
                "3. Inspect for itchy tags or other small things that could be wrong.\n\n" +
                "4. They might not be in a very comfortable position while breastfeeding. Try changing your baby’s position or attachment to help settle them.");
    }
}
