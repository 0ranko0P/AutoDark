package me.ranko.autodark.model

interface Blockable {
    fun getPackageName(): String

    fun isPrimaryUser(): Boolean
}