package ai.bureadcar.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The Figma "Icons · 48dp" set: 24 grid, 2px round outline, playback icons filled. Tint with Icon(tint). */
object PleosIcons {
    val play: ImageVector by lazy {
        icon(
            "play",
            fill("M8 5.6v12.8a1 1 0 0 0 1.52.85l10.4-6.4a1 1 0 0 0 0-1.7L9.52 4.75A1 1 0 0 0 8 5.6z"),
        )
    }
    val pause: ImageVector by lazy {
        icon(
            "pause",
            fill("M7.2 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1 -1.2 1.2h-1.6a1.2 1.2 0 0 1 -1.2 -1.2v-11.6a1.2 1.2 0 0 1 1.2 -1.2z"),
            fill("M15.2 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1 -1.2 1.2h-1.6a1.2 1.2 0 0 1 -1.2 -1.2v-11.6a1.2 1.2 0 0 1 1.2 -1.2z"),
        )
    }
    val skipNext: ImageVector by lazy {
        icon(
            "skip_next",
            fill("M5.5 6.6v10.8a1 1 0 0 0 1.55.83l8.1-5.4a1 1 0 0 0 0-1.66l-8.1-5.4A1 1 0 0 0 5.5 6.6z"),
            fill("M17.75 5h0a1.25 1.25 0 0 1 1.25 1.25v11.5a1.25 1.25 0 0 1 -1.25 1.25h-0a1.25 1.25 0 0 1 -1.25 -1.25v-11.5a1.25 1.25 0 0 1 1.25 -1.25z"),
        )
    }
    val replay: ImageVector by lazy {
        icon(
            "replay",
            stroke("M4.5 12a7.5 7.5 0 1 0 2.2-5.3"),
            stroke("M4.5 4.5v4h4"),
        )
    }
    val close: ImageVector by lazy {
        icon(
            "close",
            stroke("M6 6l12 12M18 6L6 18"),
        )
    }
    val schedule: ImageVector by lazy {
        icon(
            "schedule",
            stroke("M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1 -2.5 2.5h-12a2.5 2.5 0 0 1 -2.5 -2.5v-10a2.5 2.5 0 0 1 2.5 -2.5z"),
            stroke("M3.5 10h17M8 3v4M16 3v4M8 14h2M14 14h2M8 17h2"),
        )
    }
    val settings: ImageVector by lazy {
        icon(
            "settings",
            stroke("M4 7h9M17 7h3M4 17h3M11 17h9"),
            stroke("M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"),
            stroke("M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"),
        )
    }
    val back: ImageVector by lazy {
        icon(
            "back",
            stroke("M15 5l-7 7 7 7"),
        )
    }
    val add: ImageVector by lazy {
        icon(
            "add",
            stroke("M12 5v14M5 12h14"),
        )
    }
    val check: ImageVector by lazy {
        icon(
            "check",
            stroke("M5 12.5l4.5 4.5L19 7.5"),
        )
    }
    val chevronRight: ImageVector by lazy {
        icon(
            "chevron_right",
            stroke("M9 5l7 7-7 7"),
        )
    }
    val chevronDown: ImageVector by lazy {
        icon(
            "chevron_down",
            stroke("M5 9l7 7 7-7"),
        )
    }
    val route: ImageVector by lazy {
        icon(
            "route",
            stroke("M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z"),
            stroke("M9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"),
        )
    }
    val bookmark: ImageVector by lazy {
        icon(
            "bookmark",
            stroke("M7 4h10a1 1 0 0 1 1 1v15l-6-4-6 4V5a1 1 0 0 1 1-1z"),
        )
    }
    val lock: ImageVector by lazy {
        icon(
            "lock",
            stroke("M7 10.5h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z"),
            stroke("M8 10.5V8a4 4 0 0 1 8 0v2.5"),
        )
    }
    val info: ImageVector by lazy {
        icon(
            "info",
            stroke("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"),
            stroke("M12 11v5.5M12 7.6v.4"),
        )
    }
    val car: ImageVector by lazy {
        icon(
            "car",
            stroke("M5 12l1.7-4.4A2 2 0 0 1 8.6 6.3h6.8a2 2 0 0 1 1.9 1.3L19 12"),
            stroke("M5.5 12h13a1.5 1.5 0 0 1 1.5 1.5v2.5a1.5 1.5 0 0 1 -1.5 1.5h-13a1.5 1.5 0 0 1 -1.5 -1.5v-2.5a1.5 1.5 0 0 1 1.5 -1.5z"),
            stroke("M6.5 17.5V19.5M17.5 17.5V19.5M7.5 14.75h1M15.5 14.75h1"),
        )
    }
    val broadcast: ImageVector by lazy {
        icon(
            "broadcast",
            stroke("M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"),
            stroke("M8.5 15.5a5 5 0 0 1 0-7M15.5 8.5a5 5 0 0 1 0 7M5.6 18.4a9 9 0 0 1 0-12.8M18.4 5.6a9 9 0 0 1 0 12.8"),
        )
    }
    val drag: ImageVector by lazy {
        icon(
            "drag",
            fill("M7.6 6a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0"),
            fill("M13.6 6a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0"),
            fill("M7.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0"),
            fill("M13.6 12a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0"),
            fill("M7.6 18a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0"),
            fill("M13.6 18a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0"),
        )
    }
    val catSubscription: ImageVector by lazy {
        icon(
            "cat_subscription",
            stroke("M14 3.5H7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8.5z"),
            stroke("M14 3.5v5h5M8.5 13h7M8.5 16.5h4.5"),
        )
    }
    val catPriceIndex: ImageVector by lazy {
        icon(
            "cat_price_index",
            stroke("M3.5 17l6-6 4 4 7-7.5"),
            stroke("M15 7.5h5.5V13"),
        )
    }
    val catTrade: ImageVector by lazy {
        icon(
            "cat_trade",
            stroke("M3.5 12.6V5A1.5 1.5 0 0 1 5 3.5h7.6a1.5 1.5 0 0 1 1.06.44l7 7a1.5 1.5 0 0 1 0 2.12l-7.6 7.6a1.5 1.5 0 0 1-2.12 0l-7-7a1.5 1.5 0 0 1-.44-1.06z"),
            stroke("M6.5 8a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0"),
        )
    }
    val catRent: ImageVector by lazy {
        icon(
            "cat_rent",
            stroke("M3.5 15.5a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0"),
            stroke("M11.2 12.3L20 3.5M16.5 7l2.5 2.5M14 9.5l2 2"),
        )
    }
    val catTransport: ImageVector by lazy {
        icon(
            "cat_transport",
            stroke("M8 3.5h8a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-8a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z"),
            stroke("M5 10.5h14M9 20.5l-2-4M15 20.5l2-4M8.5 13.5h.5M15 13.5h.5"),
        )
    }
    val catDevelopment: ImageVector by lazy {
        icon(
            "cat_development",
            stroke("M3.5 20.5h17M5.5 20.5V10l5-3v13.5M10.5 20.5V4.5l8 4v12M13.5 10.5h2M13.5 14h2M13.5 17.5h2"),
        )
    }
    val catRedevelopment: ImageVector by lazy {
        icon(
            "cat_redevelopment",
            stroke("M6.5 20.5V4l13 3.5M6.5 8.5l4-4.5M17 7.2v3.8M3.5 20.5h7"),
            stroke("M15.6 11h2.8a0.6 0.6 0 0 1 0.6 0.6v2.3a0.6 0.6 0 0 1 -0.6 0.6h-2.8a0.6 0.6 0 0 1 -0.6 -0.6v-2.3a0.6 0.6 0 0 1 0.6 -0.6z"),
        )
    }
    val catPolicy: ImageVector by lazy {
        icon(
            "cat_policy",
            stroke("M3.5 9.5L12 4l8.5 5.5M5 20h14M3.5 20.5h17M6.5 11v6.5M10.5 11v6.5M13.5 11v6.5M17.5 11v6.5"),
        )
    }
    val catRate: ImageVector by lazy {
        icon(
            "cat_rate",
            stroke("M18.5 5.5l-13 13"),
            stroke("M5 7.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"),
            stroke("M14 16.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"),
        )
    }
    val catUnsold: ImageVector by lazy {
        icon(
            "cat_unsold",
            stroke("M6.5 3.5h11a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1 -1.5 1.5h-11a1.5 1.5 0 0 1 -1.5 -1.5v-14a1.5 1.5 0 0 1 1.5 -1.5z"),
            stroke("M9 7.5h1.5M13.5 7.5H15M9 11.5h1.5M13.5 11.5H15M10 20.5v-4h4v4"),
        )
    }
}

private class Part(val stroke: Boolean, val data: String)

private fun stroke(data: String) = Part(true, data)

private fun fill(data: String) = Part(false, data)

private fun icon(name: String, vararg parts: Part): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        parts.forEach { part ->
            val nodes = addPathNodes(part.data)
            if (part.stroke) {
                addPath(
                    nodes,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            } else {
                addPath(nodes, fill = SolidColor(Color.Black))
            }
        }
    }.build()
