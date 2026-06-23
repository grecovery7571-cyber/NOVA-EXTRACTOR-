package com.example.ui

import java.io.Serializable

/**
 * Representation of a directory or file node inside the decompiled APK/IPA resource tree.
 */
data class TreeNode(
    val name: String,
    val path: String, // Relative path inside the zip file
    val isDirectory: Boolean,
    val children: MutableList<TreeNode> = mutableListOf(),
    var isExpanded: Boolean = false
) : Serializable {
    
    /**
     * Sorts children recursively such that directories come first (sorted alphabetically)
     * and files follow next (sorted alphabetically).
     */
    fun sortChildrenRecursively() {
        children.sortWith(compareBy<TreeNode> { !it.isDirectory }.thenBy { it.name.lowercase() })
        for (child in children) {
            if (child.isDirectory) {
                child.sortChildrenRecursively()
            }
        }
    }
}
