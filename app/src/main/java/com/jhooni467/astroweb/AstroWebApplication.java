package com.jhooni467.astroweb;

import android.app.Application;

import org.mozilla.geckoview.GeckoRuntime;

/**
 * Provides one GeckoRuntime for the lifetime of the application process.
 * Runtime creation is lazy so MainActivity can display a useful fallback
 * instead of failing before the browser interface is shown.
 */
public class AstroWebApplication extends Application {
    private GeckoRuntime geckoRuntime;

    public synchronized GeckoRuntime getGeckoRuntime() {
        if (geckoRuntime == null) {
            geckoRuntime = GeckoRuntime.create(this);
        }
        return geckoRuntime;
    }
}
