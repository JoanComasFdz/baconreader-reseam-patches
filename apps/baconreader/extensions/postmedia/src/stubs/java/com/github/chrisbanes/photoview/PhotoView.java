// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.github.chrisbanes.photoview;

import android.content.Context;
import android.view.GestureDetector;
import android.widget.ImageView;

public class PhotoView extends ImageView {
    public PhotoView(Context context) {
        super(context);
    }

    public void setOnDoubleTapListener(GestureDetector.OnDoubleTapListener listener) {}
}
