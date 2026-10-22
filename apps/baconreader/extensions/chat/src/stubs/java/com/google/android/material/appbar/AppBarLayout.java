// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.google.android.material.appbar;

import android.content.Context;
import android.widget.LinearLayout;

// Material 1.1.0, the version BaconReader bundles, is only on Google's Maven.
public class AppBarLayout extends LinearLayout {
    public AppBarLayout(Context context) {
        super(context);
    }

    public native void setExpanded(boolean expanded, boolean animate);
}
