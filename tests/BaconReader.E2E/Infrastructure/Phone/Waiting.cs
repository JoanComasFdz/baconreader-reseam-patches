// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>The page objects' waits: until something the user would see is on the screen.</summary>
public static class Waiting
{
    // Each poll is a round trip to the phone (~0.2–0.4 s from a cloud session) already: a short pause between them.
    private static readonly TimeSpan Poll = TimeSpan.FromMilliseconds(100);

    /// <summary>Waits up to <paramref name="timeout"/> until <paramref name="shown"/> finds an element shown on the screen.</summary>
    public static async Task UntilShownAsync(AndroidDriver driver, By shown, TimeSpan timeout, string what)
    {
        if (await FindWithinAsync(driver, shown, timeout) is null)
        {
            throw new TimeoutException($"{what} wasn't shown within {timeout.TotalSeconds:0} s.");
        }
    }

    /// <summary>Waits up to <paramref name="timeout"/> until <paramref name="condition"/> holds on the screen.</summary>
    public static async Task UntilAsync(Func<bool> condition, TimeSpan timeout, string what)
    {
        var until = DateTime.UtcNow + timeout;
        while (!await Task.Run(() => Holds(condition)))
        {
            if (DateTime.UtcNow >= until)
            {
                throw new TimeoutException($"{what} didn't happen within {timeout.TotalSeconds:0} s.");
            }

            await Task.Delay(Poll);
        }
    }

    /// <summary>The first element shown that <paramref name="shown"/> finds within <paramref name="timeout"/>, or null.</summary>
    public static async Task<IWebElement?> FindWithinAsync(AndroidDriver driver, By shown, TimeSpan timeout)
    {
        var until = DateTime.UtcNow + timeout;
        do
        {
            var found = await Task.Run(() => FindShown(driver, shown));
            if (found is not null)
            {
                return found;
            }

            await Task.Delay(Poll);
        }
        while (DateTime.UtcNow < until);

        return null;
    }

    // UiAutomator2 finds only views on the screen (its allowInvisibleElements setting is off), so a view found is shown:
    // no second round trip to ask. A view that goes stale while it's read (a list or a web page redrawing) isn't shown
    // yet: the next poll finds the new one.
    private static IWebElement? FindShown(AndroidDriver driver, By shown)
    {
        try
        {
            return driver.FindElements(shown).FirstOrDefault();
        }
        catch (StaleElementReferenceException)
        {
            return null;
        }
    }

    // A view that goes stale while the condition reads it (a web page redrawing) doesn't hold it yet.
    private static bool Holds(Func<bool> condition)
    {
        try
        {
            return condition();
        }
        catch (StaleElementReferenceException)
        {
            return false;
        }
    }
}
