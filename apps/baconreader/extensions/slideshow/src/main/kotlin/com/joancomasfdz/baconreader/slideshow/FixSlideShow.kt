// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("FixSlideShow")

package com.joancomasfdz.baconreader.slideshow

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.reseam.runtime.Export

/**
 * Called after PortraitPageLayout.getLayoutParams, which sizes a post's page in the Slide Show
 * View: the page gets the height the window has for it.
 */
@Export
fun pageLayoutParams(page: View?, params: ViewGroup.LayoutParams?): ViewGroup.LayoutParams? {
    if (page != null && params != null) SlideShowPage.fit(page, params)
    return params
}

/**
 * Called right before PostMediaHandler sets the text of an image, GIF or video post: in the Slide
 * Show View, the text scrolls inside the post's page.
 */
@Export
fun scrollText(text: TextView?) {
    if (text != null) SlideShowText.makeScrollable(text)
}
