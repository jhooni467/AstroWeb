package com.jhooni467.astroweb;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

import android.net.Uri;
import java.util.List;

/**
 * AstroWeb 0.01 — first functional browser shell powered by GeckoView.
 * The interface is intentionally small while the browser foundation is validated.
 */
public class MainActivity extends Activity {
    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final int SPACE = Color.rgb(9, 11, 27);
    private static final int GLASS = Color.argb(170, 30, 30, 65);
    private static final int TEXT = Color.rgb(246, 244, 255);
    private static final int MUTED = Color.rgb(170, 169, 198);
    private static final int ACCENT = Color.rgb(169, 155, 255);

    private GeckoSession session;
    private GeckoView geckoView;
    private EditText addressField;
    private Button backButton;
    private Button forwardButton;
    private boolean canGoBack;
    private boolean canGoForward;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(SPACE);
        getWindow().setNavigationBarColor(SPACE);
        getWindow().getDecorView().setSystemUiVisibility(0);

        buildInterface();
        openBrowser();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SPACE);
        root.setPadding(dp(14), dp(12), dp(14), dp(8));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView mark = new TextView(this);
        mark.setText("✦");
        mark.setTextColor(ACCENT);
        mark.setTextSize(23);
        mark.setPadding(0, 0, dp(9), 0);
        brandRow.addView(mark);

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("ASTROWEB");
        title.setTextColor(TEXT);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(0.16f);

        TextView subtitle = new TextView(this);
        subtitle.setText("EXPLORE BEYOND THE ORDINARY");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(9);
        subtitle.setLetterSpacing(0.12f);

        brandText.addView(title);
        brandText.addView(subtitle);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView version = new TextView(this);
        version.setText("0.01");
        version.setTextColor(MUTED);
        version.setTextSize(11);
        brandRow.addView(version);
        root.addView(brandRow, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setPadding(dp(12), dp(3), dp(4), dp(3));
        addressRow.setBackground(rounded(GLASS, Color.argb(100, 151, 139, 220), 18));

        TextView lock = new TextView(this);
        lock.setText("⌕");
        lock.setTextColor(MUTED);
        lock.setTextSize(21);
        lock.setPadding(0, 0, dp(8), 0);
        addressRow.addView(lock);

        addressField = new EditText(this);
        addressField.setSingleLine(true);
        addressField.setTextSize(14);
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
        goButton.setOnClickListener(v -> navigate(addressField.getText().toString()));
        addressRow.addView(goButton, new LinearLayout.LayoutParams(dp(48), dp(42)));
        LinearLayout.LayoutParams addressParams =
                new LinearLayout.LayoutParams(-1, dp(54));
        addressParams.topMargin = dp(12);
        addressParams.bottomMargin = dp(10);
        root.addView(addressRow, addressParams);

        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(SPACE);
        root.addView(geckoView, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, dp(9), 0, 0);

        backButton = makeButton("‹", false);
        backButton.setTextSize(25);
        backButton.setOnClickListener(v -> {
            if (session != null && canGoBack) session.goBack();
        });

        forwardButton = makeButton("›", false);
        forwardButton.setTextSize(25);
        forwardButton.setOnClickListener(v -> {
            if (session != null && canGoForward) session.goForward();
        });

        Button home = makeButton("⌂", false);
        home.setTextSize(22);
        home.setOnClickListener(v -> loadHome());

        Button refresh = makeButton("↻", false);
        refresh.setTextSize(21);
        refresh.setOnClickListener(v -> {
            if (session != null) session.reload();
        });

        addControl(controls, backButton);
        addControl(controls, forwardButton);
        addControl(controls, home);
        addControl(controls, refresh);
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(52)));

        setContentView(root);
    }

    private void openBrowser() {
        AstroWebApplication app = (AstroWebApplication) getApplication();
        session = new GeckoSession();
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(
                    GeckoSession currentSession,
                    String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                    Boolean hasUserGesture) {
                runOnUiThread(() -> {
                    if (addressField == null || url == null) return;
                    if (url.startsWith("file:///android_asset/home.html")) {
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
        });

        session.open(app.getGeckoRuntime());
        geckoView.setSession(session);
        session.loadUri(HOME_URL);
    }

    private void navigate(String rawInput) {
        if (session == null || rawInput == null) return;
        String input = rawInput.trim();
        if (input.isEmpty()) return;

        String target = input;
        boolean hasScheme = input.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
        if (!hasScheme) {
            if (input.contains(" ") || !input.contains(".")) {
                target = "https://www.google.com/search?q="
                        + Uri.encode(input);
            } else {
                target = "https://" + input;
            }
        }
        session.loadUri(target);
    }

    private void loadHome() {
        if (session != null) session.loadUri(HOME_URL);
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(canGoBack);
            backButton.setAlpha(canGoBack ? 1f : 0.42f);
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(canGoForward);
            forwardButton.setAlpha(canGoForward ? 1f : 0.42f);
        }
    }

    private Button makeButton(String label, boolean highlighted) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(highlighted ? Color.rgb(18, 16, 39) : TEXT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), dp(4), dp(8), dp(4));
        button.setBackground(rounded(
                highlighted ? ACCENT : GLASS,
                Color.argb(90, 170, 160, 225),
                16
        ));
        return button;
    }

    private void addControl(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(46), 1f);
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
            session.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
