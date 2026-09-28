package com.locationjoystick.feature.widget.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSidePopupOffsetTest {
    @Test
    fun `places popup to the right of the icon in parent-window space`() {
        assertEquals(
            50 to 250,
            widgetSidePopupOffset(
                parentXOnScreen = 10,
                parentYOnScreen = 180,
                anchorLeft = 0,
                anchorTop = 250,
                anchorRight = 50,
                popupWidth = 200,
                popupHeight = 50,
                screenWidth = 1080,
                screenHeight = 1920,
            ),
        )
    }

    @Test
    fun `flips popup to the left when the widget is docked on the right edge`() {
        assertEquals(
            -200 to 250,
            widgetSidePopupOffset(
                parentXOnScreen = 1030,
                parentYOnScreen = 400,
                anchorLeft = 0,
                anchorTop = 250,
                anchorRight = 50,
                popupWidth = 200,
                popupHeight = 50,
                screenWidth = 1080,
                screenHeight = 1920,
            ),
        )
    }

    @Test
    fun `shifts popup up when it would overflow the bottom of the screen`() {
        assertEquals(
            50 to 70,
            widgetSidePopupOffset(
                parentXOnScreen = 0,
                parentYOnScreen = 1800,
                anchorLeft = 0,
                anchorTop = 200,
                anchorRight = 50,
                popupWidth = 100,
                popupHeight = 50,
                screenWidth = 1080,
                screenHeight = 1920,
            ),
        )
    }

    // The debug stats readout (issue #104) has no anchor icon, so its Box collapses to zero
    // width. These pin that case plus the much wider popup content it carries.

    @Test
    fun `keeps a zero-width anchor flush with the icon column`() {
        assertEquals(
            0 to 900,
            widgetSidePopupOffset(
                parentXOnScreen = 0,
                parentYOnScreen = 300,
                anchorLeft = 0,
                anchorTop = 900,
                anchorRight = 0,
                popupWidth = 722,
                popupHeight = 150,
                screenWidth = 1440,
                screenHeight = 3168,
            ),
        )
    }

    @Test
    fun `flips a wide readout left when the widget is docked on the right edge`() {
        assertEquals(
            -722 to 900,
            widgetSidePopupOffset(
                parentXOnScreen = 1200,
                parentYOnScreen = 300,
                anchorLeft = 0,
                anchorTop = 900,
                anchorRight = 0,
                popupWidth = 722,
                popupHeight = 150,
                screenWidth = 1440,
                screenHeight = 3168,
            ),
        )
    }

    @Test
    fun `shifts a zero-width anchor up when the readout would overflow the bottom`() {
        assertEquals(
            0 to 850,
            widgetSidePopupOffset(
                parentXOnScreen = 0,
                parentYOnScreen = 2168,
                anchorLeft = 0,
                anchorTop = 900,
                anchorRight = 0,
                popupWidth = 722,
                popupHeight = 150,
                screenWidth = 1440,
                screenHeight = 3168,
            ),
        )
    }
}
