package com.example.samplestickerapp;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;

import java.lang.ref.WeakReference;

public class StickerPackDetailsActivity extends AddStickerPackActivity {

    public static final String EXTRA_STICKER_PACK_ID = "sticker_pack_id";
    public static final String EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority";
    public static final String EXTRA_STICKER_PACK_NAME = "sticker_pack_name";
    public static final String EXTRA_STICKER_PACK_WEBSITE = "sticker_pack_website";
    public static final String EXTRA_STICKER_PACK_EMAIL = "sticker_pack_email";
    public static final String EXTRA_STICKER_PACK_PRIVACY_POLICY = "sticker_pack_privacy_policy";
    public static final String EXTRA_STICKER_PACK_LICENSE_AGREEMENT = "sticker_pack_license_agreement";
    public static final String EXTRA_STICKER_PACK_TRAY_ICON = "sticker_pack_tray_icon";
    public static final String EXTRA_SHOW_UP_BUTTON = "show_up_button";
    public static final String EXTRA_STICKER_PACK_DATA = "sticker_pack";

    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private StickerPreviewAdapter stickerPreviewAdapter;
    private int numColumns;
    private View addButton;
    private View alreadyAddedText;
    private StickerPack stickerPack;
    private View divider;
    private WhiteListCheckAsyncTask whiteListCheckAsyncTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_sticker_pack_details);

        // 2. WindowInsets padding on root
        View root = findViewById(R.id.root_layout);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
        // optional: dark status-bar icons
        WindowInsetsControllerCompat ic =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (ic != null) ic.setAppearanceLightStatusBars(true);

        // --- UI binding ---
        boolean showUpButton = getIntent().getBooleanExtra(EXTRA_SHOW_UP_BUTTON, false);
        stickerPack = getIntent().getParcelableExtra(EXTRA_STICKER_PACK_DATA);

        TextView nameTv   = findViewById(R.id.pack_name);
        TextView authorTv = findViewById(R.id.author);
        ImageView trayIv  = findViewById(R.id.tray_image);
        TextView sizeTv   = findViewById(R.id.pack_size);
        SimpleDraweeView expandedIv = findViewById(R.id.sticker_details_expanded_sticker);

        addButton        = findViewById(R.id.add_to_whatsapp_button);
        alreadyAddedText = findViewById(R.id.already_added_text);

        layoutManager = new GridLayoutManager(this, 1);
        recyclerView  = findViewById(R.id.sticker_list);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(pageLayoutListener);
        recyclerView.addOnScrollListener(dividerScrollListener);
        divider = findViewById(R.id.divider);

        stickerPreviewAdapter = new StickerPreviewAdapter(
                getLayoutInflater(),
                R.drawable.sticker_error,
                getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_size),
                getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_padding),
                stickerPack,
                expandedIv
        );
        recyclerView.setAdapter(stickerPreviewAdapter);

        nameTv.setText(stickerPack.name);
        authorTv.setText(stickerPack.publisher);
        trayIv.setImageURI(
                StickerPackLoader.getStickerAssetUri(stickerPack.identifier, stickerPack.trayImageFile)
        );
        sizeTv.setText(Formatter.formatShortFileSize(this, stickerPack.getTotalSize()));

        addButton.setOnClickListener(v ->
                addStickerPackToWhatsApp(stickerPack.identifier, stickerPack.name)
        );
        findViewById(R.id.sticker_pack_animation_indicator)
                .setVisibility(stickerPack.animatedStickerPack ? View.VISIBLE : View.GONE);
    }

    // --- removed toolbar/menu/action-bar overrides completely ---

    private void launchInfoActivity(String website, String email,
                                    String privacy, String license, String trayUri) {
        Intent i = new Intent(this, StickerPackInfoActivity.class);
        i.putExtra(EXTRA_STICKER_PACK_ID, stickerPack.identifier);
        i.putExtra(EXTRA_STICKER_PACK_WEBSITE, website);
        i.putExtra(EXTRA_STICKER_PACK_EMAIL, email);
        i.putExtra(EXTRA_STICKER_PACK_PRIVACY_POLICY, privacy);
        i.putExtra(EXTRA_STICKER_PACK_LICENSE_AGREEMENT, license);
        i.putExtra(EXTRA_STICKER_PACK_TRAY_ICON, trayUri);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        whiteListCheckAsyncTask = new WhiteListCheckAsyncTask(this);
        whiteListCheckAsyncTask.execute(stickerPack);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (whiteListCheckAsyncTask != null && !whiteListCheckAsyncTask.isCancelled()) {
            whiteListCheckAsyncTask.cancel(true);
        }
    }

    private void updateAddUI(boolean isWhitelisted) {
        addButton.setVisibility(isWhitelisted ? View.GONE : View.VISIBLE);
        alreadyAddedText.setVisibility(isWhitelisted ? View.VISIBLE : View.GONE);
        findViewById(R.id.sticker_pack_details_tap_to_preview)
                .setVisibility(isWhitelisted ? View.GONE : View.VISIBLE);
    }

    // --- layout listeners & AsyncTask unchanged ---

    private final ViewTreeObserver.OnGlobalLayoutListener pageLayoutListener =
            () -> setNumColumns(
                    recyclerView.getWidth() /
                            getResources().getDimensionPixelSize(R.dimen.sticker_pack_details_image_size)
            );

    private void setNumColumns(int cols) {
        if (numColumns != cols) {
            layoutManager.setSpanCount(cols);
            numColumns = cols;
            stickerPreviewAdapter.notifyDataSetChanged();
        }
    }

    private final RecyclerView.OnScrollListener dividerScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                    boolean show = rv.computeVerticalScrollOffset() > 0;
                    if (divider != null) divider.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                }
            };

    static class WhiteListCheckAsyncTask extends AsyncTask<StickerPack, Void, Boolean> {
        private final WeakReference<StickerPackDetailsActivity> ref;
        WhiteListCheckAsyncTask(StickerPackDetailsActivity act) { ref = new WeakReference<>(act); }

        @Override
        protected Boolean doInBackground(StickerPack... packs) {
            StickerPackDetailsActivity act = ref.get();
            if (act == null) return false;
            return WhitelistCheck.isWhitelisted(act, packs[0].identifier);
        }

        @Override
        protected void onPostExecute(Boolean isWhite) {
            StickerPackDetailsActivity act = ref.get();
            if (act != null) act.updateAddUI(isWhite);
        }
    }
}
