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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private static final String TAG = "HistoryFragment";
    private boolean isAscending = true; // Toggle for sorting order

    public static class HistoryItem {
        private final String date;
        private final String time;
        private final List<String> interpretations;

        public HistoryItem(String date, String time, List<String> interpretations) {
            this.date = date;
            this.time = time;
            this.interpretations = interpretations;
        }

        public String getDate() { return date; }
        public String getTime() { return time; }
        public List<String> getInterpretations() { return interpretations; }

        public double getFirstInterpretationScore() {
            if (!interpretations.isEmpty()) {
                String firstInterpretation = interpretations.get(0);
                return extractScore(firstInterpretation);
            }
            return 0.0;
        }

        private double extractScore(String interpretation) {
            try {
                String percentage = interpretation.substring(interpretation.lastIndexOf("(") + 1, interpretation.lastIndexOf("%"));
                return Double.parseDouble(percentage);
            } catch (Exception e) {
                return 0.0; // Default to 0 if parsing fails
            }
        }
    }

    private static class HistoryAdapter extends ArrayAdapter<HistoryItem> {
        private final int textColor;

        HistoryAdapter(@NonNull android.content.Context context, @NonNull List<HistoryItem> objects) {
            super(context, R.layout.list_item_history, objects);
            this.textColor = ContextCompat.getColor(context, R.color.colorPrimary);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_history, parent, false);
            }

            HistoryItem item = getItem(position);
            if (item != null) {
                ((TextView) convertView.findViewById(R.id.textViewDate)).setText(item.getDate());
                ((TextView) convertView.findViewById(R.id.textViewTime)).setText(item.getTime());

                ((TextView) convertView.findViewById(R.id.textViewDate)).setTextColor(textColor);
                ((TextView) convertView.findViewById(R.id.textViewTime)).setTextColor(textColor);

                LinearLayout interpretationsContainer = convertView.findViewById(R.id.textViewInterpretationsContainer);
                interpretationsContainer.removeAllViews();
                for (String interpretation : item.getInterpretations()) {
                    TextView interpretationView = new TextView(getContext());
                    interpretationView.setText(interpretation);
                    interpretationView.setTextSize(14f);
                    interpretationView.setTextColor(textColor);
                    interpretationsContainer.addView(interpretationView);
                }
            }
            return convertView;
        }
    }

    private ListView historyListView;
    private HistoryAdapter historyAdapter;
    private DatabaseHelper dbHelper;
    private SimpleDateFormat sdfDate, sdfTime;
    private List<HistoryItem> historyItems;
    private ImageButton sortButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        historyListView = view.findViewById(R.id.historyListView);
        ImageButton datePickerButton = view.findViewById(R.id.buttonDatePicker);
        ImageButton clearAllButton = view.findViewById(R.id.buttonClearAll);
        ImageButton sortButton = view.findViewById(R.id.buttonSort);

        dbHelper = new DatabaseHelper(requireContext());
        sdfDate = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        sdfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        if (historyListView.getHeaderViewsCount() == 0) {
            historyListView.addHeaderView(inflater.inflate(R.layout.list_item_history_header, historyListView, false));
        }

        historyItems = getRecordingHistory();
        historyAdapter = new HistoryAdapter(requireContext(), historyItems);
        historyListView.setAdapter(historyAdapter);

        datePickerButton.setOnClickListener(v -> showDatePickerDialog());
        clearAllButton.setOnClickListener(v -> showClearAllConfirmationDialog());
        sortButton.setOnClickListener(v -> toggleSortOrder());

        return view;
    }

    private void toggleSortOrder() {
        isAscending = !isAscending;
        Collections.sort(historyItems, (item1, item2) -> {
            double score1 = item1.getFirstInterpretationScore();
            double score2 = item2.getFirstInterpretationScore();
            return isAscending ? Double.compare(score1, score2) : Double.compare(score2, score1);
        });
        historyAdapter.notifyDataSetChanged();
        Toast.makeText(requireContext(), isAscending ? "Sorted: Ascending" : "Sorted: Descending", Toast.LENGTH_SHORT).show();
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.set(year, month, dayOfMonth);
            filterHistoryByDate(sdfDate.format(selectedDate.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
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
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirm")
                .setMessage("Are you sure you want to clear all history?")
                .setPositiveButton(android.R.string.yes, (dialog, which) -> clearAllHistory())
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void clearAllHistory() {
        try (SQLiteDatabase db = dbHelper.getWritableDatabase()) {
            db.delete(DatabaseHelper.TABLE_HISTORY, null, null);
        }
        if (!historyItems.isEmpty()) {
            historyItems.clear();
            historyAdapter.notifyDataSetChanged();
        }
        Toast.makeText(requireContext(), "All history cleared", Toast.LENGTH_SHORT).show();
    }

    private List<HistoryItem> getRecordingHistory() {
        List<HistoryItem> list = new ArrayList<>();
        Gson gson = new Gson();

        try (SQLiteDatabase db = dbHelper.getReadableDatabase();
             Cursor cursor = db.query(DatabaseHelper.TABLE_HISTORY,
                     new String[]{DatabaseHelper.COLUMN_TIMESTAMP, DatabaseHelper.COLUMN_RESULTS},
                     null, null, null, null, DatabaseHelper.COLUMN_TIMESTAMP + " DESC")) {

            while (cursor.moveToNext()) {
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP));
                String resultsJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_RESULTS));
                List<Category> categories = gson.fromJson(resultsJson, new TypeToken<List<Category>>() {}.getType());
                List<String> interpretations = new ArrayList<>();
                for (int i = 0; i < Math.min(categories.size(), 2); i++) {
                    interpretations.add(categories.get(i).getLabel() + " (" + String.format("%.2f%%", categories.get(i).getScore() * 100) + ")");
                }
                list.add(new HistoryItem(sdfDate.format(new Date(timestamp)), sdfTime.format(new Date(timestamp)), interpretations));
            }
        }
        return list;
    }
}
