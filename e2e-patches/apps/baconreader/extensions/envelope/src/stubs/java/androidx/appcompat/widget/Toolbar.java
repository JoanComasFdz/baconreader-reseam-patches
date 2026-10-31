// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package androidx.appcompat.widget;

import android.content.Context;
import android.view.ViewGroup;

public class Toolbar extends ViewGroup {
    public Toolbar(Context context) {
        super(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {}

    public CharSequence getNavigationContentDescription() {
        return null;
    }

    public void setNavigationContentDescription(CharSequence description) {}

    public void setNavigationIcon(int resId) {}
}
