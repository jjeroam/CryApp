package com.example.babycry.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.babycry.R;
import com.example.babycry.helper.DatabaseHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.tensorflow.lite.support.label.Category;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class HistoryFragment extends Fragment {

    public class HistoryItem {
        private String date;
        private String time;
        private List<String> interpretations;

        public HistoryItem(String date, String time, List<String> interpretations) {
            this.date = date;
            this.time = time;
            this.interpretations = interpretations;
        }

        public String getDate() {
            return date;
        }

        public String getTime() {
            return time;
        }

        public List<String> getInterpretations() {
            return interpretations;
        }
    }

    private class HistoryAdapter extends ArrayAdapter<HistoryItem> {

        HistoryAdapter(@NonNull android.content.Context context, @NonNull List<HistoryItem> objects) {
            super(context, R.layout.list_item_history, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_history, parent, false);
            }

            HistoryItem item = getItem(position);

            if (item != null) {
                TextView dateView = convertView.findViewById(R.id.textViewDate);
                TextView timeView = convertView.findViewById(R.id.textViewTime);
                LinearLayout interpretationsContainer = convertView.findViewById(R.id.textViewInterpretationsContainer);

                dateView.setText(item.getDate());
                timeView.setText(item.getTime());

                // Use theme-based color for text
                int textColor = ContextCompat.getColor(getContext(), R.color.colorPrimary); // Use a theme-defined color resource
                dateView.setTextColor(textColor);
                timeView.setTextColor(textColor);

                // Clear previous views
                interpretationsContainer.removeAllViews();

                // Add each interpretation as a new row
                for (String interpretation : item.getInterpretations()) {
                    TextView interpretationView = new TextView(getContext());
                    interpretationView.setText(interpretation);
                    interpretationView.setTextSize(14f);
                    interpretationView.setTextColor(textColor); // Apply theme-based color
                    interpretationsContainer.addView(interpretationView);
                }
            }

            return convertView;
        }
    }

    private ListView historyListView;
    private HistoryAdapter historyAdapter;
    private DatabaseHelper dbHelper;
    private ImageButton datePickerButton;
    private ImageButton clearAllButton;
    private SimpleDateFormat sdfDate, sdfTime;
    private List<HistoryItem> historyItems;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        historyListView = view.findViewById(R.id.historyListView);
        datePickerButton = view.findViewById(R.id.buttonDatePicker);
        clearAllButton = view.findViewById(R.id.buttonClearAll);

        dbHelper = new DatabaseHelper(getContext());
        sdfDate = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        sdfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        // Add header view with labels only if it hasn't been added yet
        if (historyListView.getHeaderViewsCount() == 0) {
            View headerView = inflater.inflate(R.layout.list_item_history_header, historyListView, false);
            historyListView.addHeaderView(headerView);
        }

        // Get history from the database
        historyItems = getRecordingHistory();

        historyAdapter = new HistoryAdapter(getContext(), historyItems);
        historyListView.setAdapter(historyAdapter);

        datePickerButton.setOnClickListener(v -> showDatePickerDialog());
        clearAllButton.setOnClickListener(v -> showClearAllConfirmationDialog());

        return view;
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), (view, year, month, dayOfMonth) -> {
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.set(year, month, dayOfMonth);
            filterHistoryByDate(sdfDate.format(selectedDate.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void filterHistoryByDate(String selectedDate) {
        List<HistoryItem> filteredItems = new ArrayList<>();
        for (HistoryItem item : historyItems) {
            if (item.getDate().equals(selectedDate)) {
                filteredItems.add(item);
            }
        }
        historyAdapter.clear();
        historyAdapter.addAll(filteredItems);
    }

    private void showClearAllConfirmationDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("Confirm")
                .setMessage("Are you sure you want to clear all history?")
                .setPositiveButton(android.R.string.yes, (dialog, which) -> clearAllHistory())
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void clearAllHistory() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_HISTORY, null, null); // Clear all rows in the table
        db.close();

        historyItems.clear(); // Clear list
        historyAdapter.notifyDataSetChanged(); // Update the ListView

        Toast.makeText(getContext(), "All history cleared", Toast.LENGTH_SHORT).show();
    }

    private List<HistoryItem> getRecordingHistory() {
        List<HistoryItem> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                DatabaseHelper.TABLE_HISTORY,
                new String[]{DatabaseHelper.COLUMN_TIMESTAMP, DatabaseHelper.COLUMN_RESULTS},
                null, null, null, null, DatabaseHelper.COLUMN_TIMESTAMP + " DESC"
        );

        Gson gson = new Gson();
        while (cursor.moveToNext()) {
            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP));
            String resultsJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RESULTS));

            // Deserialize JSON to list of categories
            List<Category> categories;
            try {
                categories = gson.fromJson(resultsJson, new TypeToken<List<Category>>() {}.getType());
            } catch (Exception e) {
                Log.e("HistoryFragment", "JSON parsing error: " + e.getMessage());
                categories = new ArrayList<>();
            }

            // Convert timestamp to Date object
            Date dateObj = new Date(timestamp);

            // Format date and time
            String date = sdfDate.format(dateObj);
            String time = sdfTime.format(dateObj);

            // Create list of interpretations
            List<String> interpretations = new ArrayList<>();
            for (int i = 0; i < Math.min(categories.size(), 2); i++) {
                Category category = categories.get(i);
                interpretations.add(category.getLabel() + " (" + String.format("%.2f%%", category.getScore() * 100) + ")");
            }

            // Avoid adding duplicate history items
            boolean isDuplicate = list.stream()
                    .anyMatch(item -> item.getDate().equals(date) && item.getTime().equals(time) && item.getInterpretations().equals(interpretations));
            if (!isDuplicate) {
                list.add(new HistoryItem(date, time, interpretations));
            }

            // Debug logging
            Log.d("HistoryFragment", "Added HistoryItem - Date: " + date + ", Time: " + time + ", Interpretations: " + interpretations);
        }
        cursor.close();

        return list;
    }
}
