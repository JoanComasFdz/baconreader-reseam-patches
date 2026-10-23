// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader;

import android.content.Context;
import android.util.AttributeSet;

/** Shows a shortened copy of its text until "see more" is tapped. */
public class ExpandableTextView extends ClickableTextView {
    public ExpandableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public native CharSequence getOriginalText();
}
