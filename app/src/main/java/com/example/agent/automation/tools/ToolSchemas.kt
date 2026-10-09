package com.example.agent.automation.tools

/** JSON Schemas handed to the model. Kept minimal and standard (no vendor extensions). */
object ToolSchemas {
    private const val TARGET = """
        {"type":"object","description":"How to locate the element",
         "properties":{
           "text":{"type":"string","description":"Visible text of the element"},
           "contentDescription":{"type":"string","description":"Accessibility description"},
           "viewId":{"type":"string","description":"Resource id from the observation, e.g. url_bar"},
           "key":{"type":"string","description":"The [key] shown for the element in the latest observation"},
           "className":{"type":"string","description":"Widget class name"}
         }}
    """

    val OPEN_APP = """{"type":"object","properties":{"packageName":{"type":"string","description":"Android package name, e.g. com.android.chrome"}},"required":["packageName"]}"""
    val CLOSE_APP = """{"type":"object","properties":{"packageName":{"type":"string"}},"required":["packageName"]}"""
    val READ_SCREEN = """{"type":"object","properties":{}}"""
    val FIND_TEXT = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""
    val CLICK = """{"type":"object","properties":{"target":$TARGET},"required":["target"]}"""
    val LONG_CLICK = """{"type":"object","properties":{"target":$TARGET},"required":["target"]}"""
    val TYPE_TEXT = """{"type":"object","properties":{"text":{"type":"string"},"target":$TARGET},"required":["text"]}"""
    val SCROLL = """{"type":"object","properties":{"direction":{"type":"string","enum":["forward","backward"]}},"required":["direction"]}"""
    val SWIPE = """{"type":"object","properties":{"direction":{"type":"string","enum":["up","down","left","right"]},"durationMs":{"type":"integer"}},"required":["direction"]}"""
    val PRESS_BACK = """{"type":"object","properties":{}}"""
    val PRESS_HOME = """{"type":"object","properties":{}}"""
    val WAIT = """{"type":"object","properties":{"durationMs":{"type":"integer","description":"Milliseconds to wait, max 5000"}},"required":["durationMs"]}"""
    val LAUNCH_URL = """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}"""
    val GET_NOTIFICATIONS = """{"type":"object","properties":{}}"""
    val ASK_USER = """{"type":"object","properties":{"text":{"type":"string","description":"The question to ask the user"}},"required":["text"]}"""
    val FINISH = """{"type":"object","properties":{"reason":{"type":"string","description":"Short summary of the outcome"}},"required":["reason"]}"""
}
