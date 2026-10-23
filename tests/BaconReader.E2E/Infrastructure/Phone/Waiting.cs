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

    /// <summary>The first element shown that <paramref name="shown"/> finds within <paramref name="timeout"/>, or null.</summary>
    public static async Task<IWebElement?> FindWithinAsync(AndroidDriver driver, By shown, TimeSpan timeout)
    {
        var until = DateTime.UtcNow + timeout;
        do
        {
            var found = await Task.Run(() => driver.FindElements(shown).FirstOrDefault(element => element.Displayed));
            if (found is not null)
            {
                return found;
            }

            await Task.Delay(Poll);
        }
        while (DateTime.UtcNow < until);

        return null;
    }
}
