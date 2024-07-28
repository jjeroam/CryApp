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

public class TiredRecommendations extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tired_recommendations);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set the text for the TextView with ID textreco
        TextView textReco = findViewById(R.id.tiredtextreco);
        textReco.setText("1. Swaddling a baby helps them to get into sleep. Swaddling them may stop them from startling themselves awake when their legs and arms jerk involuntarily. Also, swaddling them may help in letting them feel safe and cozy.\n\n" +
                "2. Letting them use a pacifier can help in lulling them to sleep.\n\n" +
                "3. Holding a baby close to you and having them hear your heartbeat may also help in letting them sleep.\n\n" +
                "4. Softly rocking your baby is also one of the ways to lull them to sleep.\n\n" +
                "5. Some babies can also easily sleep with soft hushes and lullabies.\n\n" +
                "6. Making a gentle shushing sound directly into the baby's ear, which is similar to the noises they heard in the womb can help them to sleep.\n\n" +
                "7. Try swinging or gently jiggling the baby to get them to calm down (while always taking care to support the baby's head and neck).");
    }
}
