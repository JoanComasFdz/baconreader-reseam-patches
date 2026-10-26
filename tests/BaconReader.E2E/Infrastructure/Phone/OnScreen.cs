// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Diagnostics;
using System.Runtime.CompilerServices;
using OpenQA.Selenium;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>
/// Every Act and Read member of a page object goes through here (E2E-TEST-CODE-RULES.md, rule c), so the video shows
/// what the scenario does, one step at a time: when the run records, a step starts no sooner than
/// <see cref="E2EDevice.ActionGap"/> after the previous one ended (record.sh <c>--action-gap</c>), so the GIF doesn't tap
/// faster than the eye follows. Android draws each touch itself while recording (<c>show_touches</c>,
/// <see cref="Device.ScreenRecording"/>), so no pointer is drawn here. Without a recording it goes on at once. Waits don't
/// go through it.
/// </summary>
public static class OnScreen
{
    // When each driver's last step ended (one driver per scenario, its steps one after another).
    private static readonly ConditionalWeakTable<AndroidDriver, Stopwatch> SinceLastStep = [];

    private static bool Recording => E2EDevice.VideoDirectory is not null;

    /// <summary>Taps the one element <paramref name="target"/> finds.</summary>
    public static Task PressAsync(AndroidDriver driver, By target) => ActAsync(driver, target, element => element.Click());

    /// <summary>Does <paramref name="act"/> on the one element <paramref name="target"/> finds.</summary>
    public static async Task ActAsync(AndroidDriver driver, By target, Action<IWebElement> act)
    {
        await WaitForActionGapAsync(driver);
        await Task.Run(() => act(driver.FindElement(target)));
        NoteStepEnded(driver);
    }

    /// <summary>Does <paramref name="act"/>, an action on the device rather than on a view (opening an app).</summary>
    public static async Task DoAsync(AndroidDriver driver, Action act)
    {
        await WaitForActionGapAsync(driver);
        await Task.Run(act);
        NoteStepEnded(driver);
    }

    /// <summary>
    /// Scrolls <paramref name="scrollable"/> down, a screen at a time, until <paramref name="target"/> is on the screen (a
    /// button at the end of a web page).
    /// </summary>
    public static async Task ScrollToAsync(AndroidDriver driver, By scrollable, By target, string what)
    {
        await WaitForActionGapAsync(driver);
        for (var screens = 0; !await Task.Run(() => IsOnScreen(driver, target)); screens++)
        {
            if (screens == 10)
            {
                throw new InvalidOperationException($"{what} wasn't on the screen after scrolling 10 screens down.");
            }

            await Task.Run(() => driver.ExecuteScript("mobile: scrollGesture", new Dictionary<string, object>
            {
                ["elementId"] = ((OpenQA.Selenium.Appium.AppiumElement)driver.FindElement(scrollable)).Id,
                ["direction"] = "down",
                ["percent"] = 0.75,
            }));
        }

        NoteStepEnded(driver);
    }

    /// <summary>Reads what the screen shows with <paramref name="read"/>.</summary>
    public static async Task<T> ReadAsync<T>(AndroidDriver driver, Func<T> read)
    {
        await WaitForActionGapAsync(driver);
        var shown = await Task.Run(read);
        NoteStepEnded(driver);
        return shown;
    }

    private static async Task WaitForActionGapAsync(AndroidDriver driver)
    {
        if (Recording && SinceLastStep.TryGetValue(driver, out var since) && since.Elapsed < E2EDevice.ActionGap)
        {
            await Task.Delay(E2EDevice.ActionGap - since.Elapsed);
        }
    }

    private static bool IsOnScreen(AndroidDriver driver, By target) =>
        driver.FindElements(target).Any(element => element.Displayed && element.Size.Height > 0);

    private static void NoteStepEnded(AndroidDriver driver) => SinceLastStep.AddOrUpdate(driver, Stopwatch.StartNew());
}
