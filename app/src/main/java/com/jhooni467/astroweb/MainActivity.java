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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** AstroWeb 0.04 — compact browser chrome, full-width pages and basic tabs. */
public class MainActivity extends Activity {
    private static final String TAG = "AstroWeb";
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
    private EditText addressField;
    private TextView tabCounter;
    private LinearLayout addressRow;
    private int lastScrollY = 0;
    private String pendingSessionState;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageButton bookmarkButton;
    private FrameLayout failureOverlay;
    private BrowserTab activeTab;
    private SharedPreferences storage;
    private boolean recoveryScheduled;
    private int recoveryAttempts;

    private static class BrowserTab {
        GeckoSession session;
        String url = HOME_URL;
        String title = "Nueva pestaña";
        boolean canGoBack;
        boolean canGoForward;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        storage = getSharedPreferences("astroweb_browser", MODE_PRIVATE);
        pendingSessionState = storage.getString("session_state", null);
        buildInterface();
        String lastUrl = storage.getString("last_url", HOME_URL);
        createTab(lastUrl == null || lastUrl.isEmpty() ? HOME_URL : lastUrl);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, 0, 0, 0);

        // AstroWeb draws edge-to-edge on modern Android, but keeps browser controls
        // below the status bar and above the system navigation area.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(),
                    0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setBackgroundColor(BAR);

