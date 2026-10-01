package com.iiankehn.slater2.ui

import com.iiankehn.slater2.model.InputModality
import com.iiankehn.slater2.model.R2FormFactor

data class WorkspaceConfiguration(
    val formFactor: R2FormFactor,
    val navigation: NavigationPresentation,
    val inspector: InspectorPresentation,
    val toolbar: ToolbarPresentation,
    val pageScaleMode: PageScaleMode,
)

enum class NavigationPresentation { DestinationScreen, PersistentRail, PersistentPanel }

enum class InspectorPresentation { ModalSheet, CollapsiblePanel, PersistentPanel }

enum class ToolbarPresentation { CompactDock, ScrollableRibbon, FullRibbon }

enum class PageScaleMode { FitWidth, FitPage, ActualSize }

/**
 * Produces deterministic workspace behavior from window width and available input hardware.
 * The UI can react to a keyboard or pointing device without coupling document state to Android.
 */
fun workspaceConfiguration(
    widthDp: Int,
    isFoldable: Boolean = false,
    isGooglebookAndroid: Boolean = false,
    inputs: Set<InputModality> = setOf(InputModality.Touch),
): WorkspaceConfiguration {
    require(widthDp > 0) { "Workspace width must be positive." }

    val hasDesktopInput = InputModality.HardwareKeyboard in inputs ||
        InputModality.MouseTrackpad in inputs

    val formFactor = when {
        isGooglebookAndroid -> R2FormFactor.GooglebookAndroid
        isFoldable -> R2FormFactor.Foldable
        widthDp >= 600 -> R2FormFactor.Tablet
        else -> R2FormFactor.Phone
    }

    return when {
        widthDp >= 1200 -> WorkspaceConfiguration(
            formFactor = formFactor,
            navigation = NavigationPresentation.PersistentPanel,
            inspector = InspectorPresentation.PersistentPanel,
            toolbar = ToolbarPresentation.FullRibbon,
            pageScaleMode = if (hasDesktopInput) PageScaleMode.ActualSize else PageScaleMode.FitPage,
        )

        widthDp >= 840 -> WorkspaceConfiguration(
            formFactor = formFactor,
            navigation = NavigationPresentation.PersistentRail,
            inspector = InspectorPresentation.CollapsiblePanel,
            toolbar = ToolbarPresentation.ScrollableRibbon,
            pageScaleMode = PageScaleMode.FitPage,
        )

        widthDp >= 600 -> WorkspaceConfiguration(
            formFactor = formFactor,
            navigation = NavigationPresentation.PersistentRail,
            inspector = InspectorPresentation.ModalSheet,
            toolbar = ToolbarPresentation.ScrollableRibbon,
            pageScaleMode = PageScaleMode.FitWidth,
        )

        else -> WorkspaceConfiguration(
            formFactor = formFactor,
            navigation = NavigationPresentation.DestinationScreen,
            inspector = InspectorPresentation.ModalSheet,
            toolbar = ToolbarPresentation.CompactDock,
            pageScaleMode = PageScaleMode.FitWidth,
        )
    }
}
