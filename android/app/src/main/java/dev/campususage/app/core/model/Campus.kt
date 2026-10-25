package dev.campususage.app.core.model

enum class Campus(val label:String) {
    CYBER("网安基地"), MAIN("主校区");
    fun usesNativeUtilities()=this==CYBER
    companion object { fun from(value:String?)=entries.firstOrNull{it.name==value}?:CYBER }
}
