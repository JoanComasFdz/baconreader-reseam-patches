// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.data;

public class ThreadItem {
    public static class CommentItem extends ThreadItem {
        public com.onelouder.baconreader.reddit.Comment comment;

        public CommentItem() {}
    }
}