        ImageButton searchIcon = iconButton(R.drawable.ic_search, "Buscar", this::focusAddress);
        addressRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(38), dp(48)));

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
        addressField.setPadding(dp(3), 0, dp(4), 0);
        addressField.setSelectAllOnFocus(false);
        addressField.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                navigate(addressField.getText().toString());
                return true;
            }
            return false;
        });
        addressRow.addView(addressField, new LinearLayout.LayoutParams(0, dp(48), 1f));



        View addressDivider = new View(this);
        addressDivider.setBackgroundColor(DIVIDER);

        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(BG);

        FrameLayout browserFrame = new FrameLayout(this);
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
            recoveryAttempts = 0;
            restartTab(activeTab, activeTab == null ? HOME_URL : activeTab.url);
        });
        failureContent.addView(retry);

        failureOverlay.addView(failureContent,
                new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        browserFrame.addView(failureOverlay, new FrameLayout.LayoutParams(-1, -1));

        View bottomDivider = new View(this);
        bottomDivider.setBackgroundColor(DIVIDER);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setBackgroundColor(BAR);

        ImageButton home = bottomButton(R.drawable.ic_home, "Inicio", this::loadHome);
        bookmarkButton = bottomButton(R.drawable.ic_bookmark, "Marcadores", this::toggleBookmark);
        ImageButton search = bottomButton(R.drawable.ic_search, "Buscar", this::focusAddress);
        ImageButton tabs = bottomButton(R.drawable.ic_tabs, "Pestañas", this::showTabsDialog);
        ImageButton menu = bottomButton(R.drawable.ic_more, "Menú", () -> showBrowserMenu(bottomBar));

        addWeighted(bottomBar, home);
        addWeighted(bottomBar, bookmarkButton);
        addWeighted(bottomBar, search);
        addWeighted(bottomBar, tabs);
        addWeighted(bottomBar, menu);

        root.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(50)));
        root.addView(addressDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(browserFrame, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(bottomDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(bottomBar, new LinearLayout.LayoutParams(-1, dp(49)));

        setContentView(root);
        updateNavigationButtons();
        updateTabCounter();
        updateBookmarkButton();
        root.requestApplyInsets();
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
        button.setBackgroundResource(android.R.drawable.list_selector_background);
        button.setContentDescription(description);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private ImageButton bottomButton(int icon, String description, Runnable action) {
        ImageButton button = iconButton(icon, description, action);
        button.setImageTintList(ColorStateList.valueOf(TEXT));
        return button;
    }

    private void addWeighted(LinearLayout row, View button) {
        row.addView(button, new LinearLayout.LayoutParams(0, dp(49), 1f));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void focusAddress() {
        addressField.requestFocus();
        addressField.setSelection(addressField.getText().length());
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showSoftInput(addressField, InputMethodManager.SHOW_IMPLICIT);
    }

    private void createTab(String url) {
        BrowserTab tab = new BrowserTab();
        tabs.add(tab);
        initializeTab(tab, url, true);
        updateTabCounter();
    }

    private void initializeTab(BrowserTab tab, String url, boolean activate) {
        try {
            GeckoRuntime runtime = ((AstroWebApplication) getApplication()).getGeckoRuntime();
            GeckoSession newSession = new GeckoSession();
            tab.session = newSession;
            tab.url = url == null ? HOME_URL : url;
            tab.canGoBack = false;
            tab.canGoForward = false;

            newSession.setContentDelegate(new GeckoSession.ContentDelegate() {
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
            });

            newSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                @Override
                public void onLocationChange(GeckoSession current, String uri,
                        List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                        Boolean hasUserGesture) {
                    runOnUiThread(() -> {
                        if (uri == null) return;
                        tab.url = uri;
                        storage.edit().putString("last_url", uri).apply();
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
                        if (tab == activeTab) failureOverlay.setVisibility(View.GONE);
                    });
                }

                @Override
                public void onPageStop(GeckoSession current, boolean success) {
                    if (success) {
                        recoveryAttempts = 0;
                        addHistory(tab.url);
                        current.flushSessionState();
                    }
                }

                @Override
                public void onSessionStateChange(GeckoSession current, GeckoSession.SessionState state) {
                    if (tab == activeTab || tab.session == current) saveSessionState(tab, state);
                }
            });

            newSession.setScrollDelegate((session, scrollX, scrollY) -> {
                if (tab != activeTab) return;
                if (scrollY > lastScrollY + dp(8)) animateAddressBar(false);
                else if (scrollY < lastScrollY - dp(8)) animateAddressBar(true);
                lastScrollY = Math.max(0, scrollY);
            });
            newSession.open(runtime);
            if (activate) {
                activeTab = tab;
                geckoView.setSession(newSession);
                geckoView.coverUntilFirstPaint(BG);
                failureOverlay.setVisibility(View.GONE);
                addressField.setText(tab.url.equals(HOME_URL) ? "" : tab.url);
                updateNavigationButtons();
                updateBookmarkButton();
            }
            if (activate && pendingSessionState != null && !pendingSessionState.isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(pendingSessionState, Base64.DEFAULT);
                    Parcel parcel = Parcel.obtain();
                    parcel.unmarshall(bytes, 0, bytes.length);
                    parcel.setDataPosition(0);
                    newSession.restoreState(GeckoSession.SessionState.CREATOR.createFromParcel(parcel));
                    parcel.recycle();
                    pendingSessionState = null;
                } catch (Exception restoreError) {
                    Log.w(TAG, "Could not restore previous browser session", restoreError);
                    pendingSessionState = null;
                    newSession.loadUri(tab.url);
                }
            } else {
                newSession.loadUri(tab.url);
            }
            updateTabCounter();
        } catch (Throwable error) {
            Log.e(TAG, "GeckoView initialization failed", error);
            if (activate) showFailure("AstroWeb no pudo iniciarse",
                    "El motor del navegador no pudo arrancar. Inténtalo de nuevo.");
        }
    }

    private void activateTab(BrowserTab tab) {
        if (tab == null) return;
        if (tab.session == null) {
            restartTab(tab, tab.url);
            return;
        }
        activeTab = tab;
        geckoView.setSession(tab.session);
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
        tab.url = target == null || target.isEmpty() ? HOME_URL : target;
        if (tab == activeTab) failureOverlay.setVisibility(View.GONE);
        initializeTab(tab, tab.url, tab == activeTab);
    }

    private void handleEngineStopped(BrowserTab tab, GeckoSession stoppedSession) {
        runOnUiThread(() -> {
            if (isFinishing() || tab.session != stoppedSession || recoveryScheduled) return;
            recoveryScheduled = true;
            if (recoveryAttempts < 1) {
                recoveryAttempts++;
                handler.postDelayed(() -> {
                    recoveryScheduled = false;
                    if (!isFinishing() && tab.session == stoppedSession) {
                        restartTab(tab, tab.url);
                    }
                }, 850);
            } else {
                recoveryScheduled = false;
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

        String target = input;
        boolean hasScheme = input.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
        if (!hasScheme) {
            if (input.contains(" ") || !input.contains(".")) {
                target = "https://www.google.com/search?q=" + Uri.encode(input);
            } else {
                target = "https://" + input;
            }
        }

        activeTab.url = target;
        failureOverlay.setVisibility(View.GONE);
        hideKeyboard();
        try {
            activeTab.session.loadUri(target);
        } catch (RuntimeException error) {
            Log.e(TAG, "Navigation failed", error);
            showFailure("No se pudo abrir la dirección",
                    "Reinicia la pestaña e inténtalo de nuevo.");
        }
    }

    private void loadHome() {
        if (activeTab == null) {
            createTab(HOME_URL);
            return;
        }
        if (activeTab.session == null) {
            restartTab(activeTab, HOME_URL);
            return;
        }
        activeTab.url = HOME_URL;
        addressField.setText("");
        failureOverlay.setVisibility(View.GONE);
        activeTab.session.loadUri(HOME_URL);
    }

    private void reloadPage() {
        if (activeTab == null || activeTab.session == null) return;
        try {
            failureOverlay.setVisibility(View.GONE);
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
        if (tabs.isEmpty()) {
            createTab(HOME_URL);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (BrowserTab tab : new ArrayList<>(tabs)) {
            String label = tab.url.equals(HOME_URL) ? "Nueva pestaña" : tab.url;
            labels.add((tab == activeTab ? "✓   " : "") + shorten(label, 48));
            actions.add(() -> activateTab(tab));
        }
        showBottomSheet("Pestañas · " + tabs.size(), labels, actions,
                "Nueva pestaña", () -> createTab(HOME_URL),
                "Cerrar pestaña actual", this::closeActiveTab);
    }

    private String shorten(String value, int max) {
        if (value == null) return "Nueva pestaña";
        if (value.length() <= max) return value;
        return value.substring(0, max - 1) + "…";
    }

    private void closeActiveTab() {
        if (activeTab == null) return;
        if (tabs.size() == 1) {
            restartTab(activeTab, HOME_URL);
            return;
        }
        int oldIndex = tabs.indexOf(activeTab);
        BrowserTab closing = activeTab;
        tabs.remove(closing);
        GeckoSession old = closing.session;
        closing.session = null;
        if (old != null) {
            try { old.close(); }
            catch (RuntimeException error) { Log.w(TAG, "Tab close failed", error); }
        }
        activeTab = null;
        BrowserTab next = tabs.get(Math.max(0, Math.min(oldIndex - 1, tabs.size() - 1)));
        activateTab(next);
        updateTabCounter();
    }

    private void showBrowserMenu(View anchor) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Nueva pestaña"); actions.add(() -> createTab(HOME_URL));
        labels.add("Atrás"); actions.add(this::goBack);
        labels.add("Adelante"); actions.add(this::goForward);
        labels.add("Recargar"); actions.add(this::reloadPage);
        labels.add("Marcadores"); actions.add(this::showBookmarksDialog);
        labels.add("Historial"); actions.add(this::showHistoryDialog);
        labels.add("Compartir página"); actions.add(this::shareCurrentPage);
        labels.add("Copiar dirección"); actions.add(this::copyCurrentAddress);
        labels.add("Abrir en otro navegador"); actions.add(this::openInExternalBrowser);
        showBottomSheet("AstroWeb", labels, actions, null, null, null);
    }

    private void showBottomSheet(String title, List<String> labels, List<Runnable> actions,
            String primaryLabel, Runnable primaryAction,
            String secondaryLabel, Runnable secondaryAction) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(12), dp(20), dp(18));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.rgb(34, 41, 35));
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
            TextView item = new TextView(this);
            item.setText(labels.get(i));
            item.setTextColor(TEXT);
            item.setTextSize(15);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), 0, dp(10), 0);
            item.setMinHeight(dp(48));
            item.setBackgroundResource(android.R.drawable.list_selector_background);
            item.setOnClickListener(v -> {
                dialog.dismiss();
                actions.get(index).run();
            });
            panel.addView(item, new LinearLayout.LayoutParams(-1, dp(48)));
            if (i < labels.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(DIVIDER);
                panel.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }

        if (primaryLabel != null && primaryAction != null) {
            TextView primary = sheetAction(primaryLabel, true);
            primary.setOnClickListener(v -> { dialog.dismiss(); primaryAction.run(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
            p.topMargin = dp(12);
            panel.addView(primary, p);
        }
        if (secondaryLabel != null && secondaryAction != null) {
            TextView secondary = sheetAction(secondaryLabel, false);
            secondary.setOnClickListener(v -> { dialog.dismiss(); secondaryAction.run(); });
            panel.addView(secondary, new LinearLayout.LayoutParams(-1, dp(48)));
        }

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM;
            params.dimAmount = 0.48f;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
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
        String[] labels = saved.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Marcadores")
                .setItems(labels, (dialog, which) -> navigate(labels[which]))
                .setPositiveButton("Cerrar", null)
                .show();
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
        for (String item : existing.split("\n")) {
            if (!item.isEmpty()) entries.add(item);
        }
        String[] labels = entries.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Historial")
                .setItems(labels, (dialog, which) -> navigate(labels[which]))
                .setPositiveButton("Borrar historial", (dialog, which) -> {
                    storage.edit().remove("history").apply();
                    Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
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
        if (tab == null || state == null || tab != activeTab) return;
        try {
            Parcel parcel = Parcel.obtain();
            state.writeToParcel(parcel, 0);
            byte[] bytes = parcel.marshall();
            parcel.recycle();
            storage.edit()
                    .putString("session_state", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    .putString("last_url", tab.url)
                    .apply();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not persist browser session", error);
        }
    }

    private void animateAddressBar(boolean show) {
        if (addressRow == null) return;
        int target = show ? dp(50) : 0;
        View view = addressRow;
        int current = view.getLayoutParams().height;
        if (show && current == target && view.getVisibility() == View.VISIBLE) return;
        if (!show && (current == 0 || view.getVisibility() != View.VISIBLE)) return;
        if (show) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
        }
        ValueAnimator animator = ValueAnimator.ofInt(current, target);
        animator.setDuration(190);
        animator.addUpdateListener(animation -> {
            android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = (int) animation.getAnimatedValue();
            view.setLayoutParams(params);
            view.setAlpha(show ? Math.min(1f, (float) params.height / Math.max(1, target))
                    : Math.max(0f, (float) params.height / Math.max(1, current)));
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!show) view.setVisibility(View.GONE);
            }
        });
        animator.start();
    }

    private void hideKeyboard() {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && addressField != null) {
            manager.hideSoftInputFromWindow(addressField.getWindowToken(), 0);
            addressField.clearFocus();
        }
    }

    @Override
    public void onBackPressed() {
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
        }
        tabs.clear();
        activeTab = null;
        super.onDestroy();
    }
}
