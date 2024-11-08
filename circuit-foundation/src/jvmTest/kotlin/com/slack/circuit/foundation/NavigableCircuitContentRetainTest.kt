// Copyright (C) 2024 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuit.foundation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG_BUTTON = "TAG_BUTTON"
private const val TAG_GOTO_BUTTON = "TAG_GOTO_BUTTON"
private const val TAG_POP_BUTTON = "TAG_POP_BUTTON"
private const val TAG_CONDITIONAL_RETAINED = "TAG_CONDITIONAL_RETAINED"
private const val TAG_UI_RETAINED = "TAG_UI_RETAINED"
private const val TAG_PRESENTER_RETAINED = "TAG_PRESENTER_RETAINED"
private const val TAG_STATE = "TAG_STATE"

@RunWith(ComposeUiTestRunner::class)
class NavigableCircuitContentRetainTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val dataSource = DataSource()

  private val circuit =
    Circuit.Builder()
      .addPresenter<ScreenA, ScreenA.State> { _, _, _ -> ScreenAPresenter() }
      .addUi<ScreenA, ScreenA.State> { _, modifier -> ScreenAUi(modifier) }
      .addPresenter<ScreenB, ScreenB.State> { _, _, _ -> ScreenBPresenter(dataSource) }
      .addUi<ScreenB, ScreenB.State> { state, modifier -> ScreenBUi(state, modifier) }
      .addPresenter<ScreenC, ScreenC.State> { _, navigator, _ -> ScreenCPresenter(navigator) }
      .addUi<ScreenC, ScreenC.State> { state, modifier -> ScreenCUi(state, modifier) }
      .addPresenter<ScreenD, ScreenD.State> { _, navigator, _ -> ScreenDPresenter(navigator) }
      .addUi<ScreenD, ScreenD.State> { state, modifier -> ScreenDUi(state, modifier) }
      .build()

  /** test with presentWithLifecycle=true. */
  @Test
  fun test() {
    composeTestRule.run {
      setUpTestContent(circuit, ScreenA)
      waitForIdle()

      onNodeWithTag(TAG_STATE).assertDoesNotExist()
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertDoesNotExist()
      onNodeWithTag(TAG_UI_RETAINED).assertDoesNotExist()

      dataSource.value = 1

      onNodeWithTag(TAG_BUTTON).performClick()

      onNodeWithTag(TAG_STATE).assertTextEquals("1")
      onNodeWithTag(TAG_UI_RETAINED).assertTextEquals("1")
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertTextEquals("1")

      onNodeWithTag(TAG_BUTTON).performClick()

      onNodeWithTag(TAG_STATE).assertDoesNotExist()
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertDoesNotExist()
      onNodeWithTag(TAG_UI_RETAINED).assertDoesNotExist()

      dataSource.value = 2

      onNodeWithTag(TAG_BUTTON).performClick()

      onNodeWithTag(TAG_STATE).assertTextEquals("2")

      // UI's rememberRetained is being reset correctly
      onNodeWithTag(TAG_UI_RETAINED).assertTextEquals("2")

      // presenter's rememberRetained is not being reset.
      // I don't know why it retains previous value.
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertTextEquals("2")
    }
  }

  /** test with presentWithLifecycle=false. */
  @Test
  fun test_withoutLifecycle() {
    composeTestRule.run {
      setUpTestContent(circuit.newBuilder().presentWithLifecycle(false).build(), ScreenA)
      waitForIdle()

      onNodeWithTag(TAG_STATE).assertDoesNotExist()
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertDoesNotExist()
      onNodeWithTag(TAG_UI_RETAINED).assertDoesNotExist()

      dataSource.value = 1

      onNodeWithTag(TAG_BUTTON).performClick()

      onNodeWithTag(TAG_STATE).assertTextEquals("1")
      onNodeWithTag(TAG_UI_RETAINED).assertTextEquals("1")
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertTextEquals("1")

      onNodeWithTag(TAG_BUTTON).performClick()

      onNodeWithTag(TAG_STATE).assertDoesNotExist()
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertDoesNotExist()
      onNodeWithTag(TAG_UI_RETAINED).assertDoesNotExist()

      dataSource.value = 2

      onNodeWithTag(TAG_BUTTON).performClick()

      onNodeWithTag(TAG_STATE).assertTextEquals("2")

      // UI's rememberRetained is being reset correctly
      onNodeWithTag(TAG_UI_RETAINED).assertTextEquals("2")

      // presenter's rememberRetained is being reset correctly
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertTextEquals("2")
    }
  }

  @Test
  fun test_conditional_retain() {
    composeTestRule.run {
      setUpTestContent(circuit.newBuilder().presentWithLifecycle(false).build(), ScreenC)
      waitForIdle()

      onNodeWithTag(TAG_STATE).assertDoesNotExist()
      onNodeWithTag(TAG_PRESENTER_RETAINED).assertDoesNotExist()
      onNodeWithTag(TAG_UI_RETAINED).assertDoesNotExist()

      onNodeWithTag(TAG_BUTTON).performClick()
      waitForIdle()

      onNodeWithTag(TAG_CONDITIONAL_RETAINED).assertTextEquals("1")

      onNodeWithTag(TAG_BUTTON).performClick()

      onNodeWithTag(TAG_GOTO_BUTTON).performClick()
      onNodeWithTag(TAG_POP_BUTTON).performClick()

      onNodeWithTag(TAG_BUTTON).performClick()
      waitForIdle()

      onNodeWithTag(TAG_CONDITIONAL_RETAINED).assertTextEquals("1")
    }
  }

  private fun ComposeContentTestRule.setUpTestContent(circuit: Circuit, screen: Screen): Navigator {
    lateinit var navigator: Navigator
    setContent {
      CircuitCompositionLocals(circuit) {
        val backStack = rememberSaveableBackStack(screen)
        navigator = rememberCircuitNavigator(backStack = backStack, onRootPop = {})
        NavigableCircuitContent(navigator = navigator, backStack = backStack)
      }
    }
    return navigator
  }

  private data object ScreenA : Screen {
    data object State : CircuitUiState
  }

  private class ScreenAPresenter : Presenter<ScreenA.State> {
    @Composable
    override fun present(): ScreenA.State {
      return ScreenA.State
    }
  }

  @Composable
  private fun ScreenAUi(modifier: Modifier = Modifier) {
    Column(modifier) {
      val isChildVisible = remember { mutableStateOf(false) }
      Button(
        modifier = Modifier.testTag(TAG_BUTTON),
        onClick = { isChildVisible.value = !isChildVisible.value },
      ) {
        Text("toggle")
      }
      if (isChildVisible.value) {
        CircuitContent(screen = ScreenB)
      }
    }
  }

  private data object ScreenB : Screen {

    data class State(val count: Int, val retainedCount: Int) : CircuitUiState
  }

  private class ScreenBPresenter(private val source: DataSource) : Presenter<ScreenB.State> {

    @Composable
    override fun present(): ScreenB.State {
      val count = source.fetch()
      val retained = rememberRetained { count }
      return ScreenB.State(count, retained)
    }
  }

  @Composable
  private fun ScreenBUi(state: ScreenB.State, modifier: Modifier = Modifier) {
    Column(modifier) {
      val retained = rememberRetained { state.count }
      Text(text = retained.toString(), modifier = Modifier.testTag(TAG_UI_RETAINED))
      Text(text = state.count.toString(), modifier = Modifier.testTag(TAG_STATE))
      Text(
        text = state.retainedCount.toString(),
        modifier = Modifier.testTag(TAG_PRESENTER_RETAINED),
      )
    }
  }

  private data object ScreenC : Screen {

    data class State(val eventSink: (Event) -> Unit) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
      data class GoTo(val screen: Screen) : Event
    }
  }

  private class ScreenCPresenter(private val navigator: Navigator) : Presenter<ScreenC.State> {
    @Composable
    override fun present(): ScreenC.State {
      return ScreenC.State { event ->
        when (event) {
          is ScreenC.Event.GoTo -> navigator.goTo(event.screen)
        }
      }
    }
  }

  @Composable
  private fun ScreenCUi(state: ScreenC.State, modifier: Modifier = Modifier) {
    Column(modifier) {
      Button(
        modifier = Modifier.testTag(TAG_GOTO_BUTTON),
        onClick = { state.eventSink(ScreenC.Event.GoTo(ScreenD)) },
      ) {
        Text("goto")
      }
      val isVisible = remember { mutableStateOf(false) }
      Button(
        modifier = Modifier.testTag(TAG_BUTTON),
        onClick = { isVisible.value = !isVisible.value },
      ) {
        Text("toggle")
      }
      if (isVisible.value) {
        val count = rememberRetained { mutableStateOf(0) }
        LaunchedEffect(Unit) { count.value += 1 }

        Text(modifier = Modifier.testTag(TAG_CONDITIONAL_RETAINED), text = count.value.toString())
      }
    }
  }

  private data object ScreenD : Screen {

    data class State(val eventSink: (Event) -> Unit) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
      data object Pop : Event
    }
  }

  private class ScreenDPresenter(private val navigator: Navigator) : Presenter<ScreenD.State> {

    @Composable
    override fun present(): ScreenD.State {
      return ScreenD.State { event ->
        when (event) {
          is ScreenD.Event.Pop -> navigator.pop()
        }
      }
    }
  }

  @Composable
  private fun ScreenDUi(state: ScreenD.State, modifier: Modifier = Modifier) {
    Column(modifier) {
      Button(
        onClick = { state.eventSink(ScreenD.Event.Pop) },
        modifier = Modifier.testTag(TAG_POP_BUTTON),
      ) {
        Text(text = "pop")
      }
    }
  }

  private class DataSource {
    var value: Int = 0

    fun fetch(): Int = value
  }
}
