// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium;
using OpenQA.Selenium.Appium;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>
/// The only ways a page object finds a view (E2E-TEST-CODE-RULES.md, rule c): its resource id, its content description or
/// its exact text. Never XPath or a position.
/// </summary>
public static class Locate
{
    /// <summary>A view of BaconReader by its resource id (<c>R.id.toolbarTitle</c> → "toolbarTitle").</summary>
    public static By AppId(string id) => By.Id($"{E2EDevice.Package}:id/{id}");

    /// <summary>A view of Android's own (a dialog's <c>android:id/button1</c>) by its resource id.</summary>
    public static By AndroidId(string id) => By.Id($"android:id/{id}");

    /// <summary>A view by its content description, which views a patch creates in code get from the patch.</summary>
    public static By Description(string description) => MobileBy.AccessibilityId(description);

    /// <summary>A view showing exactly <paramref name="text"/>.</summary>
    public static By Text(string text) =>
        MobileBy.AndroidUIAutomator($"new UiSelector().text(\"{text.Replace("\\", "\\\\").Replace("\"", "\\\"")}\")");
}
