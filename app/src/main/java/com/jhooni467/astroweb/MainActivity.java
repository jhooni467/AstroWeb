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
import android.widget.ScrollView;
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

/** AstroWeb 0.06 — adaptive browser chrome and persistent Gecko sessions. */
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
    private View addressDivider;
    private ValueAnimator addressAnimator;
    private int addressAnimationToken = 0;
    private int lastScrollY = 0;
    private float scrollAccumulator = 0f;
    private int scrollDirection = 0;
    private boolean addressBarHidden = false;
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
        String serializedState;
        Bitmap thumbnail;
        boolean canGoBack;
        boolean canGoForward;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BAR);
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



        addressDivider = new View(this);
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
        ImageButton menu = bottomButton(R.drawable.ic_more, "Herramientas", () -> {});
        menu.setOnClickListener(v -> showBrowserMenu(v));

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
        addressField.setSelection(addressField.getText().length());
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showSoftInput(addressField, InputMethodManager.SHOW_IMPLICIT);
    }

    private void createTab(String url) {
        BrowserTab tab = new BrowserTab();
        if (pendingSessionState != null && !pendingSessionState.isEmpty()) {
            tab.serializedState = pendingSessionState;
            pendingSessionState = null;
        }
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
                public void onTitleChange(GeckoSession current, String title) {
                    if (title != null && !title.trim().isEmpty()) {
                        tab.title = title.trim();
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
                        if (uri.equals(HOME_URL)) {
                            lastScrollY = 0;
                            scrollAccumulator = 0f;
                            scrollDirection = 0;
                            animateAddressBar(true);
                        }
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
                        if (tab == activeTab) {
                            handler.postDelayed(() -> captureTabPreview(tab, null), 320);
                        }
                    }
                }

                @Override
                public void onSessionStateChange(GeckoSession current, GeckoSession.SessionState state) {
                    if (tab == activeTab || tab.session == current) saveSessionState(tab, state);
                }
            });

            newSession.setScrollDelegate(new GeckoSession.ScrollDelegate() {
                @Override
                public void onScrollChanged(GeckoSession session, int scrollX, int scrollY) {
                    if (tab != activeTab) return;
                    int delta = scrollY - lastScrollY;
                    lastScrollY = Math.max(0, scrollY);

                    if (scrollY <= 3) {
                        scrollAccumulator = 0f;
                        scrollDirection = 0;
                        animateAddressBar(true);
                        return;
                    }
                    if (delta == 0) return;

                    int direction = delta > 0 ? 1 : -1;
                    if (direction != scrollDirection) {
                        scrollAccumulator = 0f;
                        scrollDirection = direction;
                    }
                    scrollAccumulator += Math.abs(delta);

                    if (direction > 0 && !addressBarHidden && scrollAccumulator >= 34f) {
                        scrollAccumulator = 0f;
                        animateAddressBar(false);
                    } else if (direction < 0 && addressBarHidden && scrollAccumulator >= 22f) {
                        scrollAccumulator = 0f;
                        animateAddressBar(true);
                    }
                }
            });
            newSession.open(runtime);
            newSession.setActive(activate);
            if (activate) {
                if (activeTab != null && activeTab != tab && activeTab.session != null) {
                    try { activeTab.session.setActive(false); }
                    catch (RuntimeException ignored) { }
                }
                activeTab = tab;
                geckoView.setSession(newSession);
                geckoView.coverUntilFirstPaint(BG);
                failureOverlay.setVisibility(View.GONE);
                addressField.setText(tab.url.equals(HOME_URL) ? "" : tab.url);
                updateNavigationButtons();
                updateBookmarkButton();
            }
            if (tab.serializedState != null && !tab.serializedState.isEmpty()) {
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
        if (activeTab != null && activeTab != tab && activeTab.session != null) {
            captureTabPreview(activeTab, null);
            try { activeTab.session.setActive(false); }
            catch (RuntimeException ignored) { }
        }
        activeTab = tab;
        try { tab.session.setActive(true); }
        catch (RuntimeException ignored) { }
        if (tab.url.equals(HOME_URL)) {
            lastScrollY = 0;
            scrollAccumulator = 0f;
            scrollDirection = 0;
            animateAddressBar(true);
        }
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
        captureTabPreview(activeTab, this::showTabsOverview);
    }

    private void captureTabPreview(BrowserTab tab, Runnable afterCapture) {
        if (tab == null || tab.session == null || geckoView == null) {
            if (afterCapture != null) afterCapture.run();
            return;
        }
        try {
            geckoView.capturePixels().accept(bitmap -> {
                if (bitmap != null && !bitmap.isRecycled()) {
                    try {
                        int maxWidth = dp(360);
                        float scale = Math.min(1f, (float) maxWidth / Math.max(1, bitmap.getWidth()));
                        int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
                        int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
                        tab.thumbnail = Bitmap.createScaledBitmap(bitmap, width, height, true);
                    } catch (RuntimeException imageError) {
                        Log.w(TAG, "Could not create tab preview", imageError);
                    }
                }
                if (afterCapture != null) afterCapture.run();
            }, error -> {
                Log.w(TAG, "Tab preview capture unavailable", error);
                if (afterCapture != null) afterCapture.run();
            });
        } catch (RuntimeException error) {
            Log.w(TAG, "Tab preview capture failed", error);
            if (afterCapture != null) afterCapture.run();
        }
    }

    private void showTabsOverview() {
        Dialog dialog = new Dialog(this);
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

        final int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
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

        page.setAlpha(0f);
        page.setTranslationY(dp(8));
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
        dialog.show();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        page.animate().alpha(1f).translationY(0f).setDuration(210)
                .setInterpolator(new DecelerateInterpolator(1.3f)).start();
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

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(19);
        close.setTextColor(Color.WHITE);
        close.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.rgb(25, 29, 26));
        closeBg.setCornerRadius(dp(14));
        close.setBackground(closeBg);
        close.setContentDescription("Cerrar pestaña");
        applySoftRipple(close);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(28), dp(28),
                Gravity.TOP | Gravity.END);
        closeParams.topMargin = dp(7);
        closeParams.rightMargin = dp(7);
        previewFrame.addView(close, closeParams);
        close.setOnClickListener(v -> closeTabFromOverview(tab, overviewDialog));

        card.addView(previewFrame, new LinearLayout.LayoutParams(-1, dp(118)));

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
            tab.thumbnail = null;
            tab.title = "Nueva pestaña";
            restartTab(tab, HOME_URL);
            overviewDialog.dismiss();
            showTabsDialog();
            return;
        }

        int oldIndex = tabs.indexOf(tab);
        boolean wasActive = tab == activeTab;
        tabs.remove(tab);
        GeckoSession old = tab.session;
        tab.session = null;
        tab.thumbnail = null;
        if (old != null) {
            try { old.close(); }
            catch (RuntimeException error) { Log.w(TAG, "Tab close failed", error); }
        }

        if (wasActive) {
            activeTab = null;
            BrowserTab next = tabs.get(Math.max(0, Math.min(oldIndex, tabs.size() - 1)));
            activateTab(next);
        }
        updateTabCounter();
        overviewDialog.dismiss();
        showTabsDialog();
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
        List<Integer> icons = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        addTool(labels, icons, actions, "Nueva pestaña", R.drawable.ic_add, () -> createTab(HOME_URL));
        addTool(labels, icons, actions, "Atrás", R.drawable.ic_back, this::goBack);
        addTool(labels, icons, actions, "Adelante", R.drawable.ic_forward, this::goForward);
        addTool(labels, icons, actions, "Recargar", R.drawable.ic_reload, this::reloadPage);
        addTool(labels, icons, actions, "Marcadores", R.drawable.ic_bookmark, this::showBookmarksDialog);
        addTool(labels, icons, actions, "Historial", R.drawable.ic_history, this::showHistoryDialog);
        addTool(labels, icons, actions, "Compartir", R.drawable.ic_share, this::shareCurrentPage);
        addTool(labels, icons, actions, "Copiar enlace", R.drawable.ic_copy, this::copyCurrentAddress);
        addTool(labels, icons, actions, "Abrir fuera", R.drawable.ic_external, this::openInExternalBrowser);

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
                        new LinearLayout.LayoutParams(-1, dp(68));
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
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(5);
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
        if (tab == null || state == null || tab != activeTab) return;
        try {
            String serialized = state.toString();
            if (serialized == null || serialized.isEmpty()) return;
            tab.serializedState = serialized;
            storage.edit()
                    .putString("session_state", serialized)
                    .putString("last_url", tab.url)
                    .apply();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not persist browser session", error);
        }
    }

    private void animateAddressBar(boolean show) {
        if (addressRow == null) return;
        if (show == !addressBarHidden && addressRow.getVisibility() == View.VISIBLE) return;
        if (!show && addressBarHidden) return;

        addressBarHidden = !show;
        if (addressAnimator != null) {
            addressAnimator.cancel();
            addressAnimator = null;
        }

        View row = addressRow;
        int targetHeight = show ? dp(50) : 0;
        int currentHeight = Math.max(0, row.getLayoutParams().height);

        if (show) {
            row.setVisibility(View.VISIBLE);
            if (addressDivider != null) addressDivider.setVisibility(View.VISIBLE);
            if (currentHeight == 0) {
                row.setAlpha(0f);
                row.setTranslationY(-dp(10));
            }
        }

        float startAlpha = row.getAlpha();
        float endAlpha = show ? 1f : 0f;
        float startTranslation = row.getTranslationY();
        float endTranslation = show ? 0f : -dp(10);
        int token = ++addressAnimationToken;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        addressAnimator = animator;
        animator.setDuration(260);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(value -> {
            float fraction = (float) value.getAnimatedValue();
            int height = currentHeight + Math.round((targetHeight - currentHeight) * fraction);
            android.view.ViewGroup.LayoutParams params = row.getLayoutParams();
            params.height = Math.max(0, height);
            row.setLayoutParams(params);
            row.setAlpha(startAlpha + (endAlpha - startAlpha) * fraction);
            row.setTranslationY(startTranslation + (endTranslation - startTranslation) * fraction);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (token != addressAnimationToken) return;
                android.view.ViewGroup.LayoutParams params = row.getLayoutParams();
                params.height = targetHeight;
                row.setLayoutParams(params);
                row.setTranslationY(endTranslation);
                row.setAlpha(endAlpha);
                if (!show) {
                    row.setVisibility(View.GONE);
                    if (addressDivider != null) addressDivider.setVisibility(View.GONE);
                }
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
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && addressField != null) {
            manager.hideSoftInputFromWindow(addressField.getWindowToken(), 0);
            addressField.clearFocus();
        }
    }

    @Override
    protected void onPause() {
        if (activeTab != null && activeTab.session != null) {
            try {
                activeTab.session.flushSessionState();
                activeTab.session.setActive(false);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not pause browser session cleanly", error);
            }
        }
        if (activeTab != null) {
            storage.edit().putString("last_url", activeTab.url)
                    .putString("session_state", activeTab.serializedState)
                    .commit();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activeTab != null && activeTab.session != null) {
            try {
                activeTab.session.setActive(true);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not resume browser session cleanly", error);
            }
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
