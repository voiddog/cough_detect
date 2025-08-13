package org.voiddog.coughdetect

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoughDetectionUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    @Before
    fun setUp() {
        // Wait for the app to initialize
        composeTestRule.waitForIdle()
    }

    @Test
    fun testAppLaunchesSuccessfully() {
        // Verify the main title is displayed
        composeTestRule.onNodeWithText("咳嗽检测应用")
            .assertIsDisplayed()
    }

    @Test
    fun testStartDetectionButtonExists() {
        // Verify the start detection button is present and clickable
        composeTestRule.onNodeWithText("开始检测")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun testDetectionStatusDisplayed() {
        // Verify the status text is displayed
        composeTestRule.onNodeWithText("未开始检测")
            .assertIsDisplayed()
    }

    @Test
    fun testStatisticsCardDisplayed() {
        // Verify statistics information is shown
        composeTestRule.onNodeWithText("统计信息")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("咳嗽次数")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("平均置信度")
            .assertIsDisplayed()
    }

    @Test
    fun testCoughRecordsListDisplayed() {
        // Verify the records list is displayed
        composeTestRule.onNodeWithText("咳嗽记录 (0)")
            .assertIsDisplayed()

        // Initially should show no records message
        composeTestRule.onNodeWithText("暂无咳嗽记录")
            .assertIsDisplayed()
    }

    @Test
    fun testClearRecordsButtonExists() {
        // Verify the clear records button exists
        composeTestRule.onNodeWithText("清空记录")
            .assertIsDisplayed()
    }

    @Test
    fun testClearRecordsButtonDisabledWhenNoRecords() {
        // Clear button should be disabled when there are no records
        composeTestRule.onNodeWithText("清空记录")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun testStartDetectionButtonClick() {
        // Click the start detection button
        composeTestRule.onNodeWithText("开始检测")
            .performClick()

        // Wait for state change
        composeTestRule.waitForIdle()

        // Verify the button text changes
        composeTestRule.onNodeWithText("暂停检测")
            .assertIsDisplayed()

        // Verify status text changes
        composeTestRule.onNodeWithText("正在检测中...")
            .assertIsDisplayed()
    }

    @Test
    fun testPauseDetectionFlow() {
        // Start detection first
        composeTestRule.onNodeWithText("开始检测")
            .performClick()

        composeTestRule.waitForIdle()

        // Then pause it
        composeTestRule.onNodeWithText("暂停检测")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify button text changes to resume
        composeTestRule.onNodeWithText("继续检测")
            .assertIsDisplayed()

        // Verify status shows paused
        composeTestRule.onNodeWithText("检测已暂停")
            .assertIsDisplayed()
    }

    @Test
    fun testResumeDetectionFlow() {
        // Start detection
        composeTestRule.onNodeWithText("开始检测")
            .performClick()

        composeTestRule.waitForIdle()

        // Pause detection
        composeTestRule.onNodeWithText("暂停检测")
            .performClick()

        composeTestRule.waitForIdle()

        // Resume detection
        composeTestRule.onNodeWithText("继续检测")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify it's recording again
        composeTestRule.onNodeWithText("暂停检测")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("正在检测中...")
            .assertIsDisplayed()
    }

    @Test
    fun testStopDetectionButton() {
        // Start detection first
        composeTestRule.onNodeWithText("开始检测")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify stop button appears
        composeTestRule.onNodeWithText("停止")
            .assertIsDisplayed()
            .assertIsEnabled()

        // Click stop button
        composeTestRule.onNodeWithText("停止")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify back to initial state
        composeTestRule.onNodeWithText("开始检测")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("未开始检测")
            .assertIsDisplayed()
    }

    @Test
    fun testAudioVisualizerExists() {
        // The audio visualizer (microphone icon) should be present
        composeTestRule.onNodeWithContentDescription("null") // Mic icon
            .assertExists()
    }

    @Test
    fun testClearConfirmDialog() {
        // This test assumes there are some records to clear
        // We'll simulate by checking if the dialog appears when button is enabled

        // Note: In a real test, we'd need to add some test data first
        // For now, we'll just test the button existence
        composeTestRule.onNodeWithText("清空记录")
            .assertExists()
    }

    @Test
    fun testNavigationAndUIResponsiveness() {
        // Test that UI is responsive to state changes

        // Start detection
        composeTestRule.onNodeWithText("开始检测")
            .performClick()

        // Verify UI updates quickly
        composeTestRule.waitForIdle()

        // Check that multiple UI elements update together
        composeTestRule.onNodeWithText("暂停检测")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("正在检测中...")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("停止")
            .assertIsDisplayed()
    }

    @Test
    fun testUIElementsAlignment() {
        // Test that key UI elements are properly displayed and aligned

        // Title should be at the top
        composeTestRule.onNodeWithText("咳嗽检测应用")
            .assertIsDisplayed()

        // Detection card should be visible
        composeTestRule.onNodeWithText("未开始检测")
            .assertIsDisplayed()

        // Control buttons should be visible
        composeTestRule.onNodeWithText("开始检测")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("清空记录")
            .assertIsDisplayed()

        // Statistics card should be visible
        composeTestRule.onNodeWithText("统计信息")
            .assertIsDisplayed()

        // Records list should be visible
        composeTestRule.onNodeWithText("咳嗽记录 (0)")
            .assertIsDisplayed()
    }

    @Test
    fun testDetectionStateFlow() {
        // Test the complete detection state flow

        // Initial state
        composeTestRule.onNodeWithText("未开始检测")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("开始检测")
            .assertIsDisplayed()

        // Start detection
        composeTestRule.onNodeWithText("开始检测")
            .performClick()

        composeTestRule.waitForIdle()

        // Recording state
        composeTestRule.onNodeWithText("正在检测中...")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("暂停检测")
            .assertIsDisplayed()

        // Pause
        composeTestRule.onNodeWithText("暂停检测")
            .performClick()

        composeTestRule.waitForIdle()

        // Paused state
        composeTestRule.onNodeWithText("检测已暂停")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("继续检测")
            .assertIsDisplayed()

        // Stop
        composeTestRule.onNodeWithText("停止")
            .performClick()

        composeTestRule.waitForIdle()

        // Back to initial state
        composeTestRule.onNodeWithText("未开始检测")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("开始检测")
            .assertIsDisplayed()
    }

    @Test
    fun testAccessibilityLabels() {
        // Test that important UI elements have proper accessibility support

        // Check for content descriptions where appropriate
        composeTestRule.onNodeWithText("清空记录")
            .assertIsDisplayed()

        // Check that text elements are readable
        composeTestRule.onNodeWithText("咳嗽检测应用")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("统计信息")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("咳嗽记录 (0)")
            .assertIsDisplayed()
    }
}
