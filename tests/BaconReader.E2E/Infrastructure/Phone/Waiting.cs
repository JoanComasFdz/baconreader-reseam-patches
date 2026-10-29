// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>The page objects' waits: until something the user would see is on the screen.</summary>
public static class Waiting
{
    private static readonly TimeSpan Poll = TimeSpan.FromMilliseconds(250);

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

    /// <summary>
    /// For a view only some devices show (a Gherkin's "(optional)" step): waits until <paramref name="shown"/> finds an
    /// element shown on the screen (true), or until <paramref name="otherwise"/> has held for <paramref name="settle"/>
    /// without it (false). Fails when neither happens within <paramref name="timeout"/>.
    /// </summary>
    public static async Task<bool> UntilShownOrSettledAsync(
        AndroidDriver driver, By shown, Func<bool> otherwise, TimeSpan settle, TimeSpan timeout, string what)
    {
        var until = DateTime.UtcNow + timeout;
        DateTime? holdsSince = null;
        while (DateTime.UtcNow < until)
        {
            if (await Task.Run(() => FindShown(driver, shown)) is not null)
            {
                return true;
            }

            if (!await Task.Run(() => Holds(otherwise)))
            {
                holdsSince = null;
            }
            else if (DateTime.UtcNow - (holdsSince ??= DateTime.UtcNow) >= settle)
            {
                return false;
            }

            await Task.Delay(Poll);
        }

        throw new TimeoutException($"{what} wasn't shown within {timeout.TotalSeconds:0} s.");
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

    // A view that goes stale while it's read (a list or a web page redrawing) isn't shown yet: the next poll finds the new one.
    private static IWebElement? FindShown(AndroidDriver driver, By shown)
    {
        try
        {
            return driver.FindElements(shown).FirstOrDefault(element => element.Displayed);
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
