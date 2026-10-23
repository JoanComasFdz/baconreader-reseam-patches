// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.onelouder.baconreader.data;

public class SessionManager {
    public static synchronized native SessionToken getToken();
}
