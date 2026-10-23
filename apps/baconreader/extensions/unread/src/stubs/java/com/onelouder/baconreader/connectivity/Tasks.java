// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.connectivity;

public class Tasks {
    public abstract static class OnCompleteListener<T> {
        public OnCompleteListener() {}

        public abstract void onCancel(String error);

        public abstract void onComplete(T result);
    }
}
