package com.jhooni467.astroweb;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** AstroWeb 0.15 — top-aligned page previews in the tab overview. */
public class MainActivity extends Activity {
    private static final String TAG = "AstroWeb";
    private static final int REQUEST_SAVE_PDF = 7101;
    private static final int REQUEST_SAVE_DOWNLOAD = 7102;
    private static final int REQUEST_ANDROID_SITE_PERMISSION = 7103;
    private static final String HOME_URL = "resource://android/assets/home.html";
    private static final int BG = Color.rgb(19, 23, 20);
    private static final int BAR = Color.rgb(27, 33, 28);
    private static final int TEXT = Color.rgb(235, 239, 233);
    private static final int MUTED = Color.rgb(157, 166, 156);
    private static final int ACCENT = Color.rgb(169, 190, 166);
    private static final int DIVIDER = Color.rgb(52, 62, 53);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BrowserTab> tabs = new ArrayList<>();

    private GeckoView geckoView;
    private FrameLayout browserRoot;
    private FrameLayout browserFrame;
    private EditText addressField;
    private TextView tabCounter;
    private LinearLayout addressRow;
    private LinearLayout topChrome;
    private View addressDivider;
    private ProgressBar pageProgress;
    private ImageButton clearAddressButton;
    private PopupWindow suggestionPopup;
    private TextWatcher addressWatcher;
    private ValueAnimator addressAnimator;
    private int addressAnimationToken = 0;
    private int lastScrollY = 0;
    private float scrollAccumulator = 0f;
    private int scrollDirection = 0;
    private boolean addressBarHidden = false;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageButton bookmarkButton;
    private FrameLayout failureOverlay;
    private BrowserTab activeTab;
    private SharedPreferences storage;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private File pendingPdfFile;
    private InputStream pendingDownloadStream;
    private String pendingDownloadName;
    private String pendingDownloadMime;
    private GeckoSession.PermissionDelegate.Callback pendingAndroidPermissionCallback;
    private boolean ignoreNextScrollUpdate = true;
    private static class BrowserTab {
        GeckoSession session;
        String url = HOME_URL;
        String title = "Nueva pestaña";
        String serializedState;
        Bitmap thumbnail;
        boolean privateMode;
        boolean desktopMode;
        boolean canGoBack;
        boolean canGoForward;
        boolean recoveryScheduled;
        int recoveryAttempts;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BAR);
        storage = getSharedPreferences("astroweb_browser", MODE_PRIVATE);
        buildInterface();
        if (!restoreTabsFromStorage()) {
            BrowserTab initialTab = new BrowserTab();
            String lastUrl = storage.getString("last_url", HOME_URL);
            initialTab.url = lastUrl == null || lastUrl.isEmpty() ? HOME_URL : lastUrl;
            initialTab.serializedState = storage.getString("session_state", null);
            tabs.add(initialTab);
            initializeTab(initialTab, initialTab.url, true);
        }
    }

    private void buildInterface() {
        browserRoot = new FrameLayout(this);
        browserRoot.setBackgroundColor(BG);
        browserRoot.setClipChildren(true);
        browserRoot.setClipToPadding(true);
        browserRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(),
                    0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(BG);
        // The bottom browser controls cover this portion of the page.
        geckoView.setVerticalClipping(dp(50));

        browserFrame = new FrameLayout(this);
        browserFrame.setBackgroundColor(BG);
        browserFrame.addView(geckoView, new FrameLayout.LayoutParams(-1, -1));

        failureOverlay = new FrameLayout(this);
        failureOverlay.setBackgroundColor(BG);
        failureOverlay.setVisibility(View.GONE);

        LinearLayout failureContent = new LinearLayout(this);
        failureContent.setGravity(Gravity.CENTER);
        failureContent.setOrientation(LinearLayout.VERTICAL);
        failureContent.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextView failureTitle = new TextView(this);
        failureTitle.setText("No se pudo abrir la página");
        failureTitle.setTextColor(TEXT);
        failureTitle.setTextSize(17);
        failureTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        failureTitle.setGravity(Gravity.CENTER);
        failureContent.addView(failureTitle);

        TextView failureMessage = new TextView(this);
        failureMessage.setText("Comprueba tu conexión o la dirección e inténtalo de nuevo.");
        failureMessage.setTextColor(MUTED);
        failureMessage.setTextSize(13);
        failureMessage.setGravity(Gravity.CENTER);
        failureMessage.setPadding(0, dp(8), 0, dp(16));
        failureContent.addView(failureMessage);

        TextView retry = new TextView(this);
        retry.setText("Reintentar");
        retry.setTextColor(ACCENT);
        retry.setTextSize(14);
        retry.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(16), dp(10), dp(16), dp(10));
        retry.setOnClickListener(v -> {
            if (activeTab != null) {
                activeTab.recoveryAttempts = 0;
                activeTab.recoveryScheduled = false;
                restartTab(activeTab, activeTab.url);
            } else {
                createTab(HOME_URL);
            }
        });
        failureContent.addView(retry);
        failureOverlay.addView(failureContent,
                new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        browserFrame.addView(failureOverlay, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams browserParams = new FrameLayout.LayoutParams(-1, -1);
        browserParams.topMargin = dp(55);
        browserRoot.addView(browserFrame, browserParams);

        topChrome = new LinearLayout(this);
        topChrome.setOrientation(LinearLayout.VERTICAL);
        topChrome.setPadding(0, dp(4), 0, 0);
        topChrome.setBackgroundColor(BAR);
        topChrome.setElevation(dp(12));

        addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(7), 0, dp(5), 0);
        GradientDrawable addressSurface = new GradientDrawable();
        addressSurface.setColor(Color.rgb(36, 43, 37));
        addressSurface.setCornerRadius(dp(13));
        addressSurface.setStroke(dp(1), Color.rgb(56, 67, 57));
        addressRow.setBackground(addressSurface);

        ImageButton searchIcon = iconButton(R.drawable.ic_search, "Buscar", this::focusAddress);
        searchIcon.setImageTintList(ColorStateList.valueOf(MUTED));
        addressRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(36), dp(42)));

        addressField = new EditText(this);
        addressField.setSingleLine(true);
        addressField.setTextSize(14);
        addressField.setTextColor(TEXT);
        addressField.setHintTextColor(MUTED);
        addressField.setHint("Buscar o escribir dirección");
        addressField.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressField.setBackgroundColor(Color.TRANSPARENT);
        addressField.setPadding(dp(4), 0, dp(2), 0);
        addressField.setSelectAllOnFocus(false);
        addressField.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigate(addressField.getText().toString());
                return true;
            }
            return false;
        });
        addressField.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                animateAddressBar(true);
                showAddressSuggestions(addressField.getText().toString());
            } else {
                dismissAddressSuggestions();
            }
        });
        addressRow.addView(addressField, new LinearLayout.LayoutParams(0, dp(42), 1f));

        clearAddressButton = iconButton(R.drawable.ic_close, "Borrar texto", () -> {
            addressField.setText("");
            addressField.requestFocus();
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(addressField,
                    InputMethodManager.SHOW_IMPLICIT);
        });
        clearAddressButton.setImageTintList(ColorStateList.valueOf(MUTED));
        clearAddressButton.setVisibility(View.GONE);
        addressRow.addView(clearAddressButton, new LinearLayout.LayoutParams(dp(34), dp(42)));

        addressWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) {
                boolean hasText = text != null && text.length() > 0;
                clearAddressButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
                if (addressField.hasFocus()) {
                    showAddressSuggestions(text == null ? "" : text.toString());
                }
            }
        };
        addressField.addTextChangedListener(addressWatcher);

        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, dp(44));
        addressParams.leftMargin = dp(9);
        addressParams.rightMargin = dp(9);
        addressParams.bottomMargin = dp(4);
        topChrome.addView(addressRow, addressParams);

        addressDivider = new View(this);
        addressDivider.setBackgroundColor(DIVIDER);
        topChrome.addView(addressDivider, new LinearLayout.LayoutParams(-1, dp(1)));

        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        pageProgress.setProgress(0);
        pageProgress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        pageProgress.setProgressBackgroundTintList(
                ColorStateList.valueOf(Color.rgb(35, 43, 36)));
        pageProgress.setVisibility(View.INVISIBLE);
        topChrome.addView(pageProgress, new LinearLayout.LayoutParams(-1, dp(2)));

        browserRoot.addView(topChrome,
                new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        View bottomDivider = new View(this);
        bottomDivider.setBackgroundColor(DIVIDER);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setBackgroundColor(BAR);
        bottomBar.setElevation(dp(10));

        ImageButton home = bottomButton(R.drawable.ic_home, "Inicio", this::loadHome);
        bookmarkButton = bottomButton(R.drawable.ic_bookmark, "Marcadores", this::toggleBookmark);
        ImageButton search = bottomButton(R.drawable.ic_search, "Buscar", this::focusAddress);
        ImageButton tabs = bottomButton(R.drawable.ic_tabs, "Pestañas", this::showTabsDialog);
        ImageButton menu = bottomButton(R.drawable.ic_more, "Herramientas", () -> {});
        menu.setOnClickListener(v -> showBrowserMenu(v));

        addWeighted(bottomBar, home);
        addWeighted(bottomBar, bookmarkButton);
        addWeighted(bottomBar, search);
        addWeighted(bottomBar, tabs);
        addWeighted(bottomBar, menu);

        LinearLayout bottomChrome = new LinearLayout(this);
        bottomChrome.setOrientation(LinearLayout.VERTICAL);
        bottomChrome.addView(bottomDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        bottomChrome.addView(bottomBar, new LinearLayout.LayoutParams(-1, dp(49)));
        browserRoot.addView(bottomChrome,
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(browserRoot);
        updateNavigationButtons();
        updateTabCounter();
        updateBookmarkButton();
        browserRoot.requestApplyInsets();
    }

    private View menuAnchor() {
        return getWindow().getDecorView().findViewById(android.R.id.content);
    }

    private ImageButton iconButton(int icon, String description, Runnable action) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(MUTED));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setBackground(new ColorDrawable(Color.TRANSPARENT));
        applySoftRipple(button);
        button.setContentDescription(description);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private ImageButton bottomButton(int icon, String description, Runnable action) {
        ImageButton button = iconButton(icon, description, action);
        button.setImageTintList(ColorStateList.valueOf(TEXT));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setAdjustViewBounds(false);
        return button;
    }

    private void addWeighted(LinearLayout row, View button) {
        row.addView(button, new LinearLayout.LayoutParams(0, dp(49), 1f));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void focusAddress() {
        animateAddressBar(true);
        addressField.requestFocus();
        addressField.setSelection(0, addressField.getText().length());
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showSoftInput(addressField,
                InputMethodManager.SHOW_IMPLICIT);
        addressField.post(() -> {
            if (addressField.hasFocus()) {
                showAddressSuggestions(addressField.getText().toString());
            }
        });
    }

    private void dismissAddressSuggestions() {
        if (suggestionPopup != null) {
            suggestionPopup.dismiss();
            suggestionPopup = null;
        }
    }

    private static class AddressSuggestion {
        final String title, subtitle, url;
        final int icon;
        final boolean search;

        AddressSuggestion(String title, String subtitle, String url, int icon, boolean search) {
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.icon = icon;
            this.search = search;
        }
    }

    private void showAddressSuggestions(String rawQuery) {
        if (addressField == null || !addressField.hasFocus() || browserRoot == null) return;
        dismissAddressSuggestions();

        String query = rawQuery == null ? "" : rawQuery.trim();
        String lower = query.toLowerCase(java.util.Locale.ROOT);
        List<AddressSuggestion> suggestions = new ArrayList<>();
        if (!query.isEmpty()) {
            suggestions.add(new AddressSuggestion("Buscar \"" + shorten(query, 32) + "\"",
                    "Buscar en Google", null, R.drawable.ic_search, true));
        }

        Set<String> unique = new HashSet<>();
        List<String> saved = new ArrayList<>(
                storage.getStringSet("bookmarks", new HashSet<>()));
        Collections.reverse(saved);
        List<String> recent = new ArrayList<>();
        String history = storage.getString("history", "");
        if (!history.isEmpty()) {
            for (String item : history.split("\\n")) {
                if (!item.isEmpty()) recent.add(item);
            }
        }

        List<String> candidates = new ArrayList<>();
        candidates.addAll(saved);
        candidates.addAll(recent);
        for (String url : candidates) {
            if (!unique.add(url)) continue;
            String host = getTabHost(url);
            String match = (host + " " + url).toLowerCase(java.util.Locale.ROOT);
            if (!query.isEmpty() && !match.contains(lower)) continue;
            boolean isBookmark = saved.contains(url);
            suggestions.add(new AddressSuggestion(host,
                    isBookmark ? "Marcador guardado" : "Visitado recientemente",
                    url, isBookmark ? R.drawable.ic_bookmark : R.drawable.ic_history, false));
            if (suggestions.size() >= 5) break;
        }
        if (suggestions.isEmpty()) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(7), dp(8), dp(7));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(30, 37, 31));
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), Color.rgb(57, 68, 58));
        panel.setBackground(background);

        TextView heading = new TextView(this);
        heading.setText(query.isEmpty() ? "VISITAS RECIENTES" : "SUGERENCIAS");
        heading.setTextColor(MUTED);
        heading.setTextSize(9);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setLetterSpacing(.12f);
        heading.setPadding(dp(11), dp(5), dp(11), dp(7));
        panel.addView(heading);

        for (AddressSuggestion suggestion : suggestions) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), dp(6), dp(10), dp(6));
            applySoftRipple(item);

            ImageView icon = new ImageView(this);
            icon.setImageResource(suggestion.icon);
            icon.setImageTintList(ColorStateList.valueOf(ACCENT));
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(dp(21), dp(21));
            iconParams.rightMargin = dp(12);
            item.addView(icon, iconParams);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(this);
            title.setText(suggestion.title);
            title.setTextColor(TEXT);
            title.setTextSize(13);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(title);

            TextView subtitle = new TextView(this);
            subtitle.setText(suggestion.subtitle);
            subtitle.setTextColor(MUTED);
            subtitle.setTextSize(10);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
            subParams.topMargin = dp(2);
            labels.addView(subtitle, subParams);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

            item.setOnClickListener(v -> {
                dismissAddressSuggestions();
                String target = suggestion.search
                        ? "https://www.google.com/search?q=" + Uri.encode(query)
                        : suggestion.url;
                navigate(target);
            });
            panel.addView(item, new LinearLayout.LayoutParams(-1, dp(52)));
        }

        suggestionPopup = new PopupWindow(panel,
                getResources().getDisplayMetrics().widthPixels - dp(20),
                WindowManager.LayoutParams.WRAP_CONTENT, false);
        suggestionPopup.setFocusable(false);
        suggestionPopup.setTouchable(true);
        suggestionPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        suggestionPopup.setOutsideTouchable(true);
        suggestionPopup.setElevation(dp(18));
        suggestionPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        suggestionPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        suggestionPopup.setOnDismissListener(() -> suggestionPopup = null);
        suggestionPopup.showAsDropDown(addressRow, -dp(1), dp(5));
    }

    private void createTab(String url) {
        createTab(url, false);
    }

    private void createPrivateTab() {
        createTab(HOME_URL, true);
    }

    private void createTab(String url, boolean privateMode) {
        if (tabs.size() >= 20) {
            Toast.makeText(this, "Llegaste al límite de 20 pestañas", Toast.LENGTH_SHORT).show();
            return;
        }
        BrowserTab tab = new BrowserTab();
        tab.privateMode = privateMode;
        tab.url = url == null || url.isEmpty() ? HOME_URL : url;
        tabs.add(tab);
        persistTabsSnapshot();
        initializeTab(tab, tab.url, true);
        updateTabCounter();
    }

    private boolean restoreTabsFromStorage() {
        String snapshot = storage.getString("tabs_snapshot", null);
        if (snapshot == null || snapshot.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(snapshot);
            JSONArray savedTabs = root.optJSONArray("tabs");
            if (savedTabs == null || savedTabs.length() == 0) return false;

            for (int i = 0; i < savedTabs.length(); i++) {
                JSONObject saved = savedTabs.optJSONObject(i);
                if (saved == null) continue;
                BrowserTab tab = new BrowserTab();
                tab.url = saved.optString("url", HOME_URL);
                if (tab.url == null || tab.url.isEmpty()) tab.url = HOME_URL;
                tab.title = saved.optString("title", "Nueva pestaña");
                tab.desktopMode = saved.optBoolean("desktopMode", false);
                tab.serializedState = saved.optString("sessionState", null);
                tabs.add(tab);
            }
            if (tabs.isEmpty()) return false;

            int activeIndex = root.optInt("activeIndex", 0);
            if (activeIndex < 0 || activeIndex >= tabs.size()) activeIndex = 0;

            // Restore only the selected session immediately. Other tabs retain their
            // saved state and are opened on demand to reduce startup work and memory.
            BrowserTab selected = tabs.get(activeIndex);
            initializeTab(selected, selected.url, true);
            updateTabCounter();
            return true;
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Saved tab list could not be restored", error);
            tabs.clear();
            activeTab = null;
            return false;
        }
    }

    private void persistTabsSnapshot() {
        if (storage == null) return;
        try {
            JSONArray savedTabs = new JSONArray();
            int activeIndex = -1;
            for (BrowserTab tab : tabs) {
                if (tab == null || tab.privateMode) continue;
                JSONObject saved = new JSONObject();
                saved.put("url", tab.url == null ? HOME_URL : tab.url);
                saved.put("title", tab.title == null ? "Nueva pestaña" : tab.title);
                saved.put("desktopMode", tab.desktopMode);
                if (tab.serializedState != null && !tab.serializedState.isEmpty()) {
                    saved.put("sessionState", tab.serializedState);
                }
                savedTabs.put(saved);
                if (tab == activeTab) activeIndex = savedTabs.length() - 1;
            }

            // Keep a sensible limit so a large browsing session cannot grow
            // SharedPreferences without bounds.
            while (savedTabs.length() > 20) {
                savedTabs.remove(0);
                if (activeIndex >= 0) activeIndex--;
            }
            if (activeIndex < 0 && savedTabs.length() > 0) activeIndex = 0;

            JSONObject root = new JSONObject();
            root.put("tabs", savedTabs);
            root.put("activeIndex", activeIndex);
            SharedPreferences.Editor editor = storage.edit()
                    .putString("tabs_snapshot", root.toString());
            if (activeTab != null && !activeTab.privateMode) {
                editor.putString("last_url", activeTab.url);
            }
            editor.apply();
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Could not persist browser tabs", error);
        }
    }

    private void initializeTab(BrowserTab tab, String url, boolean activate) {
        initializeTab(tab, url, activate, true, true);
    }

    private void initializeTab(BrowserTab tab, String url, boolean activate,
            boolean loadInitialUri) {
        initializeTab(tab, url, activate, loadInitialUri, true);
    }

    private void initializeTab(BrowserTab tab, String url, boolean activate,
            boolean loadInitialUri, boolean openSession) {
        try {
            GeckoRuntime runtime = ((AstroWebApplication) getApplication()).getGeckoRuntime();
            GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                    .usePrivateMode(tab.privateMode)
                    .userAgentMode(tab.desktopMode
                            ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                            : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                    .viewportMode(tab.desktopMode
                            ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                            : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                    .build();
            GeckoSession newSession = new GeckoSession(settings);
            tab.session = newSession;
            tab.url = url == null ? HOME_URL : url;
            tab.canGoBack = false;
            tab.canGoForward = false;

            newSession.setContentDelegate(new GeckoSession.ContentDelegate() {
                @Override
                public void onTitleChange(GeckoSession current, String title) {
                    if (title != null && !title.trim().isEmpty()) {
                        tab.title = title.trim();
                        runOnUiThread(() -> persistTabsSnapshot());
                    }
                }

                @Override
                public void onCrash(GeckoSession crashedSession) {
                    Log.e(TAG, "Gecko content process crashed");
                    handleEngineStopped(tab, crashedSession);
                }

                @Override
                public void onKill(GeckoSession killedSession) {
                    Log.w(TAG, "Gecko content process was killed");
                    handleEngineStopped(tab, killedSession);
                }

                @Override
                public void onExternalResponse(GeckoSession current, WebResponse response) {
                    handleExternalDownload(response);
                }
            });

            newSession.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
                @Override
                public void onAndroidPermissionsRequest(GeckoSession current, String[] permissions,
                        GeckoSession.PermissionDelegate.Callback callback) {
                    requestAndroidSitePermissions(permissions, callback);
                }

                @Override
                public GeckoResult<Integer> onContentPermissionRequest(GeckoSession current,
                        GeckoSession.PermissionDelegate.ContentPermission permission) {
                    return requestContentSitePermission(permission, tab.privateMode);
                }

                @Override
                public void onMediaPermissionRequest(GeckoSession current, String uri,
                        GeckoSession.PermissionDelegate.MediaSource[] video,
                        GeckoSession.PermissionDelegate.MediaSource[] audio,
                        GeckoSession.PermissionDelegate.MediaCallback callback) {
                    requestMediaSitePermission(uri, video, audio, callback, tab.privateMode);
                }
            });

            newSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                @Override
                public GeckoResult<GeckoSession> onNewSession(GeckoSession current, String uri) {
                    Log.i(TAG, "Page requested a new tab: " + uri);
                    BrowserTab newTab = createWindowTab(uri);
                    return newTab == null || newTab.session == null
                            ? null : GeckoResult.fromValue(newTab.session);
                }

                @Override
                public void onLocationChange(GeckoSession current, String uri,
                        List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                        Boolean hasUserGesture) {
                    runOnUiThread(() -> {
                        if (uri == null) return;
                        if (!uri.equals(tab.url)) tab.serializedState = null;
                        tab.url = uri;
                        if (!tab.privateMode) storage.edit().putString("last_url", uri).apply();
                        if (uri.equals(HOME_URL)) {
                            lastScrollY = 0;
                            scrollAccumulator = 0f;
                            scrollDirection = 0;
                            ignoreNextScrollUpdate = true;
                            animateAddressBar(true);
                        }
                        persistTabsSnapshot();
                        if (tab == activeTab && addressField != null) {
                            addressField.setText(uri.equals(HOME_URL) ? "" : uri);
                            addressField.setSelection(addressField.getText().length());
                        }
                    });
                }

                @Override
                public void onCanGoBack(GeckoSession current, boolean value) {
                    runOnUiThread(() -> {
                        tab.canGoBack = value;
                        if (tab == activeTab) updateNavigationButtons();
                    });
                }

                @Override
                public void onCanGoForward(GeckoSession current, boolean value) {
                    runOnUiThread(() -> {
                        tab.canGoForward = value;
                        if (tab == activeTab) updateNavigationButtons();
                    });
                }

                @Override
                public GeckoResult<String> onLoadError(
                        GeckoSession current, String uri, WebRequestError error) {
                    Log.e(TAG, "Page load failed: " + uri + " — " + error);
                    runOnUiThread(() -> {
                        if (tab == activeTab) {
                            showFailure("No se pudo cargar la página",
                                    "Comprueba tu conexión o la dirección e inténtalo de nuevo.");
                        }
                    });
                    return null;
                }
            });

            newSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
                @Override
                public void onPageStart(GeckoSession current, String uri) {
                    runOnUiThread(() -> {
                        if (tab == activeTab) {
                            failureOverlay.setVisibility(View.GONE);
                            pageProgress.setProgress(0);
                            pageProgress.setVisibility(View.VISIBLE);
                        }
                    });
                }

                @Override
                public void onProgressChange(GeckoSession current, int progress) {
                    if (tab == activeTab) {
                        runOnUiThread(() -> {
                            pageProgress.setProgress(Math.max(0, Math.min(100, progress)));
                            if (progress >= 100) pageProgress.setVisibility(View.INVISIBLE);
                        });
                    }
                }

                @Override
                public void onPageStop(GeckoSession current, boolean success) {
                    if (tab == activeTab) {
                        runOnUiThread(() -> pageProgress.setVisibility(View.INVISIBLE));
                    }
                    if (success) {
                        tab.recoveryAttempts = 0;
                        if (!tab.privateMode) addHistory(tab.url);
                        current.flushSessionState();
                        if (tab == activeTab) {
                            handler.postDelayed(() -> captureTabPreview(tab, null), 320);
                        }
                    }
                }

                @Override
                public void onSessionStateChange(GeckoSession current,
                        GeckoSession.SessionState state) {
                    if (tab == activeTab || tab.session == current) saveSessionState(tab, state);
                }
            });

            newSession.setScrollDelegate(new GeckoSession.ScrollDelegate() {
                @Override
                public void onScrollChanged(GeckoSession session, int scrollX, int scrollY) {
                    if (tab != activeTab || addressField.hasFocus()
                            || (suggestionPopup != null && suggestionPopup.isShowing())
                            || (geckoView != null && geckoView.shouldPinOnScreen())) return;

                    int currentY = Math.max(0, scrollY);
                    if (ignoreNextScrollUpdate) {
                        lastScrollY = currentY;
                        scrollAccumulator = 0f;
                        scrollDirection = 0;
                        ignoreNextScrollUpdate = false;
                        return;
                    }

                    int delta = currentY - lastScrollY;
                    lastScrollY = currentY;

                    if (currentY <= dp(2)) {
                        scrollAccumulator = 0f;
                        scrollDirection = 0;
                        animateAddressBar(true);
                        return;
                    }
                    if (delta == 0) return;

                    // Count every real scroll step, including tiny changes from slow drags.
                    // Opposite movement cancels the accumulator naturally, filtering jitter.
                    scrollAccumulator += delta;
                    float hideThreshold = dp(42);
                    float showThreshold = -dp(30);
                    scrollAccumulator = Math.max(showThreshold,
                            Math.min(hideThreshold, scrollAccumulator));

                    if (!addressBarHidden && scrollAccumulator >= hideThreshold) {
                        scrollAccumulator = 0f;
                        animateAddressBar(false);
                    } else if (addressBarHidden && scrollAccumulator <= showThreshold) {
                        scrollAccumulator = 0f;
                        animateAddressBar(true);
                    }
                }
            });


                if (openSession) {
                newSession.open(runtime);
                if (activate) {
                    if (activeTab != null && activeTab != tab && activeTab.session != null) {
                        try {
                            activeTab.session.setFocused(false);
                            activeTab.session.setActive(false);
                            activeTab.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
                        } catch (RuntimeException ignored) { }
                    }
                    activeTab = tab;
                    persistTabsSnapshot();
                    lastScrollY = 0;
                    scrollAccumulator = 0f;
                    scrollDirection = 0;
                    ignoreNextScrollUpdate = true;
                    animateAddressBar(true);
                    setBrowserContentTopOffset(true);
                    geckoView.setSession(newSession);
                    newSession.setActive(true);
                    newSession.setFocused(true);
                    newSession.setPriorityHint(GeckoSession.PRIORITY_HIGH);
                    geckoView.coverUntilFirstPaint(BG);
                    failureOverlay.setVisibility(View.GONE);
                    addressField.setText(tab.url.equals(HOME_URL) ? "" : tab.url);
                    clearAddressButton.setVisibility(tab.url.equals(HOME_URL) ? View.GONE : View.VISIBLE);
                    pageProgress.setVisibility(View.INVISIBLE);
                    updateNavigationButtons();
                    updateBookmarkButton();
                } else {
                    newSession.setActive(false);
                    newSession.setFocused(false);
                    newSession.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
                }
                if (loadInitialUri && tab.serializedState != null && !tab.serializedState.isEmpty()) {
                    try {
                        GeckoSession.SessionState savedState =
                                GeckoSession.SessionState.fromString(tab.serializedState);
                        if (savedState == null) {
                            byte[] bytes = Base64.decode(tab.serializedState, Base64.DEFAULT);
                            Parcel parcel = Parcel.obtain();
                            try {
                                parcel.unmarshall(bytes, 0, bytes.length);
                                parcel.setDataPosition(0);
                                savedState = GeckoSession.SessionState.CREATOR.createFromParcel(parcel);
                            } finally {
                                parcel.recycle();
                            }
                        }
                        if (savedState != null) {
                            newSession.restoreState(savedState);
                        } else {
                            newSession.loadUri(tab.url);
                        }
                    } catch (Exception restoreError) {
                        Log.w(TAG, "Could not restore previous browser session", restoreError);
                        tab.serializedState = null;
                        newSession.loadUri(tab.url);
                    }
                } else if (loadInitialUri) {
                    newSession.loadUri(tab.url);
                }
            }
        } catch (Throwable error) {
            Log.e(TAG, "GeckoView initialization failed", error);
            if (tab.session != null) {
                try {
                    if (tab.session.isOpen()) tab.session.close();
                } catch (RuntimeException closeError) {
                    Log.w(TAG, "Could not discard failed session", closeError);
                }
                tab.session = null;
            }
            if (activate) showFailure("AstroWeb no pudo iniciarse",
                    "El motor del navegador no pudo arrancar. Inténtalo de nuevo.");
        }
    }

    private String permissionLabel(int permission) {
        switch (permission) {
            case GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION:
                return "tu ubicación";
            case GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION:
                return "mostrar notificaciones";
            case GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE:
                return "guardar datos del sitio";
            case GeckoSession.PermissionDelegate.PERMISSION_XR:
                return "usar funciones de realidad virtual";
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE:
                return "reproducir audio automáticamente";
            case GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE:
                return "reproducir contenido automáticamente";
            default:
                return "usar una función del dispositivo";
        }
    }

    private GeckoResult<Integer> requestContentSitePermission(
            GeckoSession.PermissionDelegate.ContentPermission permission, boolean privateMode) {
        // Persistent storage grants are intentionally blocked until GeckoView exposes a
        // reliable per-site revoke/reset path for this permission.
        if (permission.permission == GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE) {
            return GeckoResult.fromValue(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
        }

        String origin = getSiteOrigin(permission.uri);
        String type = "content_" + permission.permission;
        String savedDecision = privateMode ? null : readSitePermissionDecision(origin, type);
        if ("allow".equals(savedDecision)) {
            return GeckoResult.fromValue(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW);
        }
        if ("deny".equals(savedDecision)) {
            return GeckoResult.fromValue(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
        }
        if (permission.value != GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT) {
            return GeckoResult.fromValue(permission.value);
        }

        GeckoResult<Integer> result = new GeckoResult<>();
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
                return;
            }

            String site = getTabHost(permission.uri);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Permiso del sitio")
                    .setMessage(site + " solicita permiso para "
                            + permissionLabel(permission.permission) + ".")
                    .setPositiveButton("Permitir", (d, which) -> {
                        if (!privateMode) saveSitePermissionDecision(origin, type, "allow");
                        result.complete(
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW);
                    })
                    .setNegativeButton("Bloquear", (d, which) -> {
                        if (!privateMode) saveSitePermissionDecision(origin, type, "deny");
                        result.complete(
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
                    })
                    .create();
            dialog.setOnCancelListener(d ->
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY));
            dialog.setOnShowListener(d -> permission.notifyShown());
            dialog.show();
        });
        return result;
    }

    private void requestAndroidSitePermissions(String[] permissions,
            GeckoSession.PermissionDelegate.Callback callback) {
        if (permissions == null || permissions.length == 0) {
            callback.grant();
            return;
        }
        // Only pass permissions that AstroWeb declares in its manifest.
        // Unknown/new Gecko permissions are rejected safely instead of causing
        // an undeclared-permission SecurityException on older Android versions.
        for (String permission : permissions) {
            boolean supported = android.Manifest.permission.ACCESS_COARSE_LOCATION.equals(permission)
                    || android.Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)
                    || android.Manifest.permission.CAMERA.equals(permission)
                    || android.Manifest.permission.RECORD_AUDIO.equals(permission);
            if (!supported) {
                callback.reject();
                return;
            }
        }
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                callback.reject();
                return;
            }
            if (pendingAndroidPermissionCallback != null) {
                pendingAndroidPermissionCallback.reject();
            }
            pendingAndroidPermissionCallback = callback;
            try {
                requestPermissions(permissions, REQUEST_ANDROID_SITE_PERMISSION);
            } catch (RuntimeException error) {
                pendingAndroidPermissionCallback = null;
                callback.reject();
                Log.w(TAG, "Android site permission request failed", error);
            }
        });
    }

    private void requestMediaSitePermission(String uri,
            GeckoSession.PermissionDelegate.MediaSource[] video,
            GeckoSession.PermissionDelegate.MediaSource[] audio,
            GeckoSession.PermissionDelegate.MediaCallback callback, boolean privateMode) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                callback.reject();
                return;
            }

            boolean needsCamera = video != null && video.length > 0;
            boolean needsMicrophone = audio != null && audio.length > 0;
            if (!needsCamera && !needsMicrophone) {
                callback.reject();
                return;
            }
            if (needsCamera && checkSelfPermission(android.Manifest.permission.CAMERA)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                callback.reject();
                return;
            }
            if (needsMicrophone && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                callback.reject();
                return;
            }

            String origin = getSiteOrigin(uri);
            String cameraType = "media_camera";
            String microphoneType = "media_microphone";
            String cameraDecision = privateMode || !needsCamera ? null
                    : readSitePermissionDecision(origin, cameraType);
            String microphoneDecision = privateMode || !needsMicrophone ? null
                    : readSitePermissionDecision(origin, microphoneType);

            if ("deny".equals(cameraDecision) || "deny".equals(microphoneDecision)) {
                callback.reject();
                return;
            }

            boolean cameraAllowed = !needsCamera || "allow".equals(cameraDecision);
            boolean microphoneAllowed = !needsMicrophone || "allow".equals(microphoneDecision);
            if (cameraAllowed && microphoneAllowed) {
                GeckoSession.PermissionDelegate.MediaSource videoSource =
                        needsCamera ? video[0] : null;
                GeckoSession.PermissionDelegate.MediaSource audioSource =
                        needsMicrophone ? audio[0] : null;
                callback.grant(videoSource, audioSource);
                return;
            }

            String requested = needsCamera && needsMicrophone ? "cámara y micrófono"
                    : needsCamera ? "cámara" : "micrófono";
            String site = getTabHost(uri);
            GeckoSession.PermissionDelegate.MediaSource videoSource =
                    needsCamera ? video[0] : null;
            GeckoSession.PermissionDelegate.MediaSource audioSource =
                    needsMicrophone ? audio[0] : null;

            new AlertDialog.Builder(this)
                    .setTitle("Acceso al dispositivo")
                    .setMessage(site + " solicita usar tu " + requested + ".")
                    .setPositiveButton("Permitir", (d, which) -> {
                        if (!privateMode) {
                            if (needsCamera) {
                                saveSitePermissionDecision(origin, cameraType, "allow");
                            }
                            if (needsMicrophone) {
                                saveSitePermissionDecision(origin, microphoneType, "allow");
                            }
                        }
                        callback.grant(videoSource, audioSource);
                    })
                    .setNegativeButton("Bloquear", (d, which) -> {
                        if (!privateMode) {
                            if (needsCamera) {
                                saveSitePermissionDecision(origin, cameraType, "deny");
                            }
                            if (needsMicrophone) {
                                saveSitePermissionDecision(origin, microphoneType, "deny");
                            }
                        }
                        callback.reject();
                    })
                    .setOnCancelListener(d -> callback.reject())
                    .show();
        });
    }

    private String getSiteOrigin(String url) {
        if (url == null || url.isEmpty() || url.equals(HOME_URL)) return "";
        try {
            Uri parsed = Uri.parse(url);
            String scheme = parsed.getScheme();
            String host = parsed.getHost();
            if (scheme == null || host == null || host.isEmpty()) return "";
            String origin = scheme.toLowerCase(java.util.Locale.ROOT) + "://"
                    + host.toLowerCase(java.util.Locale.ROOT);
            if (parsed.getPort() != -1) origin += ":" + parsed.getPort();
            return origin;
        } catch (RuntimeException error) {
            return "";
        }
    }

    private String readSitePermissionDecision(String origin, String type) {
        if (origin == null || origin.isEmpty()) return null;
        try {
            JSONObject decisions = new JSONObject(storage.getString("site_permissions", "{}"));
            String value = decisions.optString(origin + "|" + type, "");
            return value.isEmpty() ? null : value;
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Could not read site permission settings", error);
            return null;
        }
    }

    private void saveSitePermissionDecision(String origin, String type, String decision) {
        if (origin == null || origin.isEmpty() || type == null || type.isEmpty()) return;
        try {
            JSONObject decisions = new JSONObject(storage.getString("site_permissions", "{}"));
            String key = origin + "|" + type;
            if (decision == null) decisions.remove(key);
            else decisions.put(key, decision);
            storage.edit().putString("site_permissions", decisions.toString()).apply();
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Could not save site permission settings", error);
        }
    }

    private String sitePermissionLabel(String type) {
        if ("media_camera".equals(type)) return "Cámara";
        if ("media_microphone".equals(type)) return "Micrófono";
        if (type != null && type.startsWith("content_")) {
            try {
                return permissionLabel(Integer.parseInt(type.substring("content_".length())));
            } catch (NumberFormatException ignored) { }
        }
        return "Permiso del sitio";
    }

    private void showSitePermissionsDialog() {
        String origin = getSiteOrigin(currentUrl());
        if (origin.isEmpty()) {
            Toast.makeText(this, "Abre un sitio web primero", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject decisions;
        try {
            decisions = new JSONObject(storage.getString("site_permissions", "{}"));
        } catch (JSONException error) {
            decisions = new JSONObject();
        }

        List<String> labels = new ArrayList<>();
        List<String> subtitles = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        JSONArray keys = decisions.names();
        if (keys != null) {
            String prefix = origin + "|";
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.optString(i, "");
                if (!key.startsWith(prefix)) continue;
                String type = key.substring(prefix.length());
                String decision = decisions.optString(key, "deny");
                labels.add(sitePermissionLabel(type));
                subtitles.add(("allow".equals(decision) ? "Permitido" : "Bloqueado")
                        + " · Toca para cambiar");
                icons.add(R.drawable.ic_private);
                actions.add(() -> showSitePermissionEditor(origin, type));
            }
        }

        if (labels.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permisos de " + getTabHost(currentUrl()))
                    .setMessage("Este sitio todavía no tiene permisos guardados. "
                            + "AstroWeb te preguntará cuando solicite acceso.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }

        showBottomSheet("Permisos · " + getTabHost(currentUrl()),
                labels, subtitles, icons, actions,
                null, null, "Restablecer permisos", () -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Restablecer permisos")
                            .setMessage("AstroWeb volverá a preguntar a este sitio cuando solicite acceso.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Restablecer", (dialog, which) -> {
                                clearSitePermissionDecisions(origin);
                                Toast.makeText(this, "Permisos restablecidos",
                                        Toast.LENGTH_SHORT).show();
                            })
                            .show();
                });
    }

    private void showSitePermissionEditor(String origin, String type) {
        String label = sitePermissionLabel(type);
        String current = readSitePermissionDecision(origin, type);
        int selected = "allow".equals(current) ? 0 : "deny".equals(current) ? 1 : 2;
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setSingleChoiceItems(
                        new String[]{"Permitir", "Bloquear", "Preguntar siempre"},
                        selected, (dialog, which) -> {
                            String decision = which == 0 ? "allow" : which == 1 ? "deny" : null;
                            saveSitePermissionDecision(origin, type, decision);
                            dialog.dismiss();
                            Toast.makeText(this, "Preferencia actualizada",
                                    Toast.LENGTH_SHORT).show();
                            showSitePermissionsDialog();
                        })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void clearSitePermissionDecisions(String origin) {
        try {
            JSONObject current = new JSONObject(storage.getString("site_permissions", "{}"));
            JSONObject remaining = new JSONObject();
            JSONArray keys = current.names();
            String prefix = origin + "|";
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.optString(i, "");
                    if (!key.startsWith(prefix)) remaining.put(key, current.get(key));
                }
            }
            storage.edit().putString("site_permissions", remaining.toString()).apply();
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Could not reset site permissions", error);
        }
    }

    private void handleExternalDownload(WebResponse response) {
        if (response == null || response.body == null) {
            Toast.makeText(this, "No se pudo obtener el archivo", Toast.LENGTH_SHORT).show();
            return;
        }
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                try { response.body.close(); } catch (IOException ignored) { }
                return;
            }
            if (pendingDownloadStream != null) {
                try { response.body.close(); } catch (IOException ignored) { }
                Toast.makeText(this, "Termina la descarga actual antes de iniciar otra",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            pendingDownloadStream = response.body;
            pendingDownloadName = suggestedDownloadName(response.uri);
            pendingDownloadMime = "application/octet-stream";
            for (Map.Entry<String, String> header : response.headers.entrySet()) {
                if (header.getKey() != null && header.getKey().equalsIgnoreCase("Content-Type")
                        && header.getValue() != null) {
                    pendingDownloadMime = header.getValue().split(";")[0].trim();
                    break;
                }
            }
            response.setReadTimeoutMillis(120000);
            launchCreateDocument(REQUEST_SAVE_DOWNLOAD, pendingDownloadMime, pendingDownloadName);
        });
    }

    private String suggestedDownloadName(String uriText) {
        try {
            String name = Uri.parse(uriText).getLastPathSegment();
            if (name != null && !name.trim().isEmpty()) return safeFileName(Uri.decode(name));
        } catch (RuntimeException ignored) { }
        return "archivo-" + System.currentTimeMillis();
    }

    private String safeFileName(String value) {
        String name = value == null ? "archivo" : value.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "archivo";
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private void launchCreateDocument(int requestCode, String mimeType, String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType == null || mimeType.isEmpty()
                ? "application/octet-stream" : mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName == null ? "archivo" : fileName);
        try {
            startActivityForResult(intent, requestCode);
        } catch (RuntimeException error) {
            if (requestCode == REQUEST_SAVE_DOWNLOAD && pendingDownloadStream != null) {
                try { pendingDownloadStream.close(); } catch (IOException ignored) { }
                pendingDownloadStream = null;
            }
            if (requestCode == REQUEST_SAVE_PDF && pendingPdfFile != null) {
                pendingPdfFile.delete();
                pendingPdfFile = null;
            }
            Toast.makeText(this, "No se pudo abrir el selector de archivos",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrentPageAsPdf() {
        if (activeTab == null || activeTab.session == null || currentUrl().equals(HOME_URL)) {
            Toast.makeText(this, "Abre una página antes de guardarla como PDF",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (pendingPdfFile != null) {
            Toast.makeText(this, "Termina de guardar el PDF actual primero",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        GeckoSession session = activeTab.session;
        String pageTitle = safeFileName(activeTab.title);
        Toast.makeText(this, "Preparando PDF…", Toast.LENGTH_SHORT).show();
        try {
            session.saveAsPdf().accept(pdfStream -> {
                if (pdfStream == null) {
                    Toast.makeText(this, "No se pudo generar el PDF", Toast.LENGTH_SHORT).show();
                    return;
                }
                ioExecutor.execute(() -> {
                    File temp = new File(getCacheDir(),
                            "astroweb-" + System.currentTimeMillis() + ".pdf");
                    boolean written = false;
                    try (InputStream input = pdfStream;
                         OutputStream output = new FileOutputStream(temp)) {
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        output.flush();
                        written = true;
                    } catch (IOException error) {
                        Log.e(TAG, "PDF generation failed", error);
                    }

                    final boolean ready = written;
                    runOnUiThread(() -> {
                        if (!ready || isFinishing() || isDestroyed()) {
                            temp.delete();
                            Toast.makeText(this, "No se pudo generar el PDF",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        pendingPdfFile = temp;
                        launchCreateDocument(REQUEST_SAVE_PDF, "application/pdf",
                                pageTitle + ".pdf");
                    });
                });
            }, error -> runOnUiThread(() ->
                    Toast.makeText(this, "No se pudo generar el PDF",
                            Toast.LENGTH_SHORT).show()));
        } catch (RuntimeException error) {
            Log.e(TAG, "PDF export could not start", error);
            Toast.makeText(this, "No se pudo iniciar la exportación",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void copyStreamToUri(InputStream input, Uri destination, String name, String mime) {
        ioExecutor.execute(() -> {
            boolean success = false;
            try (InputStream source = input;
                 OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("No output stream for destination");
                byte[] buffer = new byte[16384];
                int count;
                while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
                success = true;
            } catch (IOException error) {
                Log.e(TAG, "Download save failed", error);
            }
            if (success) recordDownload(destination, name, mime);
            final boolean saved = success;
            runOnUiThread(() -> Toast.makeText(this,
                    saved ? "Archivo guardado en Descargas" : "No se pudo guardar el archivo",
                    Toast.LENGTH_SHORT).show());
        });
    }

    private void copyFileToUri(File sourceFile, Uri destination, String name, String mime) {
        ioExecutor.execute(() -> {
            boolean success = false;
            try (InputStream input = new FileInputStream(sourceFile);
                 OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("No output stream for destination");
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
                success = true;
            } catch (IOException error) {
                Log.e(TAG, "PDF save failed", error);
            } finally {
                sourceFile.delete();
            }
            if (success) recordDownload(destination, name, mime);
            final boolean saved = success;
            runOnUiThread(() -> Toast.makeText(this,
                    saved ? "PDF guardado correctamente" : "No se pudo guardar el PDF",
                    Toast.LENGTH_SHORT).show());
        });
    }

    private void recordDownload(Uri uri, String name, String mime) {
        try {
            JSONArray entries = new JSONArray(storage.getString("downloads", "[]"));
            JSONObject item = new JSONObject();
            item.put("uri", uri.toString());
            item.put("name", name == null ? "archivo" : name);
            item.put("mime", mime == null ? "application/octet-stream" : mime);
            item.put("savedAt", System.currentTimeMillis());
            entries.put(item);
            while (entries.length() > 30) entries.remove(0);
            storage.edit().putString("downloads", entries.toString()).apply();
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Could not store download history", error);
        }
    }

    private void showDownloadsDialog() {
        JSONArray entries;
        try {
            entries = new JSONArray(storage.getString("downloads", "[]"));
        } catch (JSONException error) {
            entries = new JSONArray();
        }

        List<JSONObject> savedItems = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> subtitles = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (int i = entries.length() - 1; i >= 0; i--) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null || item.optString("uri").isEmpty()) continue;
            savedItems.add(item);
            labels.add(item.optString("name", "Archivo guardado"));
            String mime = item.optString("mime", "Archivo");
            long savedAt = item.optLong("savedAt", 0L);
            String when = savedAt > 0
                    ? android.text.format.DateFormat.format("dd MMM yyyy · HH:mm", savedAt).toString()
                    : "Fecha no disponible";
            subtitles.add(mime + " · " + when);
            icons.add(R.drawable.ic_history);
            actions.add(() -> showDownloadActions(item));
        }

        if (savedItems.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Descargas")
                    .setMessage("Todavía no hay archivos guardados.")
                    .setPositiveButton("Cerrar", null)
                    .show();
            return;
        }

        showBottomSheet("Descargas", labels, subtitles, icons, actions,
                null, null, "Borrar lista", () ->
                        new AlertDialog.Builder(this)
                                .setTitle("Borrar lista de descargas")
                                .setMessage("Solo se quitarán los registros de AstroWeb. "
                                        + "Los archivos guardados no se eliminarán.")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Borrar", (dialog, which) -> {
                                    storage.edit().remove("downloads").apply();
                                    Toast.makeText(this, "Lista de descargas borrada",
                                            Toast.LENGTH_SHORT).show();
                                })
                                .show());
    }

    private void showDownloadActions(JSONObject item) {
        String name = item.optString("name", "Archivo guardado");
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"Abrir archivo", "Quitar de la lista"}, (dialog, which) -> {
                    if (which == 0) {
                        openSavedDownload(item);
                    } else {
                        removeDownloadHistoryItem(item);
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void removeDownloadHistoryItem(JSONObject target) {
        try {
            JSONArray entries = new JSONArray(storage.getString("downloads", "[]"));
            JSONArray updated = new JSONArray();
            String targetUri = target.optString("uri", "");
            long targetTime = target.optLong("savedAt", -1L);
            boolean removed = false;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.optJSONObject(i);
                if (item == null) continue;
                boolean same = !removed
                        && targetUri.equals(item.optString("uri", ""))
                        && targetTime == item.optLong("savedAt", -2L);
                if (same) {
                    removed = true;
                } else {
                    updated.put(item);
                }
            }
            storage.edit().putString("downloads", updated.toString()).apply();
            Toast.makeText(this, removed ? "Quitado de la lista" : "Registro no encontrado",
                    Toast.LENGTH_SHORT).show();
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Could not remove download history entry", error);
            Toast.makeText(this, "No se pudo actualizar la lista",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openSavedDownload(JSONObject item) {
        try {
            Uri uri = Uri.parse(item.getString("uri"));
            String mime = item.optString("mime", "application/octet-stream");
            Intent open = new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(uri, mime);
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(open, "Abrir archivo"));
        } catch (Exception error) {
            Toast.makeText(this, "No hay una aplicación para abrir este archivo",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private BrowserTab createWindowTab(String requestedUrl) {
        if (tabs.size() >= 20) {
            Toast.makeText(this, "Llegaste al límite de 20 pestañas", Toast.LENGTH_SHORT).show();
            return null;
        }
        BrowserTab tab = new BrowserTab();
        tab.privateMode = activeTab != null && activeTab.privateMode;
        tab.url = requestedUrl == null || requestedUrl.isEmpty() ? HOME_URL : requestedUrl;
        tabs.add(tab);
        // GeckoView opens this session itself after onNewSession returns it.
        initializeTab(tab, tab.url, false, false, false);
        updateTabCounter();
        if (tab.session == null) {
            tabs.remove(tab);
            updateTabCounter();
            return null;
        }
        persistTabsSnapshot();
        activateWindowTabWhenReady(tab, 0);
        return tab;
    }

    private void activateWindowTabWhenReady(BrowserTab tab, int attempt) {
        handler.postDelayed(() -> {
            if (isFinishing() || !tabs.contains(tab) || tab.session == null) return;
            if (tab.session.isOpen()) {
                activateTab(tab);
            } else if (attempt < 60) {
                activateWindowTabWhenReady(tab, attempt + 1);
            } else {
                // Keep the tab instead of silently deleting it. It may still be
                // opening while Gecko is under startup or memory pressure.
                Log.w(TAG, "New window session is taking longer than expected");
            }
        }, 80);
    }

    private void activateTab(BrowserTab tab) {
        if (tab == null) return;
        if (tab.session == null || !tab.session.isOpen()) {
            if (tab.session != null) tab.session = null;
            initializeTab(tab, tab.url, true);
            return;
        }
        if (activeTab != null && activeTab != tab && activeTab.session != null) {
            captureTabPreview(activeTab, null);
            try {
                activeTab.session.flushSessionState();
                activeTab.session.setFocused(false);
                activeTab.session.setActive(false);
                activeTab.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
            } catch (RuntimeException ignored) { }
        }
        activeTab = tab;
        persistTabsSnapshot();
        lastScrollY = 0;
        scrollAccumulator = 0f;
        scrollDirection = 0;
        ignoreNextScrollUpdate = true;
        if (tab.url.equals(HOME_URL)) animateAddressBar(true);
        geckoView.setSession(tab.session);
        try {
            tab.session.setActive(true);
            tab.session.setFocused(true);
            tab.session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        } catch (RuntimeException ignored) { }
        addressField.setText(tab.url.equals(HOME_URL) ? "" : tab.url);
        addressField.setSelection(addressField.getText().length());
        failureOverlay.setVisibility(View.GONE);
        updateNavigationButtons();
        updateBookmarkButton();
        updateTabCounter();
    }

    private void restartTab(BrowserTab tab, String target) {
        if (tab == null) {
            createTab(HOME_URL);
            return;
        }
        GeckoSession old = tab.session;
        tab.session = null;
        if (old != null) {
            try { old.close(); }
            catch (RuntimeException error) { Log.w(TAG, "Session close failed", error); }
        }
        String nextUrl = target == null || target.isEmpty() ? HOME_URL : target;
        if (!nextUrl.equals(tab.url)) tab.serializedState = null;
        tab.url = nextUrl;
        if (tab == activeTab) failureOverlay.setVisibility(View.GONE);
        initializeTab(tab, tab.url, tab == activeTab);
    }

    private void handleEngineStopped(BrowserTab tab, GeckoSession stoppedSession) {
        runOnUiThread(() -> {
            if (isFinishing() || tab.session != stoppedSession || tab.recoveryScheduled) return;
            tab.recoveryScheduled = true;
            if (tab.recoveryAttempts < 1) {
                tab.recoveryAttempts++;
                handler.postDelayed(() -> {
                    tab.recoveryScheduled = false;
                    if (!isFinishing() && tabs.contains(tab) && tab.session == stoppedSession) {
                        restartTab(tab, tab.url);
                    }
                }, 850);
            } else {
                tab.recoveryScheduled = false;
                if (tab == activeTab) {
                    showFailure("El motor del navegador se detuvo",
                            "Reinicia esta pestaña para continuar.");
                }
            }
        });
    }

    private void navigate(String raw) {
        if (activeTab == null || activeTab.session == null || raw == null) return;
        String input = raw.trim();
        if (input.isEmpty()) return;
        dismissAddressSuggestions();

        String target = input;
        boolean hasScheme = input.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
        if (!hasScheme) {
            boolean looksLikeHost = input.matches("(?i)^[a-z0-9-]+(\\.[a-z0-9-]+)+(:[0-9]+)?(/.*)?$");
            if (input.contains(" ") || !looksLikeHost) {
                target = "https://www.google.com/search?q=" + Uri.encode(input);
            } else {
                target = "https://" + input;
            }
        }

        if (!target.equals(activeTab.url)) activeTab.serializedState = null;
        activeTab.url = target;
        persistTabsSnapshot();
        failureOverlay.setVisibility(View.GONE);
        pageProgress.setProgress(0);
        pageProgress.setVisibility(View.VISIBLE);
        hideKeyboard();
        try {
            activeTab.session.loadUri(target);
        } catch (RuntimeException error) {
            Log.e(TAG, "Navigation failed", error);
            pageProgress.setVisibility(View.INVISIBLE);
            showFailure("No se pudo abrir la dirección",
                    "Reinicia la pestaña e inténtalo de nuevo.");
        }
    }

    private void loadHome() {
        lastScrollY = 0;
        scrollAccumulator = 0f;
        scrollDirection = 0;
        animateAddressBar(true);
        if (activeTab == null) {
            createTab(HOME_URL);
            return;
        }
        if (activeTab.session == null) {
            restartTab(activeTab, HOME_URL);
            return;
        }
        activeTab.url = HOME_URL;
        activeTab.serializedState = null;
        persistTabsSnapshot();
        addressField.setText("");
        pageProgress.setVisibility(View.INVISIBLE);
        failureOverlay.setVisibility(View.GONE);
        activeTab.session.loadUri(HOME_URL);
    }

    private void reloadPage() {
        if (activeTab == null || activeTab.session == null) return;
        try {
            failureOverlay.setVisibility(View.GONE);
            pageProgress.setProgress(0);
            pageProgress.setVisibility(View.VISIBLE);
            activeTab.session.reload();
        } catch (RuntimeException error) {
            restartTab(activeTab, activeTab.url);
        }
    }

    private void goBack() {
        if (activeTab != null && activeTab.session != null && activeTab.canGoBack) {
            try { activeTab.session.goBack(); }
            catch (RuntimeException error) { Log.w(TAG, "Back failed", error); }
        }
    }

    private void goForward() {
        if (activeTab != null && activeTab.session != null && activeTab.canGoForward) {
            try { activeTab.session.goForward(); }
            catch (RuntimeException error) { Log.w(TAG, "Forward failed", error); }
        }
    }

    private void showTabsDialog() {
        captureTabPreview(activeTab, this::showTabsOverview);
    }

    private void captureTabPreview(BrowserTab tab, Runnable afterCapture) {
        if (tab == null || tab.session == null || geckoView == null) {
            if (afterCapture != null) afterCapture.run();
            return;
        }

        try {
            // Capture the document from its origin, not the currently scrolled viewport.
            // GeckoView 157+ supports this API; AstroWeb currently uses GeckoView 160.
            geckoView.captureFullPage().accept(bitmap -> {
                Bitmap topPreview = createTopPageTabPreview(bitmap);
                if (topPreview != null) {
                    storeTabPreview(tab, topPreview);
                    if (afterCapture != null) afterCapture.run();
                } else {
                    captureVisibleTabPreview(tab, afterCapture);
                }
            }, error -> {
                Log.w(TAG, "Full page tab preview unavailable; using visible viewport", error);
                captureVisibleTabPreview(tab, afterCapture);
            });
        } catch (RuntimeException error) {
            Log.w(TAG, "Full page tab preview failed; using visible viewport", error);
            captureVisibleTabPreview(tab, afterCapture);
        }
    }

    private Bitmap createTopPageTabPreview(Bitmap fullPage) {
        if (fullPage == null || fullPage.isRecycled()) return null;
        try {
            int screenWidthDp = Math.max(1, getResources().getConfiguration().screenWidthDp);
            int columns = screenWidthDp >= 960 ? 4 : (screenWidthDp >= 650 ? 3 : 2);
            float cardWidthDp = Math.max(1f,
                    (screenWidthDp - 32f - 14f * (columns - 1)) / columns);
            float targetAspect = cardWidthDp / 210f;

            int sourceWidth = fullPage.getWidth();
            int sourceHeight = fullPage.getHeight();
            if (sourceWidth <= 0 || sourceHeight <= 0) return null;

            // Crop from y=0, keeping the top of the page visible in the tab card.
            int cropHeight = Math.max(1, Math.min(sourceHeight,
                    Math.round(sourceWidth / targetAspect)));
            int outputWidth = Math.max(1, Math.min(dp(360), sourceWidth));
            int outputHeight = Math.max(1, Math.round(outputWidth / targetAspect));

            Bitmap preview = Bitmap.createBitmap(outputWidth, outputHeight,
                    Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(preview);
            canvas.drawColor(Color.rgb(22, 28, 24));
            android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.FILTER_BITMAP_FLAG
                            | android.graphics.Paint.DITHER_FLAG);

            float contentHeight = Math.min(outputHeight,
                    outputWidth * (float) cropHeight / sourceWidth);
            android.graphics.Rect sourceRect =
                    new android.graphics.Rect(0, 0, sourceWidth, cropHeight);
            android.graphics.Rect destinationRect = new android.graphics.Rect(
                    0, 0, outputWidth, Math.max(1, Math.round(contentHeight)));
            canvas.drawBitmap(fullPage, sourceRect, destinationRect, paint);
            return preview;
        } catch (RuntimeException | OutOfMemoryError error) {
            Log.w(TAG, "Could not crop full page for tab preview", error);
            return null;
        } finally {
            if (!fullPage.isRecycled()) fullPage.recycle();
        }
    }

    private void captureVisibleTabPreview(BrowserTab tab, Runnable afterCapture) {
        if (tab == null || tab.session == null || geckoView == null) {
            if (afterCapture != null) afterCapture.run();
            return;
        }
        try {
            geckoView.capturePixels().accept(bitmap -> {
                Bitmap scaled = scaleVisibleTabPreview(bitmap);
                if (scaled != null) storeTabPreview(tab, scaled);
                if (afterCapture != null) afterCapture.run();
            }, error -> {
                Log.w(TAG, "Visible tab preview unavailable", error);
                if (afterCapture != null) afterCapture.run();
            });
        } catch (RuntimeException error) {
            Log.w(TAG, "Visible tab preview failed", error);
            if (afterCapture != null) afterCapture.run();
        }
    }

    private Bitmap scaleVisibleTabPreview(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        try {
            int maxWidth = dp(360);
            float scale = Math.min(1f, (float) maxWidth / Math.max(1, bitmap.getWidth()));
            int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
            int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
            // createScaledBitmap may return the original instance when dimensions match.
            if (scaled != bitmap && !bitmap.isRecycled()) bitmap.recycle();
            return scaled;
        } catch (RuntimeException | OutOfMemoryError error) {
            Log.w(TAG, "Could not scale visible tab preview", error);
            if (!bitmap.isRecycled()) bitmap.recycle();
            return null;
        }
    }

    private void storeTabPreview(BrowserTab tab, Bitmap preview) {
        if (preview == null || preview.isRecycled()) return;
        if (tab == null || !tabs.contains(tab)) {
            preview.recycle();
            return;
        }
        if (tab.thumbnail != null && tab.thumbnail != preview && !tab.thumbnail.isRecycled()) {
            tab.thumbnail.recycle();
        }
        tab.thumbnail = preview;
    }

    private void showTabsOverview() {
        showTabsOverview(null);
    }

    private void showTabsOverview(Dialog existingDialog) {
        Dialog dialog = existingDialog == null ? new Dialog(this) : existingDialog;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setPadding(dp(16), dp(18), dp(16), dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout headingGroup = new LinearLayout(this);
        headingGroup.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("Pestañas");
        heading.setTextColor(TEXT);
        heading.setTextSize(23);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headingGroup.addView(heading);

        TextView count = new TextView(this);
        count.setText(tabs.size() + (tabs.size() == 1 ? " ventana abierta" : " ventanas abiertas"));
        count.setTextColor(MUTED);
        count.setTextSize(12);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, -2);
        countParams.topMargin = dp(2);
        headingGroup.addView(count, countParams);
        header.addView(headingGroup, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView addTab = overviewHeaderAction("+", "Nueva pestaña");
        addTab.setOnClickListener(v -> {
            dialog.dismiss();
            createTab(HOME_URL);
        });
        header.addView(addTab, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView done = overviewHeaderAction("×", "Cerrar vista de pestañas");
        done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        doneParams.leftMargin = dp(6);
        header.addView(done, doneParams);

        page.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(12), 0, dp(18));

        int screenWidth = getResources().getConfiguration().screenWidthDp;
        final int columns = screenWidth >= 960 ? 4 : (screenWidth >= 650 ? 3 : 2);
        for (int i = 0; i < tabs.size(); i += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < columns; c++) {
                int index = i + c;
                if (index < tabs.size()) {
                    BrowserTab tab = tabs.get(index);
                    View card = createTabCard(tab, dialog);
                    LinearLayout.LayoutParams cardParams =
                            new LinearLayout.LayoutParams(0, -2, 1f);
                    if (c > 0) cardParams.leftMargin = dp(7);
                    if (c < columns - 1) cardParams.rightMargin = dp(7);
                    row.addView(card, cardParams);
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(14);
            rows.addView(row, rowParams);
        }

        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView newTabButton = sheetAction("＋   Nueva pestaña", true);
        newTabButton.setOnClickListener(v -> {
            dialog.dismiss();
            createTab(HOME_URL);
        });
        LinearLayout.LayoutParams newButtonParams = new LinearLayout.LayoutParams(-1, dp(48));
        newButtonParams.topMargin = dp(8);
        page.addView(newTabButton, newButtonParams);

        if (existingDialog == null) {
            page.setAlpha(0f);
            page.setTranslationY(dp(8));
        } else {
            page.setAlpha(1f);
            page.setTranslationY(0f);
        }
        dialog.setContentView(page);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.CENTER;
            params.dimAmount = 0.32f;
            window.setAttributes(params);
        }
        if (existingDialog == null) dialog.show();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        if (existingDialog == null) {
            page.animate().alpha(1f).translationY(0f).setDuration(210)
                    .setInterpolator(new DecelerateInterpolator(1.3f)).start();
        }
    }

    private TextView overviewHeaderAction(String label, String description) {
        TextView action = new TextView(this);
        action.setText(label);
        action.setTextSize(label.equals("+") ? 26 : 25);
        action.setTextColor(TEXT);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription(description);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(35, 43, 36));
        background.setCornerRadius(dp(14));
        action.setBackground(background);
        applySoftRipple(action);
        return action;
    }

    private View createTabCard(BrowserTab tab, Dialog overviewDialog) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipToOutline(true);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.rgb(29, 36, 31));
        cardBackground.setCornerRadius(dp(15));
        cardBackground.setStroke(dp(1), tab == activeTab
                ? Color.rgb(117, 151, 119) : Color.rgb(54, 65, 56));
        card.setBackground(cardBackground);
        applySoftRipple(card);

        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackgroundColor(Color.rgb(22, 28, 24));
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (tab.thumbnail != null && !tab.thumbnail.isRecycled()) {
            preview.setImageBitmap(tab.thumbnail);
        } else {
            TextView placeholder = new TextView(this);
            String host = getTabHost(tab.url);
            placeholder.setText(host);
            placeholder.setTextColor(Color.rgb(174, 190, 173));
            placeholder.setTextSize(14);
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setBackgroundColor(Color.rgb(25, 33, 27));
            previewFrame.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        }
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        if (tab == activeTab) {
            TextView activeTag = new TextView(this);
            activeTag.setText("ACTIVA");
            activeTag.setTextSize(8);
            activeTag.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            activeTag.setTextColor(Color.rgb(22, 31, 23));
            activeTag.setGravity(Gravity.CENTER);
            activeTag.setPadding(dp(7), dp(4), dp(7), dp(4));
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setColor(ACCENT);
            tagBg.setCornerRadius(dp(8));
            activeTag.setBackground(tagBg);
            FrameLayout.LayoutParams tagParams = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.BOTTOM | Gravity.START);
            tagParams.leftMargin = dp(8);
            tagParams.bottomMargin = dp(8);
            previewFrame.addView(activeTag, tagParams);
        }

        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_close);
        close.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.rgb(25, 29, 26));
        closeBg.setCornerRadius(dp(12));
        close.setBackground(closeBg);
        close.setContentDescription("Cerrar pestaña");
        close.setClickable(true);
        close.setFocusable(true);
        close.setElevation(dp(14));
        applySoftRipple(close);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(36), dp(36),
                Gravity.TOP | Gravity.END);
        closeParams.topMargin = dp(7);
        closeParams.rightMargin = dp(7);
        close.setOnClickListener(v -> closeTabFromOverview(tab, overviewDialog));
        previewFrame.addView(close, closeParams);
        close.bringToFront();

        card.addView(previewFrame, new LinearLayout.LayoutParams(-1, dp(210)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(10), dp(9), dp(10), dp(10));

        TextView title = new TextView(this);
        title.setText(tab.title == null || tab.title.trim().isEmpty()
                ? "Nueva pestaña" : shorten(tab.title, 31));
        title.setTextColor(TEXT);
        title.setTextSize(12);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(title);

        TextView url = new TextView(this);
        url.setText(getTabHost(tab.url));
        url.setTextColor(MUTED);
        url.setTextSize(10);
        url.setSingleLine(true);
        url.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, -2);
        urlParams.topMargin = dp(3);
        details.addView(url, urlParams);

        if (tab.privateMode) {
            TextView privateBadge = new TextView(this);
            privateBadge.setText("Pestaña privada");
            privateBadge.setTextSize(9);
            privateBadge.setTextColor(ACCENT);
            privateBadge.setPadding(0, dp(5), 0, 0);
            details.addView(privateBadge);
        }
        card.addView(details);
        card.setOnClickListener(v -> {
            overviewDialog.dismiss();
            activateTab(tab);
        });
        return card;
    }

    private String getTabHost(String url) {
        if (url == null || url.isEmpty() || url.equals(HOME_URL)) return "Nueva pestaña";
        try {
            Uri parsed = Uri.parse(url);
            String host = parsed.getHost();
            return host == null ? shorten(url, 35) : host.replaceFirst("^www\\.", "");
        } catch (RuntimeException error) {
            return shorten(url, 35);
        }
    }

    private void closeTabFromOverview(BrowserTab tab, Dialog overviewDialog) {
        if (tab == null || !tabs.contains(tab)) return;

        if (tabs.size() == 1) {
            tabs.remove(tab);
            GeckoSession lastSession = tab.session;
            tab.session = null;
            recycleTabThumbnail(tab);
            if (lastSession != null) {
                try {
                    lastSession.close();
                } catch (RuntimeException error) {
                    Log.w(TAG, "Last tab close failed", error);
                }
            }
            activeTab = null;
            clearSavedTabsAndExit();
            overviewDialog.dismiss();
            return;
        }

        int oldIndex = tabs.indexOf(tab);
        boolean wasActive = tab == activeTab;
        tabs.remove(tab);

        GeckoSession old = tab.session;
        tab.session = null;
        recycleTabThumbnail(tab);
        if (old != null) {
            try {
                old.close();
            } catch (RuntimeException error) {
                Log.w(TAG, "Tab close failed", error);
            }
        }

        if (wasActive) {
            activeTab = null;
            int nextIndex = Math.min(oldIndex, tabs.size() - 1);
            activateTab(tabs.get(Math.max(0, nextIndex)));
        }

        updateTabCounter();
        persistTabsSnapshot();

        // Keep the same Dialog/window alive. Replacing its content avoids the
        // close-fade/reopen-fade flash that appeared after every tab deletion.
        showTabsOverview(overviewDialog);
    }

    private void clearSavedTabsAndExit() {
        if (storage != null) {
            storage.edit()
                    .remove("tabs_snapshot")
                    .remove("last_url")
                    .remove("session_state")
                    .apply();
        }
        updateTabCounter();
        finish();
    }


        private void recycleTabThumbnail(BrowserTab tab) {
        if (tab == null || tab.thumbnail == null) return;
        if (!tab.thumbnail.isRecycled()) tab.thumbnail.recycle();
        tab.thumbnail = null;
    }

    private String shorten(String value, int max) {
        if (value == null) return "Nueva pestaña";
        if (value.length() <= max) return value;
        return value.substring(0, max - 1) + "…";
    }

    private void closeActiveTab() {
        if (activeTab == null) return;

        if (tabs.size() <= 1) {
            BrowserTab last = activeTab;
            tabs.remove(last);
            activeTab = null;
            GeckoSession old = last.session;
            last.session = null;
            recycleTabThumbnail(last);
            if (old != null) {
                try { old.close(); }
                catch (RuntimeException error) { Log.w(TAG, "Last tab close failed", error); }
            }
            clearSavedTabsAndExit();
            return;
        }

        int oldIndex = tabs.indexOf(activeTab);
        BrowserTab closing = activeTab;
        tabs.remove(closing);
        GeckoSession old = closing.session;
        closing.session = null;
        recycleTabThumbnail(closing);
        if (old != null) {
            try { old.close(); }
            catch (RuntimeException error) { Log.w(TAG, "Tab close failed", error); }
        }
        activeTab = null;
        BrowserTab next = tabs.get(Math.max(0, Math.min(oldIndex - 1, tabs.size() - 1)));
        activateTab(next);
        updateTabCounter();
        persistTabsSnapshot();
    }


        private void showBrowserMenu(View anchor) {
        List<String> labels = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        addTool(labels, icons, actions, "Nueva pestaña", R.drawable.ic_add, () -> createTab(HOME_URL));
        addTool(labels, icons, actions, "Pestaña privada", R.drawable.ic_private, this::createPrivateTab);
        addTool(labels, icons, actions, "Buscar en página", R.drawable.ic_search, this::showFindInPage);
        addTool(labels, icons, actions, "Modo escritorio", R.drawable.ic_desktop, this::toggleDesktopMode);
        addTool(labels, icons, actions, "Atrás", R.drawable.ic_back, this::goBack);
        addTool(labels, icons, actions, "Adelante", R.drawable.ic_forward, this::goForward);
        addTool(labels, icons, actions, "Recargar", R.drawable.ic_reload, this::reloadPage);
        addTool(labels, icons, actions, "Marcadores", R.drawable.ic_bookmark, this::showBookmarksDialog);
        addTool(labels, icons, actions, "Historial", R.drawable.ic_history, this::showHistoryDialog);
        addTool(labels, icons, actions, "Compartir", R.drawable.ic_share, this::shareCurrentPage);
        addTool(labels, icons, actions, "Copiar enlace", R.drawable.ic_copy, this::copyCurrentAddress);
        addTool(labels, icons, actions, "Abrir fuera", R.drawable.ic_external, this::openInExternalBrowser);
        addTool(labels, icons, actions, "Guardar PDF", R.drawable.ic_copy, this::saveCurrentPageAsPdf);
        addTool(labels, icons, actions, "Descargas", R.drawable.ic_history, this::showDownloadsDialog);
        addTool(labels, icons, actions, "Permisos del sitio", R.drawable.ic_private, this::showSitePermissionsDialog);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(13), dp(10), dp(13), dp(12));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.rgb(31, 38, 32));
        panelBg.setCornerRadius(dp(18));
        panelBg.setStroke(dp(1), Color.rgb(58, 70, 59));
        panel.setBackground(panelBg);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Herramientas");
        title.setTextColor(TEXT);
        title.setTextSize(14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(28), 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextColor(MUTED);
        close.setTextSize(21);
        close.setGravity(Gravity.CENTER);
        heading.addView(close, new LinearLayout.LayoutParams(dp(30), dp(28)));
        panel.addView(heading);

        final PopupWindow[] popupHolder = new PopupWindow[1];
        LinearLayout row = null;
        for (int i = 0; i < labels.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rowParams =
                        new LinearLayout.LayoutParams(-1, dp(58));
                rowParams.topMargin = dp(2);
                panel.addView(row, rowParams);
            }

            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(3), dp(4), dp(3), dp(4));
            applySoftRipple(item);

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons.get(i));
            icon.setImageTintList(ColorStateList.valueOf(
                    labels.get(i).equals("Atrás") && (activeTab == null || !activeTab.canGoBack)
                    || labels.get(i).equals("Adelante") && (activeTab == null || !activeTab.canGoForward)
                            ? Color.rgb(99, 109, 100) : ACCENT));
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            item.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));

            TextView label = new TextView(this);
            label.setText(labels.get(i));
            label.setTextColor(TEXT);
            label.setTextSize(9);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(3);
            item.addView(label, labelParams);

            row.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
            item.setOnClickListener(v -> {
                if (popupHolder[0] != null) popupHolder[0].dismiss();
                actions.get(index).run();
            });
        }

        PopupWindow popup = new PopupWindow(panel,
                Math.min(dp(330), getResources().getDisplayMetrics().widthPixels - dp(24)),
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        popupHolder[0] = popup;
        panel.setAlpha(0f);
        panel.setTranslationY(dp(10));
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(18));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        popup.setOnDismissListener(() -> { });
        close.setOnClickListener(v -> popup.dismiss());

        popup.showAtLocation(anchor, Gravity.BOTTOM | Gravity.END, dp(12), dp(62));
        panel.animate().alpha(1f).translationY(0f).setDuration(170)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
    }

    private void addTool(List<String> labels, List<Integer> icons,
            List<Runnable> actions, String label, int icon, Runnable action) {
        labels.add(label);
        icons.add(icon);
        actions.add(action);
    }

    private void showBottomSheet(String title, List<String> labels, List<String> subtitles,
            List<Integer> icons, List<Runnable> actions,
            String primaryLabel, Runnable primaryAction,
            String secondaryLabel, Runnable secondaryAction) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(12), dp(20), dp(18));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.rgb(31, 38, 32));
        surface.setCornerRadii(new float[]{dp(22),dp(22),dp(22),dp(22),dp(0),dp(0),dp(0),dp(0)});
        panel.setBackground(surface);

        View handle = new View(this);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(Color.rgb(112, 125, 111));
        handleBg.setCornerRadius(dp(4));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(34), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(15);
        panel.addView(handle, handleParams);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(TEXT);
        heading.setTextSize(18);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(dp(2), dp(3), dp(2), dp(13));
        panel.addView(heading);

        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            boolean selected = labels.get(i).startsWith("✓");
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(9), dp(5), dp(9), dp(5));
            if (selected) {
                GradientDrawable selectedBg = new GradientDrawable();
                selectedBg.setColor(Color.rgb(43, 57, 45));
                selectedBg.setCornerRadius(dp(12));
                item.setBackground(selectedBg);
            }
            applySoftRipple(item);

            ImageView itemIcon = new ImageView(this);
            itemIcon.setImageResource(icons.get(index));
            itemIcon.setImageTintList(ColorStateList.valueOf(selected ? ACCENT : MUTED));
            itemIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(dp(24), dp(24));
            iconParams.rightMargin = dp(13);
            item.addView(itemIcon, iconParams);

            LinearLayout textColumn = new LinearLayout(this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setGravity(Gravity.CENTER_VERTICAL);

            TextView itemTitle = new TextView(this);
            itemTitle.setText(labels.get(i));
            itemTitle.setTextColor(selected ? ACCENT : TEXT);
            itemTitle.setTextSize(14);
            itemTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            textColumn.addView(itemTitle);

            String sub = subtitles != null && i < subtitles.size() ? subtitles.get(i) : "";
            if (sub != null && !sub.isEmpty()) {
                TextView itemSubtitle = new TextView(this);
                itemSubtitle.setText(sub);
                itemSubtitle.setTextColor(MUTED);
                itemSubtitle.setTextSize(11);
                itemSubtitle.setSingleLine(true);
                itemSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams subtitleParams =
                        new LinearLayout.LayoutParams(-1, -2);
                subtitleParams.topMargin = dp(2);
                textColumn.addView(itemSubtitle, subtitleParams);
            }
            item.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1f));
            item.setMinimumHeight(dp((sub == null || sub.isEmpty()) ? 48 : 60));
            item.setOnClickListener(v -> dismissSheet(dialog, panel, actions.get(index)));
            panel.addView(item, new LinearLayout.LayoutParams(-1, dp(
                    (sub == null || sub.isEmpty()) ? 48 : 60)));

            if (i < labels.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(47, 56, 48));
                panel.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }

        if (primaryLabel != null && primaryAction != null) {
            TextView primary = sheetAction(primaryLabel, true);
            primary.setOnClickListener(v -> dismissSheet(dialog, panel, primaryAction));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
            p.topMargin = dp(12);
            panel.addView(primary, p);
        }
        if (secondaryLabel != null && secondaryAction != null) {
            TextView secondary = sheetAction(secondaryLabel, false);
            secondary.setOnClickListener(v -> dismissSheet(dialog, panel, secondaryAction));
            panel.addView(secondary, new LinearLayout.LayoutParams(-1, dp(48)));
        }

        panel.setAlpha(0f);
        panel.setTranslationY(dp(26));
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM;
            params.dimAmount = 0.38f;
            params.windowAnimations = android.R.style.Animation_Dialog;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        panel.animate().translationY(0f).alpha(1f).setDuration(250)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
    }

    private void dismissSheet(Dialog dialog, View panel, Runnable action) {
        panel.animate().translationY(dp(18)).alpha(0f).setDuration(150)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    dialog.dismiss();
                    if (action != null) action.run();
                }).start();
    }

    private TextView sheetAction(String label, boolean primary) {
        TextView action = new TextView(this);
        action.setText(label);
        action.setTextSize(14);
        action.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        action.setGravity(Gravity.CENTER);
        action.setTextColor(primary ? Color.rgb(25, 32, 26) : TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(primary ? ACCENT : Color.rgb(49, 58, 50));
        action.setBackground(bg);
        applySoftRipple(action);
        return action;
    }

    private void toggleBookmark() {
        String url = currentUrl();
        if (url.equals(HOME_URL)) {
            Toast.makeText(this, "Abre una página para guardarla", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> saved = new HashSet<>(storage.getStringSet("bookmarks", new HashSet<>()));
        if (saved.contains(url)) {
            saved.remove(url);
            Toast.makeText(this, "Marcador eliminado", Toast.LENGTH_SHORT).show();
        } else {
            saved.add(url);
            Toast.makeText(this, "Marcador guardado", Toast.LENGTH_SHORT).show();
        }
        storage.edit().putStringSet("bookmarks", saved).apply();
        updateBookmarkButton();
    }

    private void updateBookmarkButton() {
        if (bookmarkButton == null) return;
        boolean saved = !currentUrl().equals(HOME_URL)
                && storage.getStringSet("bookmarks", new HashSet<>()).contains(currentUrl());
        bookmarkButton.setImageTintList(ColorStateList.valueOf(saved ? ACCENT : TEXT));
    }

    private void showBookmarksDialog() {
        List<String> saved = new ArrayList<>(
                new HashSet<>(storage.getStringSet("bookmarks", new HashSet<>())));
        Collections.sort(saved);
        if (saved.isEmpty()) {
            Toast.makeText(this, "Todavía no tienes marcadores", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        List<String> subtitles = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (String url : saved) {
            labels.add(shorten(url, 38));
            subtitles.add(url);
            icons.add(R.drawable.ic_bookmark);
            actions.add(() -> navigate(url));
        }
        showBottomSheet("Marcadores", labels, subtitles, icons, actions,
                null, null, null, null);
    }

    private void addHistory(String url) {
        if (url == null || url.isEmpty() || url.equals(HOME_URL)) return;
        String existing = storage.getString("history", "");
        List<String> entries = new ArrayList<>();
        if (!existing.isEmpty()) {
            for (String item : existing.split("\n")) {
                if (!item.isEmpty() && !item.equals(url)) entries.add(item);
            }
        }
        entries.add(0, url);
        while (entries.size() > 50) entries.remove(entries.size() - 1);
        storage.edit().putString("history", String.join("\n", entries)).apply();
    }

    private void showHistoryDialog() {
        String existing = storage.getString("history", "");
        if (existing.isEmpty()) {
            Toast.makeText(this, "El historial está vacío", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> entries = new ArrayList<>();
        for (String item : existing.split("\\n")) {
            if (!item.isEmpty()) entries.add(item);
        }
        List<String> labels = new ArrayList<>();
        List<String> subtitles = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (String url : entries) {
            labels.add(shorten(url, 38));
            subtitles.add(url);
            icons.add(R.drawable.ic_history);
            actions.add(() -> navigate(url));
        }
        showBottomSheet("Historial", labels, subtitles, icons, actions,
                null, null, "Borrar historial", () -> {
                    storage.edit().remove("history").commit();
                    Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show();
                });
    }

    private String currentUrl() {
        return activeTab == null || activeTab.url == null ? HOME_URL : activeTab.url;
    }

    private void shareCurrentPage() {
        String url = currentUrl();
        if (url.equals(HOME_URL)) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(share, "Compartir página"));
    }

    private void copyCurrentAddress() {
        String url = currentUrl();
        if (url.equals(HOME_URL)) return;
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Dirección web", url));
            Toast.makeText(this, "Dirección copiada", Toast.LENGTH_SHORT).show();
        }
    }

    private void openInExternalBrowser() {
        String url = currentUrl();
        if (url.equals(HOME_URL)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, "No se encontró otro navegador", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFailure(String title, String message) {
        if (failureOverlay == null) return;
        LinearLayout content = (LinearLayout) failureOverlay.getChildAt(0);
        ((TextView) content.getChildAt(0)).setText(title);
        ((TextView) content.getChildAt(1)).setText(message);
        failureOverlay.setVisibility(View.VISIBLE);
    }

    private void updateNavigationButtons() {
        if (backButton == null || forwardButton == null) return;
        boolean back = activeTab != null && activeTab.canGoBack;
        boolean forward = activeTab != null && activeTab.canGoForward;
        backButton.setEnabled(back);
        backButton.setAlpha(back ? 1f : 0.35f);
        forwardButton.setEnabled(forward);
        forwardButton.setAlpha(forward ? 1f : 0.35f);
    }

    private void updateTabCounter() {
        if (tabCounter != null) tabCounter.setText(String.valueOf(Math.max(1, tabs.size())));
    }

    private void saveSessionState(BrowserTab tab, GeckoSession.SessionState state) {
        if (tab == null || state == null || tab.privateMode || !tabs.contains(tab)) return;
        try {
            String serialized = state.toString();
            if (serialized == null || serialized.isEmpty()) return;
            tab.serializedState = serialized;
            persistTabsSnapshot();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not persist browser session", error);
        }
    }

    private void setBrowserContentTopOffset(boolean toolbarVisible) {
        if (browserFrame == null) return;
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) browserFrame.getLayoutParams();
        int target = toolbarVisible ? dp(55) : 0;
        if (params.topMargin != target) {
            params.topMargin = target;
            browserFrame.setLayoutParams(params);
        }
    }

    private void animateAddressBar(boolean show) {
        if (topChrome == null) return;
        // addressBarHidden represents the target state, not the current frame.
        if (show == !addressBarHidden) return;

        // Increment first: cancel() also invokes onAnimationEnd on the old animator.
        // The old listener must not apply its stale hide/show completion.
        final int token = ++addressAnimationToken;
        if (addressAnimator != null) {
            addressAnimator.cancel();
            addressAnimator = null;
        }

        addressBarHidden = !show;
        if (show) {
            setBrowserContentTopOffset(true);
            topChrome.setVisibility(View.VISIBLE);
        }

        View chrome = topChrome;
        int distance = chrome.getHeight() > 0 ? chrome.getHeight() : dp(55);
        float fromY = chrome.getTranslationY();
        float fromAlpha = chrome.getAlpha();
        float toY = show ? 0f : -distance;
        float toAlpha = show ? 1f : 0f;

        if (show && chrome.getVisibility() != View.VISIBLE) {
            chrome.setVisibility(View.VISIBLE);
            fromY = -distance;
            fromAlpha = 0f;
            chrome.setTranslationY(fromY);
            chrome.setAlpha(fromAlpha);
        } else if (show && fromY == 0f && fromAlpha == 1f) {
            return;
        }

        // Capture immutable animation endpoints for Java lambda compatibility.
        final float animationFromY = fromY;
        final float animationToY = toY;
        final float animationFromAlpha = fromAlpha;
        final float animationToAlpha = toAlpha;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        addressAnimator = animator;
        animator.setDuration(show ? 230 : 190);
        animator.setInterpolator(new DecelerateInterpolator(1.35f));
        animator.addUpdateListener(value -> {
            float t = (float) value.getAnimatedValue();
            chrome.setTranslationY(animationFromY + (animationToY - animationFromY) * t);
            chrome.setAlpha(animationFromAlpha
                    + (animationToAlpha - animationFromAlpha) * t);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (token != addressAnimationToken) return;
                chrome.setTranslationY(animationToY);
                chrome.setAlpha(animationToAlpha);
                if (!show) {
                    chrome.setVisibility(View.GONE);
                    setBrowserContentTopOffset(false);
                }
                scrollAccumulator = 0f;
                scrollDirection = 0;
                ignoreNextScrollUpdate = true;
                addressAnimator = null;
            }
        });
        animator.start();
    }


        private void applySoftRipple(View view) {
        Drawable content = view.getBackground();
        if (content == null) content = new ColorDrawable(Color.TRANSPARENT);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(Color.argb(72, 169, 190, 166)),
                content,
                new ColorDrawable(Color.WHITE));
        view.setBackground(ripple);
    }

    private void hideKeyboard() {
        dismissAddressSuggestions();
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && addressField != null) {
            manager.hideSoftInputFromWindow(addressField.getWindowToken(), 0);
            addressField.clearFocus();
        }
    }

    private void showFindInPage() {
        if (activeTab == null || activeTab.session == null) return;
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setTextColor(TEXT);
        query.setHintTextColor(MUTED);
        query.setHint("Texto que quieres encontrar");
        query.setPadding(dp(12), dp(8), dp(12), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("Buscar en la página")
                .setView(query)
                .setPositiveButton("Buscar", (dialog, which) -> {
                    String text = query.getText().toString().trim();
                    if (!text.isEmpty()) {
                        activeTab.session.getFinder().find(
                                text, GeckoSession.FINDER_FIND_FORWARD);
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void toggleDesktopMode() {
        if (activeTab == null || activeTab.session == null) return;
        activeTab.desktopMode = !activeTab.desktopMode;
        activeTab.serializedState = null;
        persistTabsSnapshot();
        try {
            GeckoSessionSettings settings = activeTab.session.getSettings();
            settings.setUserAgentMode(activeTab.desktopMode
                    ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
            settings.setViewportMode(activeTab.desktopMode
                    ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                    : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
            Toast.makeText(this,
                    activeTab.desktopMode ? "Modo escritorio activado" : "Modo móvil activado",
                    Toast.LENGTH_SHORT).show();
            reloadPage();
        } catch (RuntimeException error) {
            Log.w(TAG, "Desktop mode could not be changed", error);
            Toast.makeText(this, "No se pudo cambiar el modo de visualización",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void cycleTabs(boolean reverse) {
        if (tabs.size() < 2 || activeTab == null) return;
        int index = tabs.indexOf(activeTab);
        int next = reverse ? index - 1 : index + 1;
        if (next < 0) next = tabs.size() - 1;
        if (next >= tabs.size()) next = 0;
        activateTab(tabs.get(next));
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event == null || !event.isCtrlPressed()) {
            return super.onKeyShortcut(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_L:
                focusAddress();
                addressField.selectAll();
                return true;
            case KeyEvent.KEYCODE_T:
                createTab(HOME_URL);
                return true;
            case KeyEvent.KEYCODE_N:
                if (event.isShiftPressed()) createPrivateTab();
                else createTab(HOME_URL);
                return true;
            case KeyEvent.KEYCODE_W:
                closeActiveTab();
                return true;
            case KeyEvent.KEYCODE_R:
                reloadPage();
                return true;
            case KeyEvent.KEYCODE_F:
                showFindInPage();
                return true;
            case KeyEvent.KEYCODE_TAB:
                cycleTabs(event.isShiftPressed());
                return true;
            default:
                return super.onKeyShortcut(keyCode, event);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SAVE_DOWNLOAD) {
            InputStream source = pendingDownloadStream;
            String name = pendingDownloadName;
            String mime = pendingDownloadMime;
            pendingDownloadStream = null;
            pendingDownloadName = null;
            pendingDownloadMime = null;

            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && source != null) {
                Uri destination = data.getData();
                try {
                    int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(destination, flags);
                } catch (RuntimeException ignored) { }
                copyStreamToUri(source, destination, name, mime);
            } else if (source != null) {
                try { source.close(); } catch (IOException ignored) { }
            }
            return;
        }

        if (requestCode == REQUEST_SAVE_PDF) {
            File source = pendingPdfFile;
            pendingPdfFile = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && source != null) {
                Uri destination = data.getData();
                try {
                    int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(destination, flags);
                } catch (RuntimeException ignored) { }
                copyFileToUri(source, destination, source.getName(), "application/pdf");
            } else if (source != null) {
                source.delete();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_ANDROID_SITE_PERMISSION) return;

        GeckoSession.PermissionDelegate.Callback callback = pendingAndroidPermissionCallback;
        pendingAndroidPermissionCallback = null;
        if (callback == null) return;

        boolean granted = grantResults != null && grantResults.length > 0;
        if (granted) {
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
        }
        if (granted) callback.grant();
        else callback.reject();
    }

    @Override
    protected void onPause() {
        for (BrowserTab tab : tabs) {
            if (tab.session == null) continue;
            try {
                if (!tab.session.isOpen()) continue;
                if (tab == activeTab) tab.session.setFocused(false);
                tab.session.setActive(false);
                tab.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
                tab.session.flushSessionState();
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not pause browser session cleanly", error);
            }
        }
        persistTabsSnapshot();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activeTab != null && activeTab.session != null) {
            try {
                if (!activeTab.session.isOpen()) {
                    initializeTab(activeTab, activeTab.url, true);
                } else {
                    activeTab.session.setActive(true);
                    activeTab.session.setFocused(true);
                    activeTab.session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not resume browser session cleanly", error);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (suggestionPopup != null && suggestionPopup.isShowing()) {
            dismissAddressSuggestions();
            hideKeyboard();
            return;
        }
        if (addressField != null && addressField.hasFocus()) {
            hideKeyboard();
            addressField.clearFocus();
            return;
        }
        if (addressBarHidden) {
            animateAddressBar(true);
            return;
        }
        if (activeTab != null && activeTab.canGoBack) {
            goBack();
        } else if (tabs.size() > 1) {
            closeActiveTab();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (BrowserTab tab : tabs) {
            if (tab.session != null) {
                try { tab.session.close(); }
                catch (RuntimeException error) { Log.w(TAG, "Session close failed", error); }
                tab.session = null;
            }
            recycleTabThumbnail(tab);
        }
        tabs.clear();
        activeTab = null;
        if (pendingDownloadStream != null) {
            try { pendingDownloadStream.close(); } catch (IOException ignored) { }
            pendingDownloadStream = null;
        }
        if (pendingPdfFile != null) {
            pendingPdfFile.delete();
            pendingPdfFile = null;
        }
        if (pendingAndroidPermissionCallback != null) {
            pendingAndroidPermissionCallback.reject();
            pendingAndroidPermissionCallback = null;
        }
        ioExecutor.shutdown();
        super.onDestroy();
    }
}
