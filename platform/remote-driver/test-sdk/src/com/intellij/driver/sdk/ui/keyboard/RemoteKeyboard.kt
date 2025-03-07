package com.intellij.driver.sdk.ui.keyboard

import com.intellij.driver.sdk.ui.remote.Robot
import java.awt.event.KeyEvent

class RemoteKeyboard(private val robot: Robot) {
  fun key(key: Int) = robot.pressAndReleaseKey(key)

  fun enter() = key(KeyEvent.VK_ENTER)
  fun escape() = key(KeyEvent.VK_ESCAPE)
  fun down() = key(KeyEvent.VK_DOWN)
  fun up() = key(KeyEvent.VK_UP)
  fun left() = key(KeyEvent.VK_LEFT)
  fun right() = key(KeyEvent.VK_RIGHT)
  fun backspace() = key(KeyEvent.VK_BACK_SPACE)
  fun tab() = key(KeyEvent.VK_TAB)
  fun space() = key(KeyEvent.VK_SPACE)

  fun hotKey(vararg keyCodes: Int) {
    keyCodes.forEach {
      robot.pressKey(it)
      Thread.sleep(100)
    }
    keyCodes.reversed().forEach {
      robot.releaseKey(it)
      Thread.sleep(100)
    }
  }

  fun enterText(text: String, delayBetweenCharsInMs: Long = 50) {
    text.forEach {
      robot.type(it)
      Thread.sleep(delayBetweenCharsInMs)
    }
  }

  fun pressing(key: Int, doWhilePress: RemoteKeyboard.() -> Unit) {
    robot.pressKey(key)
    this.doWhilePress()
    robot.releaseKey(key)
  }

  fun doublePressing(key: Int, doWhilePress: RemoteKeyboard.() -> Unit) {
    try {
      robot.pressKey(key)
      robot.releaseKey(key)
      robot.pressKey(key)
      this.doWhilePress()
    } finally {
      robot.releaseKey(key)
    }
  }
}