// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.google.android.exoplayer2.ui;

import android.content.Context;
import android.widget.FrameLayout;
import com.google.android.exoplayer2.Player;

public class PlayerView extends FrameLayout {
    public PlayerView(Context context) {
        super(context);
    }

    public native void setPlayer(Player player);

    public native void setShowBuffering(int showBuffering);

    public native void setShutterBackgroundColor(int color);
}
