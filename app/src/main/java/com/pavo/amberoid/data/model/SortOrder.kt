package com.pavo.amberoid.data.model

enum class SortOrder(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    ARTIST_ASC("Artist (A-Z)"),
    ARTIST_DESC("Artist (Z-A)"),
    DURATION_ASC("Duration (Short first)"),
    DURATION_DESC("Duration (Long first)"),
    DATE_ASC("Old first"),
    DATE_DESC("New first")
}