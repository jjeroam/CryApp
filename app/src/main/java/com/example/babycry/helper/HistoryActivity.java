package com.example.babycry.helper;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.babycry.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.tensorflow.lite.support.label.Category;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ListView historyListView;
    private ArrayAdapter<String> historyAdapter;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        historyListView = findViewById(R.id.historyListView);
        dbHelper = new DatabaseHelper(this);

        // Insert example data into the database
        insertExampleData();

        // Get history from the database
        List<RecordingHistory> history = getRecordingHistory();

        List<String> displayList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        for (RecordingHistory record : history) {
            String timestamp = sdf.format(record.getTimestamp());
            List<Category> topCategories = record.getTopCategories();
            StringBuilder topResults = new StringBuilder();
            for (int i = 0; i < topCategories.size(); i++) {
                Category category = topCategories.get(i);
                topResults.append(category.getLabel())
                        .append(" (")
                        .append(String.format("%.2f%%", category.getScore() * 100))
                        .append(")");
                if (i < topCategories.size() - 1) {
                    topResults.append(", ");
                }
            }
            displayList.add(timestamp + ": " + topResults.toString());
        }

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        historyListView.setAdapter(historyAdapter);
    }

    private void insertExampleData() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Example categories
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("tired", 0.234f));
        categories.add(new Category("discomfort", 0.205f));

        // Serialize categories to JSON
        Gson gson = new Gson();
        String resultsJson = gson.toJson(categories);

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_TIMESTAMP, System.currentTimeMillis() - 3600000);
        values.put(DatabaseHelper.COLUMN_RESULTS, resultsJson);

        db.insert(DatabaseHelper.TABLE_HISTORY, null, values);
    }

    private List<RecordingHistory> getRecordingHistory() {
        List<RecordingHistory> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                DatabaseHelper.TABLE_HISTORY,
                new String[]{DatabaseHelper.COLUMN_TIMESTAMP, DatabaseHelper.COLUMN_RESULTS},
                null, null, null, null, null
        );

        Gson gson = new Gson();

        while (cursor.moveToNext()) {
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP));
            String resultsJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RESULTS));

            // Log the retrieved JSON string for debugging
            System.out.println("Retrieved JSON: " + resultsJson);

            // Deserialize JSON to list of categories
            List<Category> categories = gson.fromJson(resultsJson, new TypeToken<List<Category>>(){}.getType());

            list.add(new RecordingHistory(timestamp, categories));
        }
        cursor.close();

        return list;
    }
}
