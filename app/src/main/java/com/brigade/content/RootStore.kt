package com.brigade.content

/**
 * Remembers which campaign folder the user chose.
 *
 * An interface over one string. That is not ceremony: it is what keeps `content/` free
 * of `android.*` so [ContentRepository] and everything above it unit-tests on the JVM.
 */
interface RootStore {
    fun load(): String?
    fun save(treeUri: String?)
}
