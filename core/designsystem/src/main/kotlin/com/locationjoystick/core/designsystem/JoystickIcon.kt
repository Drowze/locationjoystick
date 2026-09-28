/*
 * Material Symbols joystick icon by Google.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Source: https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/joystick.kt
 * Query: var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50
 * Adapted to the app's package and lazy initialization; vector geometry is unchanged.
 */
package com.locationjoystick.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val materialJoystickIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "joystick",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6.8f, 13f)
                lineTo(12f, 16f)
                lineToRelative(5.2f, -3f)
                lineTo(13f, 10.58f)
                verticalLineTo(14f)
                horizontalLineTo(11f)
                verticalLineTo(10.58f)
                lineTo(6.8f, 13f)
                close()
                moveTo(11f, 8.27f)
                verticalLineTo(7.85f)
                quadTo(9.9f, 7.52f, 9.2f, 6.61f)
                reflectiveQuadTo(8.5f, 4.5f)
                quadTo(8.5f, 3.05f, 9.53f, 2.02f)
                reflectiveQuadTo(12f, 1f)
                reflectiveQuadToRelative(2.48f, 1.02f)
                reflectiveQuadTo(15.5f, 4.5f)
                quadToRelative(0f, 1.2f, -0.7f, 2.11f)
                quadTo(14.1f, 7.52f, 13f, 7.85f)
                verticalLineTo(8.27f)
                lineToRelative(7f, 4.03f)
                quadToRelative(0.48f, 0.28f, 0.74f, 0.74f)
                reflectiveQuadTo(21f, 14.05f)
                verticalLineToRelative(1.9f)
                quadToRelative(0f, 0.55f, -0.26f, 1.01f)
                quadTo(20.48f, 17.43f, 20f, 17.7f)
                lineToRelative(-7f, 4.03f)
                quadTo(12.53f, 22f, 12f, 22f)
                reflectiveQuadTo(11f, 21.73f)
                lineTo(4f, 17.7f)
                quadTo(3.53f, 17.43f, 3.26f, 16.96f)
                reflectiveQuadTo(3f, 15.95f)
                verticalLineToRelative(-1.9f)
                quadTo(3f, 13.5f, 3.26f, 13.04f)
                reflectiveQuadTo(4f, 12.3f)
                lineTo(11f, 8.27f)
                close()
                moveToRelative(0f, 9.45f)
                lineTo(5f, 14.27f)
                verticalLineToRelative(1.68f)
                lineTo(12f, 20f)
                lineToRelative(7f, -4.05f)
                verticalLineTo(14.27f)
                lineToRelative(-6f, 3.45f)
                quadTo(12.53f, 18f, 12f, 18f)
                reflectiveQuadTo(11f, 17.73f)
                close()
                moveTo(13.06f, 5.56f)
                quadTo(13.5f, 5.13f, 13.5f, 4.5f)
                reflectiveQuadTo(13.06f, 3.44f)
                reflectiveQuadTo(12f, 3f)
                reflectiveQuadTo(10.94f, 3.44f)
                reflectiveQuadTo(10.5f, 4.5f)
                reflectiveQuadToRelative(0.44f, 1.06f)
                reflectiveQuadTo(12f, 6f)
                reflectiveQuadTo(13.06f, 5.56f)
                close()
                moveTo(12f, 20f)
                close()
            }
        }.build()
}
