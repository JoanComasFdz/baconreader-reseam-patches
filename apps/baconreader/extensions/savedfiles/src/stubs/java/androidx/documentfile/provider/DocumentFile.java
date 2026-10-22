// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package androidx.documentfile.provider;

import android.net.Uri;

public abstract class DocumentFile {
    public abstract boolean delete();

    public abstract Uri getUri();

    public abstract boolean isFile();

    public abstract long length();
}
