package com.jhooni467.astroweb;

import android.app.Application;

import org.mozilla.geckoview.GeckoRuntime;

/**
 * Owns the process-wide GeckoRuntime. A single runtime can serve many sessions/tabs.
 */
public class AstroWebApplication extends Application {
    private GeckoRuntime geckoRuntime;

    @Override
    public void onCreate() {
        super.onCreate();
        geckoRuntime = GeckoRuntime.create(this);
    }

    public GeckoRuntime getGeckoRuntime() {
        return geckoRuntime;
    }
}
