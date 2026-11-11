// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.google.android.exoplayer2.ui;

import android.content.Context;
import android.widget.FrameLayout;

public class PlayerControlView extends FrameLayout {
    public PlayerControlView(Context context) {
        super(context);
    }

    public interface VisibilityListener {
        void onVisibilityChange(int visibility);
    }
}
