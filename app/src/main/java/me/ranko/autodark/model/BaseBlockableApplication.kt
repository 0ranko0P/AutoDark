package me.ranko.autodark.model

class BaseBlockableApplication(private val packageName: String) : Blockable {

    constructor(blockable: Blockable):this(blockable.getPackageName())

    override fun getPackageName(): String = packageName

    override fun isPrimaryUser(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Blockable && packageName == other.getPackageName()
    }

    override fun hashCode(): Int = packageName.hashCode()

    override fun toString(): String = packageName
}