package com.algorobo.quiz;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.algorobo.quiz.ExamCategoryCatalog.Category;
import com.algorobo.quiz.ExamCategoryCatalog.ExamType;
import java.util.ArrayList;
import java.util.List;

public class ExamOrderDialog {

    private final Context context;
    private final List<Category> categories;
    private final List<Category> order;
    private final ExamType currentType;
    private AlertDialog dialog;
    private OrderAdapter adapter;
    private final Runnable onDone;

    public interface OnOrderChangedListener {
        void onChanged(List<String> categoryNames);
    }

    public ExamOrderDialog(Context context, int currentTypeIndex, Runnable onDone) {
        this.context = context;
        this.currentType = ExamCategoryCatalog.getByIndex(currentTypeIndex);
        this.categories = new ArrayList<>(currentType.categories);
        this.order = new ArrayList<>(currentType.categories);
        this.onDone = onDone;
    }

    public void show() {
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(dp(8), dp(8), dp(8), dp(8));

        adapter = new OrderAdapter();
        recyclerView.setAdapter(adapter);

        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                adapter.moveItem(source.getAdapterPosition(), target.getAdapterPosition());
                adapter.notifyItemMoved(source.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
            @Override
            public boolean isLongPressDragEnabled() { return true; }
            @Override
            public boolean isItemViewSwipeEnabled() { return false; }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);

        dialog = new AlertDialog.Builder(context)
                .setTitle("排序：" + currentType.name)
                .setView(recyclerView)
                .setPositiveButton("完成", (d, w) -> save())
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    private void save() {
        List<String> names = new ArrayList<>();
        for (Category c : order) names.add(c.name);
        DataStore.setCategoryOrder(context, currentType.name, names);
        if (onDone != null) onDone.run();
        if (dialog != null) dialog.dismiss();
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private class OrderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_CATEGORY = 0;
        private final List<Category> items = new ArrayList<>(order);

        OrderAdapter() {}

        void moveItem(int from, int to) {
            Category item = items.remove(from);
            items.add(to, item);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(context);
            tv.setTextSize(14);
            tv.setPadding(dp(32), dp(10), dp(16), dp(10));
            tv.setGravity(Gravity.CENTER_VERTICAL);
            return new CategoryViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Category c = items.get(position);
            ((CategoryViewHolder) holder).tv.setText((c.icon != null ? c.icon + " " : "") + c.name);
        }

        @Override
        public int getItemCount() { return items.size(); }

        @Override
        public int getItemViewType(int position) { return TYPE_CATEGORY; }
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        CategoryViewHolder(TextView v) { super(v); tv = v; }
    }
}
