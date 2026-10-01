package com.iiankehn.slater2.ui

import com.iiankehn.slater2.model.InputModality
import com.iiankehn.slater2.model.R2FormFactor
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceConfigurationTest {
    @Test
    fun phoneUsesCompactWorkspace() {
        val workspace = workspaceConfiguration(widthDp = 412)

        assertEquals(R2FormFactor.Phone, workspace.formFactor)
        assertEquals(NavigationPresentation.DestinationScreen, workspace.navigation)
        assertEquals(ToolbarPresentation.CompactDock, workspace.toolbar)
        assertEquals(PageScaleMode.FitWidth, workspace.pageScaleMode)
    }

    @Test
    fun googlebookWithDesktopInputUsesFullWorkspace() {
        val workspace = workspaceConfiguration(
            widthDp = 1366,
            isGooglebookAndroid = true,
            inputs = setOf(InputModality.HardwareKeyboard, InputModality.MouseTrackpad),
        )

        assertEquals(R2FormFactor.GooglebookAndroid, workspace.formFactor)
        assertEquals(NavigationPresentation.PersistentPanel, workspace.navigation)
        assertEquals(InspectorPresentation.PersistentPanel, workspace.inspector)
        assertEquals(ToolbarPresentation.FullRibbon, workspace.toolbar)
        assertEquals(PageScaleMode.ActualSize, workspace.pageScaleMode)
    }

    @Test
    fun foldableIdentityDoesNotDependOnWidthGuessing() {
        val workspace = workspaceConfiguration(widthDp = 700, isFoldable = true)

        assertEquals(R2FormFactor.Foldable, workspace.formFactor)
        assertEquals(NavigationPresentation.PersistentRail, workspace.navigation)
    }
}
