package com.example.babycry.helper;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.babycry.R;

import org.tensorflow.lite.support.label.Category;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ListView historyListView;
    private List<RecordingHistory> history;
    private ArrayAdapter<String> historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Activity view = null;
        historyListView = findViewById(R.id.historyListView);

        // Get history from intent or shared preferences
        // For simplicity, we use a static list here
        history = getRecordingHistory();

        List<String> displayList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        for (RecordingHistory record : history) {
            String timestamp = sdf.format(record.getTimestamp());
            List<Category> topCategories = record.getTopCategories();
            String topResults = "";
            for (int i = 0; i < topCategories.size(); i++) {
                Category category = topCategories.get(i);
                topResults += category.getLabel() + " (" + String.format("%.2f%%", category.getScore() * 100) + ")";
                if (i < topCategories.size() - 1) {
                    topResults += ", ";
                }
            }
            displayList.add(timestamp + ": " + topResults);
        }

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        historyListView.setAdapter(historyAdapter);
    }

    private List<RecordingHistory> getRecordingHistory() {
        // For now, returning a static list
        // In a real app, this would retrieve history from a database or shared preferences
        List<RecordingHistory> list = new ArrayList<>();
        // Example data
        list.add(new RecordingHistory(System.currentTimeMillis() - 3600000, List.of(
                new Category("tired", 0.234f),
                new Category("discomfort", 0.205f)
        )));
        return list;
    }
}
