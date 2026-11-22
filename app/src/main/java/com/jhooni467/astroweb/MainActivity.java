package com.jhooni467.astroweb;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;

import java.util.List;

/** AstroWeb 0.03 — minimal browser interface and GeckoView recovery. */
public class MainActivity extends Activity {
    private static final String TAG = "AstroWeb";
    private static final String HOME_URL = "resource://android/assets/home.html";
    private static final int BG = Color.rgb(9, 10, 23);
    private static final int TEXT = Color.rgb(244, 242, 255);
    private static final int MUTED = Color.rgb(151, 150, 178);
    private static final int ACCENT = Color.rgb(177, 159, 255);
    private static final int LINE = Color.argb(95, 177, 159, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private GeckoSession session;
    private GeckoView geckoView;
    private EditText addressField;
    private TextView backButton;
    private TextView forwardButton;
    private FrameLayout failureOverlay;
    private boolean canGoBack;
    private boolean canGoForward;
    private boolean recoveryScheduled;
    private int recoveryAttempts;
    private String lastRequestedUrl = HOME_URL;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildInterface();
        openBrowser(HOME_URL);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(6), dp(18), dp(4));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(18), insets.getSystemWindowInsetTop() + dp(5),
                    dp(18), insets.getSystemWindowInsetBottom() + dp(4));
            return insets;
        });

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView searchIcon = new TextView(this);
        searchIcon.setText("⌕");
        searchIcon.setTextColor(MUTED);
        searchIcon.setTextSize(23);
        searchIcon.setGravity(Gravity.CENTER);
        addressRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(30), -1));

        addressField = new EditText(this);
        addressField.setSingleLine(true);
        addressField.setTextSize(15);
        addressField.setTextColor(TEXT);
        addressField.setHintTextColor(MUTED);
        addressField.setHint("Search or enter address");
        addressField.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressField.setBackgroundColor(Color.TRANSPARENT);
        addressField.setPadding(dp(5), 0, dp(5), 0);
        addressField.setSelectAllOnFocus(false);
        addressField.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                navigate(addressField.getText().toString());
                return true;
            }
            return false;
        });
        addressRow.addView(addressField, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView go = textButton("GO", true);
        go.setOnClickListener(v -> navigate(addressField.getText().toString()));
        addressRow.addView(go, new LinearLayout.LayoutParams(dp(48), dp(42)));
        root.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(48)));

        View addressDivider = new View(this);
        addressDivider.setBackgroundColor(LINE);
        root.addView(addressDivider, new LinearLayout.LayoutParams(-1, dp(1)));

        FrameLayout browserFrame = new FrameLayout(this);
        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(BG);
        browserFrame.addView(geckoView, new FrameLayout.LayoutParams(-1, -1));

        failureOverlay = new FrameLayout(this);
        failureOverlay.setBackgroundColor(BG);
        failureOverlay.setVisibility(View.GONE);
        LinearLayout failureContent = new LinearLayout(this);
        failureContent.setGravity(Gravity.CENTER);
        failureContent.setOrientation(LinearLayout.VERTICAL);
        failureContent.setPadding(dp(28), dp(24), dp(28), dp(24));

        TextView symbol = new TextView(this);
        symbol.setText("✦");
        symbol.setTextColor(ACCENT);
        symbol.setTextSize(30);
        symbol.setGravity(Gravity.CENTER);
        failureContent.addView(symbol, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView failureTitle = new TextView(this);
        failureTitle.setText("Browser stopped");
        failureTitle.setTextColor(TEXT);
        failureTitle.setTextSize(18);
        failureTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        failureTitle.setGravity(Gravity.CENTER);
        failureContent.addView(failureTitle);

        TextView failureMessage = new TextView(this);
        failureMessage.setText("AstroWeb couldn't keep this page running.");
        failureMessage.setTextColor(MUTED);
        failureMessage.setTextSize(13);
        failureMessage.setGravity(Gravity.CENTER);
        failureMessage.setPadding(0, dp(9), 0, dp(18));
        failureContent.addView(failureMessage);

        TextView retry = textButton("RESTART BROWSER", true);
        retry.setOnClickListener(v -> {
            recoveryAttempts = 0;
            restartBrowser(lastRequestedUrl);
        });
        failureContent.addView(retry, new LinearLayout.LayoutParams(dp(170), dp(44)));

        FrameLayout.LayoutParams failureContentParams =
                new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
        failureOverlay.addView(failureContent, failureContentParams);
        browserFrame.addView(failureOverlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams browserParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        browserParams.topMargin = dp(3);
        browserParams.bottomMargin = dp(3);
        root.addView(browserFrame, browserParams);

        View toolbarDivider = new View(this);
        toolbarDivider.setBackgroundColor(Color.argb(55, 177, 159, 255));
        root.addView(toolbarDivider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);

        backButton = toolbarButton("‹", "Back");
        backButton.setOnClickListener(v -> goBack());
        forwardButton = toolbarButton("›", "Forward");
        forwardButton.setOnClickListener(v -> goForward());

        TextView home = toolbarButton("⌂", "Home");
        home.setOnClickListener(v -> loadHome());

        TextView menu = toolbarButton("⋯", "Browser menu");
        menu.setOnClickListener(v -> showBrowserMenu(menu));

        addToolbarItem(toolbar, backButton);
        addToolbarItem(toolbar, forwardButton);
        addToolbarItem(toolbar, home);
        addToolbarItem(toolbar, menu);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(49)));

        setContentView(root);
        updateNavigationButtons();
        root.requestApplyInsets();
    }

    private void openBrowser(String url) {
        try {
            GeckoRuntime runtime = ((AstroWebApplication) getApplication()).getGeckoRuntime();
            GeckoSession newSession = new GeckoSession();

            newSession.setContentDelegate(new GeckoSession.ContentDelegate() {
                @Override
                public void onCrash(GeckoSession crashedSession) {
                    Log.e(TAG, "Gecko content process crashed");
                    handleEngineStopped();
                }

                @Override
                public void onKill(GeckoSession killedSession) {
                    Log.w(TAG, "Gecko content process was killed");
                    handleEngineStopped();
                }
            });

            newSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                @Override
                public void onLocationChange(GeckoSession current, String uri,
                        List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                        Boolean hasUserGesture) {
                    runOnUiThread(() -> {
                        if (uri == null || addressField == null) return;
                        lastRequestedUrl = uri;
                        addressField.setText(uri.startsWith(HOME_URL) ? "" : uri);
                        addressField.setSelection(addressField.getText().length());
                    });
                }

                @Override
                public void onCanGoBack(GeckoSession current, boolean value) {
                    runOnUiThread(() -> {
                        canGoBack = value;
                        updateNavigationButtons();
                    });
                }

                @Override
                public void onCanGoForward(GeckoSession current, boolean value) {
                    runOnUiThread(() -> {
                        canGoForward = value;
                        updateNavigationButtons();
                    });
                }

                @Override
                public GeckoResult<String> onLoadError(
                        GeckoSession current, String uri, WebRequestError error) {
                    Log.e(TAG, "Page load failed: " + uri + " — " + error);
                    runOnUiThread(() -> showFailure(
                            "Page couldn't load",
                            "Check your connection or the web address and try again."));
                    return null;
                }
            });

            newSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
                @Override
                public void onPageStart(GeckoSession current, String uri) {
                    runOnUiThread(() -> failureOverlay.setVisibility(View.GONE));
                }

                @Override
                public void onPageStop(GeckoSession current, boolean success) {
                    if (success) recoveryAttempts = 0;
                }
            });

            newSession.open(runtime);
            session = newSession;
            geckoView.setSession(session);
            geckoView.coverUntilFirstPaint(BG);
            lastRequestedUrl = url;
            session.loadUri(url);
            updateNavigationButtons();
        } catch (Throwable error) {
            Log.e(TAG, "GeckoView initialization failed", error);
            showFailure("AstroWeb couldn't start",
                    "The browser engine couldn't initialize. Try restarting it.");
        }
    }

    private void handleEngineStopped() {
        runOnUiThread(() -> {
            if (isFinishing() || recoveryScheduled) return;
            recoveryScheduled = true;
            if (recoveryAttempts < 1) {
                recoveryAttempts++;
                handler.postDelayed(() -> {
                    recoveryScheduled = false;
                    if (!isFinishing()) restartBrowser(lastRequestedUrl);
                }, 900);
            } else {
                recoveryScheduled = false;
                showFailure("Browser engine stopped",
                        "The browser stopped more than once. Restart it to continue.");
            }
        });
    }

    private void restartBrowser(String target) {
        failureOverlay.setVisibility(View.GONE);
        GeckoSession old = session;
        session = null;
        if (old != null) {
            try { old.close(); }
            catch (RuntimeException error) { Log.w(TAG, "Session close failed", error); }
        }
        canGoBack = false;
        canGoForward = false;
        updateNavigationButtons();
        openBrowser(target == null || target.isEmpty() ? HOME_URL : target);
    }

    private void navigate(String raw) {
        if (session == null || raw == null) return;
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

        lastRequestedUrl = target;
        failureOverlay.setVisibility(View.GONE);
        hideKeyboard();
        try { session.loadUri(target); }
        catch (RuntimeException error) {
            Log.e(TAG, "Navigation failed", error);
            showFailure("Navigation unavailable", "Restart the browser and try again.");
        }
    }

    private void loadHome() {
        if (session == null) {
            restartBrowser(HOME_URL);
            return;
        }
        lastRequestedUrl = HOME_URL;
        failureOverlay.setVisibility(View.GONE);
        session.loadUri(HOME_URL);
    }

    private void goBack() {
        if (session != null && canGoBack) {
            try { session.goBack(); }
            catch (RuntimeException error) { Log.w(TAG, "Back failed", error); }
        }
    }

    private void goForward() {
        if (session != null && canGoForward) {
            try { session.goForward(); }
            catch (RuntimeException error) { Log.w(TAG, "Forward failed", error); }
        }
    }

    private void showBrowserMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Reload page").setOnMenuItemClickListener(item -> {
            if (session != null) {
                try { session.reload(); }
                catch (RuntimeException error) { restartBrowser(lastRequestedUrl); }
            } else restartBrowser(lastRequestedUrl);
            return true;
        });
        popup.getMenu().add("Share page").setOnMenuItemClickListener(item -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, lastRequestedUrl);
            startActivity(Intent.createChooser(share, "Share page"));
            return true;
        });
        popup.getMenu().add("Copy address").setOnMenuItemClickListener(item -> {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Web address", lastRequestedUrl));
                Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.getMenu().add("Open in another browser").setOnMenuItemClickListener(item -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(lastRequestedUrl))); }
            catch (Exception error) {
                Toast.makeText(this, "No compatible browser found", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }

    private void showFailure(String title, String message) {
        if (failureOverlay == null) return;
        LinearLayout content = (LinearLayout) failureOverlay.getChildAt(0);
        ((TextView) content.getChildAt(1)).setText(title);
        ((TextView) content.getChildAt(2)).setText(message);
        failureOverlay.setVisibility(View.VISIBLE);
    }

    private TextView textButton(String label, boolean accent) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(accent ? Color.rgb(18, 15, 38) : TEXT);
        button.setTextSize(accent ? 12 : 23);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        if (accent) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ACCENT);
            bg.setCornerRadius(dp(8));
            button.setBackground(bg);
        } else {
            button.setBackgroundResource(android.R.drawable.list_selector_background);
        }
        button.setContentDescription(label);
        return button;
    }

    private TextView toolbarButton(String glyph, String description) {
        TextView button = new TextView(this);
        button.setText(glyph);
        button.setTextSize(27);
        button.setTextColor(TEXT);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackgroundResource(android.R.drawable.list_selector_background);
        return button;
    }

    private void addToolbarItem(LinearLayout toolbar, TextView button) {
        toolbar.addView(button, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(canGoBack);
            backButton.setAlpha(canGoBack ? 1f : 0.35f);
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(canGoForward);
            forwardButton.setAlpha(canGoForward ? 1f : 0.35f);
        }
    }

    private void hideKeyboard() {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(addressField.getWindowToken(), 0);
            addressField.clearFocus();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (canGoBack) goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (session != null) {
            try { session.close(); }
            catch (RuntimeException error) { Log.w(TAG, "Session close failed", error); }
            session = null;
        }
        super.onDestroy();
    }
}
