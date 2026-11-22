package com.jhooni467.astroweb;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;

import java.util.List;

/**
 * AstroWeb 0.02 — GeckoView browser shell with loading and recovery states.
 */
public class MainActivity extends Activity {
    private static final String TAG = "AstroWeb";
    private static final String HOME_URL = "resource://android/assets/home.html";

    private static final int SPACE = Color.rgb(8, 10, 25);
    private static final int SURFACE = Color.rgb(20, 21, 48);
    private static final int GLASS = Color.argb(225, 25, 26, 57);
    private static final int TEXT = Color.rgb(246, 244, 255);
    private static final int MUTED = Color.rgb(166, 165, 195);
    private static final int ACCENT = Color.rgb(174, 157, 255);
    private static final int BORDER = Color.argb(115, 163, 147, 255);

    private GeckoSession session;
    private GeckoView geckoView;
    private EditText addressField;
    private Button backButton;
    private Button forwardButton;
    private ProgressBar loadProgress;
    private TextView statusText;
    private LinearLayout failureOverlay;

    private boolean canGoBack;
    private boolean canGoForward;
    private String lastRequestedUrl = HOME_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(SPACE);
        getWindow().setNavigationBarColor(SPACE);

        buildInterface();
        openBrowser(HOME_URL);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SPACE);
        root.setPadding(dp(16), dp(14), dp(16), dp(10));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView mark = new TextView(this);
        mark.setText("✦");
        mark.setTextColor(ACCENT);
        mark.setTextSize(27);
        mark.setGravity(Gravity.CENTER);
        mark.setPadding(0, 0, dp(11), 0);
        brandRow.addView(mark, new LinearLayout.LayoutParams(dp(39), dp(44)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("ASTROWEB");
        title.setTextColor(TEXT);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(0.13f);

        TextView subtitle = new TextView(this);
        subtitle.setText("EXPLORE BEYOND THE ORDINARY");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(9);
        subtitle.setLetterSpacing(0.12f);

        brandText.addView(title);
        brandText.addView(subtitle);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView version = new TextView(this);
        version.setText("0.02");
        version.setTextColor(MUTED);
        version.setTextSize(11);
        brandRow.addView(version);

        root.addView(brandRow, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setPadding(dp(14), dp(4), dp(5), dp(4));
        addressRow.setBackground(rounded(SURFACE, BORDER, 22));

        TextView searchMark = new TextView(this);
        searchMark.setText("⌕");
        searchMark.setTextColor(MUTED);
        searchMark.setTextSize(24);
        searchMark.setGravity(Gravity.CENTER);
        addressRow.addView(searchMark, new LinearLayout.LayoutParams(dp(30), -1));

        addressField = new EditText(this);
        addressField.setSingleLine(true);
        addressField.setTextSize(15);
        addressField.setTextColor(TEXT);
        addressField.setHintTextColor(MUTED);
        addressField.setHint("Search or enter a website");
        addressField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressField.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressField.setBackgroundColor(Color.TRANSPARENT);
        addressField.setPadding(0, dp(8), 0, dp(8));
        addressField.setSelectAllOnFocus(false);
        addressField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                navigate(addressField.getText().toString());
                return true;
            }
            return false;
        });
        addressRow.addView(addressField, new LinearLayout.LayoutParams(0, -2, 1f));

        Button goButton = makeButton("GO", true);
        goButton.setTextSize(14);
        goButton.setOnClickListener(v -> navigate(addressField.getText().toString()));
        addressRow.addView(goButton, new LinearLayout.LayoutParams(dp(58), dp(44)));

        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, dp(58));
        addressParams.topMargin = dp(13);
        addressParams.bottomMargin = dp(7);
        root.addView(addressRow, addressParams);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);

        statusText = new TextView(this);
        statusText.setText("READY TO EXPLORE");
        statusText.setTextColor(MUTED);
        statusText.setTextSize(9);
        statusText.setLetterSpacing(0.12f);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, dp(19), 1f));

        loadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadProgress.setIndeterminate(true);
        loadProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        loadProgress.setVisibility(View.GONE);
        statusRow.addView(loadProgress, new LinearLayout.LayoutParams(dp(62), dp(3)));
        root.addView(statusRow, new LinearLayout.LayoutParams(-1, dp(20)));

        FrameLayout browserFrame = new FrameLayout(this);
        browserFrame.setBackground(rounded(GLASS, Color.argb(65, 174, 157, 255), 20));
        browserFrame.setClipToOutline(true);

        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(SPACE);
        browserFrame.addView(geckoView, new FrameLayout.LayoutParams(-1, -1));

        failureOverlay = new LinearLayout(this);
        failureOverlay.setOrientation(LinearLayout.VERTICAL);
        failureOverlay.setGravity(Gravity.CENTER);
        failureOverlay.setPadding(dp(28), dp(24), dp(28), dp(24));
        failureOverlay.setBackgroundColor(SPACE);
        failureOverlay.setVisibility(View.GONE);

        TextView failureMark = new TextView(this);
        failureMark.setText("✦");
        failureMark.setTextColor(ACCENT);
        failureMark.setTextSize(34);
        failureMark.setGravity(Gravity.CENTER);
        failureOverlay.addView(failureMark, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView failureTitle = new TextView(this);
        failureTitle.setText("This page is out of reach");
        failureTitle.setTextColor(TEXT);
        failureTitle.setTextSize(19);
        failureTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        failureTitle.setGravity(Gravity.CENTER);
        failureOverlay.addView(failureTitle);

        TextView failureMessage = new TextView(this);
        failureMessage.setText("Check your connection or try opening the page again.");
        failureMessage.setTextColor(MUTED);
        failureMessage.setTextSize(13);
        failureMessage.setGravity(Gravity.CENTER);
        failureMessage.setPadding(0, dp(9), 0, dp(18));
        failureOverlay.addView(failureMessage);

        Button retry = makeButton("TRY AGAIN", true);
        retry.setOnClickListener(v -> restartBrowser(lastRequestedUrl));
        failureOverlay.addView(retry, new LinearLayout.LayoutParams(dp(150), dp(46)));

        FrameLayout.LayoutParams failureParams =
                new FrameLayout.LayoutParams(-1, -1);
        browserFrame.addView(failureOverlay, failureParams);

        LinearLayout.LayoutParams browserParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        browserParams.topMargin = dp(5);
        browserParams.bottomMargin = dp(9);
        root.addView(browserFrame, browserParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        backButton = makeButton("‹", false);
        backButton.setTextSize(29);
        backButton.setContentDescription("Back");
        backButton.setOnClickListener(v -> {
            if (session != null && canGoBack) {
                try {
                    session.goBack();
                } catch (RuntimeException error) {
                    Log.w(TAG, "Back navigation unavailable", error);
                }
            }
        });

        forwardButton = makeButton("›", false);
        forwardButton.setTextSize(29);
        forwardButton.setContentDescription("Forward");
        forwardButton.setOnClickListener(v -> {
            if (session != null && canGoForward) {
                try {
                    session.goForward();
                } catch (RuntimeException error) {
                    Log.w(TAG, "Forward navigation unavailable", error);
                }
            }
        });

        Button home = makeButton("⌂", false);
        home.setTextSize(24);
        home.setContentDescription("Home");
        home.setOnClickListener(v -> loadHome());

        Button refresh = makeButton("↻", false);
        refresh.setTextSize(23);
        refresh.setContentDescription("Reload");
        refresh.setOnClickListener(v -> reloadPage());

        addControl(controls, backButton);
        addControl(controls, forwardButton);
        addControl(controls, home);
        addControl(controls, refresh);
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(54)));

        setContentView(root);
        setStatus("READY TO EXPLORE", false);
    }

    private void openBrowser(String startUrl) {
        try {
            AstroWebApplication app = (AstroWebApplication) getApplication();
            GeckoRuntime runtime = app.getGeckoRuntime();

            GeckoSession newSession = new GeckoSession();
            newSession.setContentDelegate(new GeckoSession.ContentDelegate() {
                @Override
                public void onCrash(GeckoSession crashedSession) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Gecko content process crashed");
                        showFailure("Browser engine stopped",
                                "AstroWeb can try to restart the browser engine.");
                    });
                }

                @Override
                public void onKill(GeckoSession killedSession) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Gecko content process was killed");
                        showFailure("Browser engine stopped",
                                "The browser process was stopped. Try restarting it.");
                    });
                }
            });

            newSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                @Override
                public void onLocationChange(
                        GeckoSession currentSession,
                        String url,
                        List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                        Boolean hasUserGesture) {
                    runOnUiThread(() -> {
                        if (url == null || addressField == null) return;
                        lastRequestedUrl = url;
                        if (url.startsWith(HOME_URL)) {
                            addressField.setText("");
                        } else {
                            addressField.setText(url);
                            addressField.setSelection(addressField.getText().length());
                        }
                    });
                }

                @Override
                public void onCanGoBack(GeckoSession currentSession, boolean value) {
                    runOnUiThread(() -> {
                        canGoBack = value;
                        updateNavigationButtons();
                    });
                }

                @Override
                public void onCanGoForward(GeckoSession currentSession, boolean value) {
                    runOnUiThread(() -> {
                        canGoForward = value;
                        updateNavigationButtons();
                    });
                }

                @Override
                public GeckoResult<String> onLoadError(
                        GeckoSession currentSession, String uri, WebRequestError error) {
                    Log.e(TAG, "Page load failed: " + uri + " — " + error);
                    runOnUiThread(() -> showFailure(
                            "This page is out of reach",
                            "Check your connection or verify the address, then try again."
                    ));
                    return null;
                }
            });

            newSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
                @Override
                public void onPageStart(GeckoSession currentSession, String url) {
                    runOnUiThread(() -> {
                        failureOverlay.setVisibility(View.GONE);
                        setStatus("LOADING PAGE", true);
                    });
                }

                @Override
                public void onPageStop(GeckoSession currentSession, boolean success) {
                    runOnUiThread(() -> {
                        setStatus(success ? "READY" : "PAGE LOAD FAILED", false);
                        if (success) failureOverlay.setVisibility(View.GONE);
                    });
                }

                @Override
                public void onProgressChange(GeckoSession currentSession, int progress) {
                    runOnUiThread(() -> {
                        if (loadProgress != null && progress >= 0) {
                            loadProgress.setProgress(progress);
                        }
                    });
                }
            });

            newSession.open(runtime);
            session = newSession;
            geckoView.setSession(session);
            geckoView.coverUntilFirstPaint(SPACE);

            lastRequestedUrl = startUrl;
            session.loadUri(startUrl);
            updateNavigationButtons();
        } catch (Throwable error) {
            Log.e(TAG, "Could not initialize GeckoView", error);
            showFailure("AstroWeb couldn't start",
                    "The browser engine could not initialize on this device. Tap below to retry.");
        }
    }

    private void restartBrowser(String target) {
        failureOverlay.setVisibility(View.GONE);
        if (session != null) {
            try {
                session.close();
            } catch (RuntimeException error) {
                Log.w(TAG, "Previous browser session could not close cleanly", error);
            }
            session = null;
        }
        canGoBack = false;
        canGoForward = false;
        updateNavigationButtons();
        openBrowser(target == null || target.isEmpty() ? HOME_URL : target);
    }

    private void navigate(String rawInput) {
        if (session == null || rawInput == null) return;

        String input = rawInput.trim();
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

        try {
            session.loadUri(target);
        } catch (RuntimeException error) {
            Log.e(TAG, "Navigation could not start: " + target, error);
            showFailure("Navigation unavailable", "Try restarting the browser engine.");
        }
    }

    private void loadHome() {
        if (session != null) {
            lastRequestedUrl = HOME_URL;
            failureOverlay.setVisibility(View.GONE);
            session.loadUri(HOME_URL);
        }
    }

    private void reloadPage() {
        if (session == null) {
            restartBrowser(lastRequestedUrl);
            return;
        }
        failureOverlay.setVisibility(View.GONE);
        try {
            session.reload();
        } catch (RuntimeException error) {
            Log.e(TAG, "Reload failed", error);
            restartBrowser(lastRequestedUrl);
        }
    }

    private void showFailure(String title, String message) {
        if (failureOverlay == null) return;
        if (failureOverlay.getChildCount() > 1
                && failureOverlay.getChildAt(1) instanceof TextView) {
            ((TextView) failureOverlay.getChildAt(1)).setText(title);
        }
        if (failureOverlay.getChildCount() > 2
                && failureOverlay.getChildAt(2) instanceof TextView) {
            ((TextView) failureOverlay.getChildAt(2)).setText(message);
        }
        failureOverlay.setVisibility(View.VISIBLE);
        setStatus("BROWSER NEEDS ATTENTION", false);
    }

    private void setStatus(String message, boolean loading) {
        if (statusText != null) statusText.setText(message);
        if (loadProgress != null) {
            loadProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) loadProgress.setProgress(0);
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null && addressField != null) {
                manager.hideSoftInputFromWindow(addressField.getWindowToken(), 0);
                addressField.clearFocus();
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not hide keyboard", error);
        }
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(canGoBack);
            backButton.setAlpha(canGoBack ? 1f : 0.38f);
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(canGoForward);
            forwardButton.setAlpha(canGoForward ? 1f : 0.38f);
        }
    }

    private Button makeButton(String label, boolean highlighted) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(highlighted ? Color.rgb(17, 15, 38) : TEXT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), dp(3), dp(8), dp(3));
        button.setBackground(rounded(
                highlighted ? ACCENT : SURFACE,
                highlighted ? Color.argb(80, 255, 255, 255) : BORDER,
                highlighted ? 18 : 20
        ));
        return button;
    }

    private void addControl(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(50), 1f);
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        row.addView(button, params);
    }

    private GradientDrawable rounded(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (session != null && canGoBack) {
            try {
                session.goBack();
                return;
            } catch (RuntimeException error) {
                Log.w(TAG, "Back navigation failed", error);
            }
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            try {
                session.close();
            } catch (RuntimeException error) {
                Log.w(TAG, "Session close failed", error);
            }
            session = null;
        }
        super.onDestroy();
    }
}
