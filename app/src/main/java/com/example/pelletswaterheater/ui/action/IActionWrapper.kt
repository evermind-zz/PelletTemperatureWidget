package com.example.pelletswaterheater.ui.action

interface IActionWrapper<T> {
    fun doAction(value: T)
}