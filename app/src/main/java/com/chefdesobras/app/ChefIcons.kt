package com.chefdesobras.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Iconos de Lucide (https://lucide.dev, licencia ISC), convertidos desde lucide-static 0.460.0.
// Archivo generado: el trazo es negro y el color real lo pone el tint de Icon.
object ChefIcons {
    val ChefHat: ImageVector by lazy {
        lucide(
            "ChefHat", 2.2f, false,
            "M 17 21 a 1 1 0 0 0 1 -1 v -5.35 c 0 -0.457 0.316 -0.844 0.727 -1.041 a 4 4 0 0 0 -2.134 -7.589 a 5 5 0 0 0 -9.186 0 a 4 4 0 0 0 -2.134 7.588 c 0.411 0.198 0.727 0.585 0.727 1.041 V 20 a 1 1 0 0 0 1 1 Z",
            "M 6 17 h 12",
        )
    }
    val Sparkles: ImageVector by lazy {
        lucide(
            "Sparkles", 2.2f, false,
            "M 9.937 15.5 A 2 2 0 0 0 8.5 14.063 l -6.135 -1.582 a 0.5 0.5 0 0 1 0 -0.962 L 8.5 9.936 A 2 2 0 0 0 9.937 8.5 l 1.582 -6.135 a 0.5 0.5 0 0 1 0.963 0 L 14.063 8.5 A 2 2 0 0 0 15.5 9.937 l 6.135 1.581 a 0.5 0.5 0 0 1 0 0.964 L 15.5 14.063 a 2 2 0 0 0 -1.437 1.437 l -1.582 6.135 a 0.5 0.5 0 0 1 -0.963 0 z",
            "M 20 3 v 4",
            "M 22 5 h -4",
            "M 4 17 v 2",
            "M 5 18 H 3",
        )
    }
    val Sparkle: ImageVector by lazy {
        lucide(
            "Sparkle", 2.4f, false,
            "M 9.937 15.5 A 2 2 0 0 0 8.5 14.063 l -6.135 -1.582 a 0.5 0.5 0 0 1 0 -0.962 L 8.5 9.936 A 2 2 0 0 0 9.937 8.5 l 1.582 -6.135 a 0.5 0.5 0 0 1 0.963 0 L 14.063 8.5 A 2 2 0 0 0 15.5 9.937 l 6.135 1.581 a 0.5 0.5 0 0 1 0 0.964 L 15.5 14.063 a 2 2 0 0 0 -1.437 1.437 l -1.582 6.135 a 0.5 0.5 0 0 1 -0.963 0 z",
        )
    }
    val Timer: ImageVector by lazy {
        lucide(
            "Timer", 2.4f, false,
            "M 10 2 L 14 2",
            "M 12 14 L 15 11",
            "M 4 14 A 8 8 0 1 0 20 14 A 8 8 0 1 0 4 14 Z",
        )
    }
    val Flame: ImageVector by lazy {
        lucide(
            "Flame", 2.2f, false,
            "M 8.5 14.5 A 2.5 2.5 0 0 0 11 12 c 0 -1.38 -0.5 -2 -1 -3 c -1.072 -2.143 -0.224 -4.054 2 -6 c 0.5 2.5 2 4.9 4 6.5 c 2 1.6 3 3.5 3 5.5 a 7 7 0 1 1 -14 0 c 0 -1.153 0.433 -2.294 1 -3 a 2.5 2.5 0 0 0 2.5 2.5 z",
        )
    }
    val Users: ImageVector by lazy {
        lucide(
            "Users", 2.2f, false,
            "M 16 21 v -2 a 4 4 0 0 0 -4 -4 H 6 a 4 4 0 0 0 -4 4 v 2",
            "M 5 7 A 4 4 0 1 0 13 7 A 4 4 0 1 0 5 7 Z",
            "M 22 21 v -2 a 4 4 0 0 0 -3 -3.87",
            "M 16 3.13 a 4 4 0 0 1 0 7.75",
        )
    }
    val Clock: ImageVector by lazy {
        lucide(
            "Clock", 2.2f, false,
            "M 2 12 A 10 10 0 1 0 22 12 A 10 10 0 1 0 2 12 Z",
            "M 12 6 L 12 12 L 16 14",
        )
    }
    val Mic: ImageVector by lazy {
        lucide(
            "Mic", 2.3f, false,
            "M 12 2 a 3 3 0 0 0 -3 3 v 7 a 3 3 0 0 0 6 0 V 5 a 3 3 0 0 0 -3 -3 Z",
            "M 19 10 v 2 a 7 7 0 0 1 -14 0 v -2",
            "M 12 19 L 12 22",
        )
    }
    val MicOff: ImageVector by lazy {
        lucide(
            "MicOff", 2.3f, false,
            "M 2 2 L 22 22",
            "M 18.89 13.23 A 7.12 7.12 0 0 0 19 12 v -2",
            "M 5 10 v 2 a 7 7 0 0 0 12 5",
            "M 15 9.34 V 5 a 3 3 0 0 0 -5.68 -1.33",
            "M 9 9 v 3 a 3 3 0 0 0 5.12 2.12",
            "M 12 19 L 12 22",
        )
    }
    val Heart: ImageVector by lazy {
        lucide(
            "Heart", 2.2f, false,
            "M 19 14 c 1.49 -1.46 3 -3.21 3 -5.5 A 5.5 5.5 0 0 0 16.5 3 c -1.76 0 -3 0.5 -4.5 2 c -1.5 -1.5 -2.74 -2 -4.5 -2 A 5.5 5.5 0 0 0 2 8.5 c 0 2.3 1.5 4.05 3 5.5 l 7 7 Z",
        )
    }
    val HeartFilled: ImageVector by lazy {
        lucide(
            "HeartFilled", 2.2f, true,
            "M 19 14 c 1.49 -1.46 3 -3.21 3 -5.5 A 5.5 5.5 0 0 0 16.5 3 c -1.76 0 -3 0.5 -4.5 2 c -1.5 -1.5 -2.74 -2 -4.5 -2 A 5.5 5.5 0 0 0 2 8.5 c 0 2.3 1.5 4.05 3 5.5 l 7 7 Z",
        )
    }
    val ArrowLeft: ImageVector by lazy {
        lucide(
            "ArrowLeft", 2.4f, false,
            "m 12 19 l -7 -7 l 7 -7",
            "M 19 12 H 5",
        )
    }
    val Close: ImageVector by lazy {
        lucide(
            "Close", 2.4f, false,
            "M 18 6 L 6 18",
            "m 6 6 l 12 12",
        )
    }
    val Check: ImageVector by lazy {
        lucide(
            "Check", 3.2f, false,
            "M 20 6 L 9 17 l -5 -5",
        )
    }
    val CircleHelp: ImageVector by lazy {
        lucide(
            "CircleHelp", 2.4f, false,
            "M 2 12 A 10 10 0 1 0 22 12 A 10 10 0 1 0 2 12 Z",
            "M 9.09 9 a 3 3 0 0 1 5.83 1 c 0 2 -3 3 -3 3",
            "M 12 17 h 0.01",
        )
    }
    val Swap: ImageVector by lazy {
        lucide(
            "Swap", 2.4f, false,
            "M 8 3 L 4 7 l 4 4",
            "M 4 7 h 16",
            "m 16 21 l 4 -4 l -4 -4",
            "M 20 17 H 4",
        )
    }
    val PlayFilled: ImageVector by lazy {
        lucide(
            "PlayFilled", 2.0f, true,
            "M 6 3 L 20 12 L 6 21 L 6 3 Z",
        )
    }
    val PauseFilled: ImageVector by lazy {
        lucide(
            "PauseFilled", 2.0f, true,
            "M 15 4 H 17 A 1 1 0 0 1 18 5 V 19 A 1 1 0 0 1 17 20 H 15 A 1 1 0 0 1 14 19 V 5 A 1 1 0 0 1 15 4 Z",
            "M 7 4 H 9 A 1 1 0 0 1 10 5 V 19 A 1 1 0 0 1 9 20 H 7 A 1 1 0 0 1 6 19 V 5 A 1 1 0 0 1 7 4 Z",
        )
    }
    val RotateCcw: ImageVector by lazy {
        lucide(
            "RotateCcw", 2.4f, false,
            "M 3 12 a 9 9 0 1 0 9 -9 a 9.75 9.75 0 0 0 -6.74 2.74 L 3 8",
            "M 3 3 v 5 h 5",
        )
    }
    val Minus: ImageVector by lazy {
        lucide(
            "Minus", 2.8f, false,
            "M 5 12 h 14",
        )
    }
    val Plus: ImageVector by lazy {
        lucide(
            "Plus", 2.8f, false,
            "M 5 12 h 14",
            "M 12 5 v 14",
        )
    }
    val ChevronLeft: ImageVector by lazy {
        lucide(
            "ChevronLeft", 2.6f, false,
            "m 15 18 l -6 -6 l 6 -6",
        )
    }
    val ChevronRight: ImageVector by lazy {
        lucide(
            "ChevronRight", 2.6f, false,
            "m 9 18 l 6 -6 l -6 -6",
        )
    }
    val ArrowRight: ImageVector by lazy {
        lucide(
            "ArrowRight", 2.6f, false,
            "M 5 12 h 14",
            "m 12 5 l 7 7 l -7 7",
        )
    }
    val Trash: ImageVector by lazy {
        lucide(
            "Trash", 2.2f, false,
            "M 3 6 h 18",
            "M 19 6 v 14 c 0 1 -1 2 -2 2 H 7 c -1 0 -2 -1 -2 -2 V 6",
            "M 8 6 V 4 c 0 -1 1 -2 2 -2 h 4 c 1 0 2 1 2 2 v 2",
            "M 10 11 L 10 17",
            "M 14 11 L 14 17",
        )
    }
    val Share: ImageVector by lazy {
        lucide(
            "Share", 2.2f, false,
            "M 15 5 A 3 3 0 1 0 21 5 A 3 3 0 1 0 15 5 Z",
            "M 3 12 A 3 3 0 1 0 9 12 A 3 3 0 1 0 3 12 Z",
            "M 15 19 A 3 3 0 1 0 21 19 A 3 3 0 1 0 15 19 Z",
            "M 8.59 13.51 L 15.42 17.49",
            "M 15.41 6.51 L 8.59 10.49",
        )
    }
    val Download: ImageVector by lazy {
        lucide(
            "Download", 2.2f, false,
            "M 21 15 v 4 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 v -4",
            "M 7 10 L 12 15 L 17 10",
            "M 12 15 L 12 3",
        )
    }
    val Refresh: ImageVector by lazy {
        lucide(
            "Refresh", 2.2f, false,
            "M 3 12 a 9 9 0 0 1 9 -9 a 9.75 9.75 0 0 1 6.74 2.74 L 21 8",
            "M 21 3 v 5 h -5",
            "M 21 12 a 9 9 0 0 1 -9 9 a 9.75 9.75 0 0 1 -6.74 -2.74 L 3 16",
            "M 8 16 H 3 v 5",
        )
    }
    val Volume: ImageVector by lazy {
        lucide(
            "Volume", 2.2f, false,
            "M 11 4.702 a 0.705 0.705 0 0 0 -1.203 -0.498 L 6.413 7.587 A 1.4 1.4 0 0 1 5.416 8 H 3 a 1 1 0 0 0 -1 1 v 6 a 1 1 0 0 0 1 1 h 2.416 a 1.4 1.4 0 0 1 0.997 0.413 l 3.383 3.384 A 0.705 0.705 0 0 0 11 19.298 z",
            "M 16 9 a 5 5 0 0 1 0 6",
            "M 19.364 18.364 a 9 9 0 0 0 0 -12.728",
        )
    }
    val CookingPot: ImageVector by lazy {
        lucide(
            "CookingPot", 2.2f, false,
            "M 2 12 h 20",
            "M 20 12 v 8 a 2 2 0 0 1 -2 2 H 6 a 2 2 0 0 1 -2 -2 v -8",
            "m 4 8 l 16 -4",
            "m 8.86 6.78 l -0.45 -1.81 a 2 2 0 0 1 1.45 -2.43 l 1.94 -0.48 a 2 2 0 0 1 2.43 1.46 l 0.45 1.8",
        )
    }
    val Leaf: ImageVector by lazy {
        lucide(
            "Leaf", 2.2f, false,
            "M 11 20 A 7 7 0 0 1 9.8 6.1 C 15.5 5 17 4.48 19 2 c 1 2 2 4.18 2 8 c 0 5.5 -4.78 10 -10 10 Z",
            "M 2 21 c 0 -3 1.85 -5.36 5.08 -6 C 9.5 14.52 12 13 13 12",
        )
    }
    val WheatOff: ImageVector by lazy {
        lucide(
            "WheatOff", 2.2f, false,
            "m 2 22 l 10 -10",
            "m 16 8 l -1.17 1.17",
            "M 3.47 12.53 L 5 11 l 1.53 1.53 a 3.5 3.5 0 0 1 0 4.94 L 5 19 l -1.53 -1.53 a 3.5 3.5 0 0 1 0 -4.94 Z",
            "m 8 8 l -0.53 0.53 a 3.5 3.5 0 0 0 0 4.94 L 9 15 l 1.53 -1.53 c 0.55 -0.55 0.88 -1.25 0.98 -1.97",
            "M 10.91 5.26 c 0.15 -0.26 0.34 -0.51 0.56 -0.73 L 13 3 l 1.53 1.53 a 3.5 3.5 0 0 1 0.28 4.62",
            "M 20 2 h 2 v 2 a 4 4 0 0 1 -4 4 h -2 V 6 a 4 4 0 0 1 4 -4 Z",
            "M 11.47 17.47 L 13 19 l -1.53 1.53 a 3.5 3.5 0 0 1 -4.94 0 L 5 19 l 1.53 -1.53 a 3.5 3.5 0 0 1 4.94 0 Z",
            "m 16 16 l -0.53 0.53 a 3.5 3.5 0 0 1 -4.94 0 L 9 15 l 1.53 -1.53 a 3.49 3.49 0 0 1 1.97 -0.98",
            "M 18.74 13.09 c 0.26 -0.15 0.51 -0.34 0.73 -0.56 L 21 11 l -1.53 -1.53 a 3.5 3.5 0 0 0 -4.62 -0.28",
            "M 2 2 L 22 22",
        )
    }
    val Zap: ImageVector by lazy {
        lucide(
            "Zap", 2.2f, false,
            "M 4 14 a 1 1 0 0 1 -0.78 -1.63 l 9.9 -10.2 a 0.5 0.5 0 0 1 0.86 0.46 l -1.92 6.02 A 1 1 0 0 0 13 10 h 7 a 1 1 0 0 1 0.78 1.63 l -9.9 10.2 a 0.5 0.5 0 0 1 -0.86 -0.46 l 1.92 -6.02 A 1 1 0 0 0 11 14 z",
        )
    }
    val CalendarClock: ImageVector by lazy {
        lucide(
            "CalendarClock", 2.2f, false,
            "M 21 7.5 V 6 a 2 2 0 0 0 -2 -2 H 5 a 2 2 0 0 0 -2 2 v 14 a 2 2 0 0 0 2 2 h 3.5",
            "M 16 2 v 4",
            "M 8 2 v 4",
            "M 3 10 h 5",
            "M 17.5 17.5 L 16 16.3 V 14",
            "M 10 16 A 6 6 0 1 0 22 16 A 6 6 0 1 0 10 16 Z",
        )
    }
    val FolderOpen: ImageVector by lazy {
        lucide(
            "FolderOpen", 2.2f, false,
            "m 6 14 l 1.5 -2.9 A 2 2 0 0 1 9.24 10 H 20 a 2 2 0 0 1 1.94 2.5 l -1.54 6 a 2 2 0 0 1 -1.95 1.5 H 4 a 2 2 0 0 1 -2 -2 V 5 a 2 2 0 0 1 2 -2 h 3.9 a 2 2 0 0 1 1.69 0.9 l 0.81 1.2 a 2 2 0 0 0 1.67 0.9 H 18 a 2 2 0 0 1 2 2 v 2",
        )
    }
    val ListChecks: ImageVector by lazy {
        lucide(
            "ListChecks", 2.2f, false,
            "m 3 17 l 2 2 l 4 -4",
            "m 3 7 l 2 2 l 4 -4",
            "M 13 6 h 8",
            "M 13 12 h 8",
            "M 13 18 h 8",
        )
    }
    val Utensils: ImageVector by lazy {
        lucide(
            "Utensils", 2.2f, false,
            "M 3 2 v 7 c 0 1.1 0.9 2 2 2 h 4 a 2 2 0 0 0 2 -2 V 2",
            "M 7 2 v 20",
            "M 21 15 V 2 a 5 5 0 0 0 -5 5 v 6 c 0 1.1 0.9 2 2 2 h 3 Z m 0 0 v 7",
        )
    }
    val Soup: ImageVector by lazy {
        lucide(
            "Soup", 2.2f, false,
            "M 12 21 a 9 9 0 0 0 9 -9 H 3 a 9 9 0 0 0 9 9 Z",
            "M 7 21 h 10",
            "M 19.5 12 L 22 6",
            "M 16.25 3 c 0.27 0.1 0.8 0.53 0.75 1.36 c -0.06 0.83 -0.93 1.2 -1 2.02 c -0.05 0.78 0.34 1.24 0.73 1.62",
            "M 11.25 3 c 0.27 0.1 0.8 0.53 0.74 1.36 c -0.05 0.83 -0.93 1.2 -0.98 2.02 c -0.06 0.78 0.33 1.24 0.72 1.62",
            "M 6.25 3 c 0.27 0.1 0.8 0.53 0.75 1.36 c -0.06 0.83 -0.93 1.2 -1 2.02 c -0.05 0.78 0.34 1.24 0.74 1.62",
        )
    }
    val Refrigerator: ImageVector by lazy {
        lucide(
            "Refrigerator", 2.2f, false,
            "M 5 6 a 4 4 0 0 1 4 -4 h 6 a 4 4 0 0 1 4 4 v 14 a 2 2 0 0 1 -2 2 H 7 a 2 2 0 0 1 -2 -2 V 6 Z",
            "M 5 10 h 14",
            "M 15 7 v 6",
        )
    }
    val CircleCheck: ImageVector by lazy {
        lucide(
            "CircleCheck", 2.2f, false,
            "M 2 12 A 10 10 0 1 0 22 12 A 10 10 0 1 0 2 12 Z",
            "m 9 12 l 2 2 l 4 -4",
        )
    }
    val Basket: ImageVector by lazy {
        lucide(
            "Basket", 2.2f, false,
            "m 15 11 l -1 9",
            "m 19 11 l -4 -7",
            "M 2 11 h 20",
            "m 3.5 11 l 1.6 7.4 a 2 2 0 0 0 2 1.6 h 9.8 a 2 2 0 0 0 2 -1.6 l 1.7 -7.4",
            "M 4.5 15.5 h 15",
            "m 5 11 l 4 -7",
            "m 9 11 l 1 9",
        )
    }
    val Carrot: ImageVector by lazy {
        lucide(
            "Carrot", 2.2f, false,
            "M 2.27 21.7 s 9.87 -3.5 12.73 -6.36 a 4.5 4.5 0 0 0 -6.36 -6.37 C 5.77 11.84 2.27 21.7 2.27 21.7 z M 8.64 14 l -2.05 -2.04 M 15.34 15 l -2.46 -2.46",
            "M 22 9 s -1.33 -2 -3.5 -2 C 16.86 7 15 9 15 9 s 1.33 2 3.5 2 S 22 9 22 9 z",
            "M 15 2 s -2 1.33 -2 3.5 S 15 9 15 9 s 2 -1.84 2 -3.5 C 17 3.33 15 2 15 2 z",
        )
    }
    val Camera: ImageVector by lazy {
        lucide(
            "Camera", 2.2f, false,
            "M 14.5 4 h -5 L 7 7 H 4 a 2 2 0 0 0 -2 2 v 9 a 2 2 0 0 0 2 2 h 16 a 2 2 0 0 0 2 -2 V 9 a 2 2 0 0 0 -2 -2 h -3 l -2.5 -3 z",
            "M 9 13 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0 Z",
        )
    }
    val Image: ImageVector by lazy {
        lucide(
            "Image", 2.2f, false,
            "M 5 3 h 14 a 2 2 0 0 1 2 2 v 14 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 V 5 a 2 2 0 0 1 2 -2 Z",
            "M 7 9 a 2 2 0 1 0 4 0 a 2 2 0 1 0 -4 0 Z",
            "m 21 15 l -3.086 -3.086 a 2 2 0 0 0 -2.828 0 L 6 21",
        )
    }
    val Lightbulb: ImageVector by lazy {
        lucide(
            "Lightbulb", 2.2f, false,
            "M 15 14 c 0.2 -1 0.7 -1.7 1.5 -2.5 c 1 -0.9 1.5 -2.2 1.5 -3.5 A 6 6 0 0 0 6 8 c 0 1 0.2 2.2 1.5 3.5 c 0.7 0.7 1.3 1.5 1.5 2.5",
            "M 9 18 h 6",
            "M 10 22 h 4",
        )
    }
    val Alert: ImageVector by lazy {
        lucide(
            "Alert", 2.2f, false,
            "m 21.73 18 l -8 -14 a 2 2 0 0 0 -3.48 0 l -8 14 A 2 2 0 0 0 4 21 h 16 a 2 2 0 0 0 1.73 -3",
            "M 12 9 v 4",
            "M 12 17 h 0.01",
        )
    }
    val Info: ImageVector by lazy {
        lucide(
            "Info", 2.2f, false,
            "M 2 12 A 10 10 0 1 0 22 12 A 10 10 0 1 0 2 12 Z",
            "M 12 16 v -4",
            "M 12 8 h 0.01",
        )
    }
    val BellRing: ImageVector by lazy {
        lucide(
            "BellRing", 2.2f, false,
            "M 6 8 a 6 6 0 0 1 12 0 c 0 7 3 9 3 9 H 3 s 3 -2 3 -9",
            "M 10.3 21 a 1.94 1.94 0 0 0 3.4 0",
            "M 4 2 C 2.8 3.7 2 5.7 2 8",
            "M 22 8 c 0 -2.3 -0.8 -4.3 -2 -6",
        )
    }
    val Hourglass: ImageVector by lazy {
        lucide(
            "Hourglass", 2.2f, false,
            "M 5 22 h 14",
            "M 5 2 h 14",
            "M 17 22 v -4.172 a 2 2 0 0 0 -0.586 -1.414 L 12 12 l -4.414 4.414 A 2 2 0 0 0 7 17.828 V 22",
            "M 7 2 v 4.172 a 2 2 0 0 0 0.586 1.414 L 12 12 l 4.414 -4.414 A 2 2 0 0 0 17 6.172 V 2",
        )
    }
    val FileDown: ImageVector by lazy {
        lucide(
            "FileDown", 2.2f, false,
            "M 15 2 H 6 a 2 2 0 0 0 -2 2 v 16 a 2 2 0 0 0 2 2 h 12 a 2 2 0 0 0 2 -2 V 7 Z",
            "M 14 2 v 4 a 2 2 0 0 0 2 2 h 4",
            "M 12 18 v -6",
            "m 9 15 l 3 3 l 3 -3",
        )
    }
    val Search: ImageVector by lazy {
        lucide(
            "Search", 2.4f, false,
            "M 3 11 A 8 8 0 1 0 19 11 A 8 8 0 1 0 3 11 Z",
            "m 21 21 l -4.3 -4.3",
        )
    }
}

private fun lucide(name: String, strokeWidth: Float, filled: Boolean, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { d ->
        builder.addPath(
            pathData = addPathNodes(d),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}
