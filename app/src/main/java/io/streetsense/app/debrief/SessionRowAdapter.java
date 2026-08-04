package io.streetsense.app.debrief;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.streetsense.app.R;

final class SessionRowAdapter extends RecyclerView.Adapter<SessionRowAdapter.ViewHolder> {

    interface OnSessionClickListener {
        void onSessionClick(JSONObject session);
    }

    private List<JSONObject> sessions = new ArrayList<>();
    private final OnSessionClickListener listener;

    SessionRowAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    void submitList(List<JSONObject> newSessions) {
        this.sessions = newSessions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject session = sessions.get(position);
        String activity = session.optString("activity", "SESSION");
        double dose = session.optDouble("inhaledPm25Micrograms", 0);
        int seconds = session.optInt("durationSeconds", 0);

        holder.rowActivity.setText(capitalize(activity));
        holder.rowMeta.setText(String.format(Locale.US, "%d:%02d — %.2f µg inhaled",
                seconds / 60, seconds % 60, dose));
        holder.itemView.setOnClickListener(v ->
                listener.onSessionClick(sessions.get(holder.getBindingAdapterPosition())));
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.US);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView rowActivity;
        final TextView rowMeta;

        ViewHolder(View itemView) {
            super(itemView);
            rowActivity = itemView.findViewById(R.id.rowActivity);
            rowMeta = itemView.findViewById(R.id.rowMeta);
        }
    }
}
