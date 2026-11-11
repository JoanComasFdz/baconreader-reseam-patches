// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Drawing;
using OpenQA.Selenium.Appium.Android;
using OpenQA.Selenium.Interactions;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>
/// The gestures on an album's image or video, a Reddit gallery's or an Imgur album's ("Swipe through albums"): a quick
/// swipe across it, from near one side to near the other, and a pinch that zooms in on it. Both go through
/// <see cref="OnScreen"/>, so the recording draws a ring following each finger.
/// </summary>
internal static class AlbumGestures
{
    private const int SwipeMoves = 6;

    /// <summary>
    /// Swipes across <paramref name="media"/>, at its middle: toward its left side (<paramref name="toTheLeft"/>), which
    /// goes to the next image, or toward its right, which goes to the one before.
    /// </summary>
    public static void Swipe(AndroidDriver driver, Rectangle media, bool toTheLeft)
    {
        var y = media.Top + (media.Height / 2);
        var near = media.Left + (media.Width / 6);
        var far = media.Right - (media.Width / 6);
        var (from, to) = toTheLeft ? (far, near) : (near, far);
        var finger = new PointerInputDevice(PointerKind.Touch, "finger");
        var swipe = new ActionSequence(finger, 0);
        swipe.AddAction(finger.CreatePointerMove(CoordinateOrigin.Viewport, from, y, TimeSpan.Zero));
        swipe.AddAction(finger.CreatePointerDown(MouseButton.Touch));
        // In small moves from the start, as a finger moves: a finger that stays put is a long press, which on an Imgur
        // album's video asks to download it.
        for (var move = 1; move <= SwipeMoves; move++)
        {
            swipe.AddAction(finger.CreatePointerMove(CoordinateOrigin.Viewport, from + ((to - from) * move / SwipeMoves), y, TimeSpan.FromMilliseconds(30)));
        }

        swipe.AddAction(finger.CreatePointerUp(MouseButton.Touch));
        OnScreen.PerformSwipe(driver, swipe, new Point(from, y), new Point(to, y));
    }

    /// <summary>Zooms in on <paramref name="media"/>: two fingers at its middle move apart, along its width.</summary>
    public static void ZoomIn(AndroidDriver driver, Rectangle media)
    {
        var x = media.Left + (media.Width / 2);
        var y = media.Top + (media.Height / 2);
        var start = media.Width / 12;
        var end = media.Width / 3;
        var (first, firstPath) = Finger("first", new Point(x - start, y), new Point(x - end, y));
        var (second, secondPath) = Finger("second", new Point(x + start, y), new Point(x + end, y));
        OnScreen.PerformPinch(driver, first, firstPath, second, secondPath);
    }

    private static (ActionSequence Moves, (Point From, Point To) Path) Finger(string name, Point from, Point to)
    {
        var finger = new PointerInputDevice(PointerKind.Touch, name);
        var moves = new ActionSequence(finger, 0);
        moves.AddAction(finger.CreatePointerMove(CoordinateOrigin.Viewport, from.X, from.Y, TimeSpan.Zero));
        moves.AddAction(finger.CreatePointerDown(MouseButton.Touch));
        moves.AddAction(finger.CreatePointerMove(CoordinateOrigin.Viewport, to.X, to.Y, TimeSpan.FromMilliseconds(500)));
        moves.AddAction(finger.CreatePointerUp(MouseButton.Touch));
        return (moves, (from, to));
    }
}
