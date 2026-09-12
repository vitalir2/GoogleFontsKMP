package vitalir.me.googlefonts

import kotlin.math.abs

/**
 * Resolves a font request against the directory, mirroring the semantics of Android's
 * downloadable fonts provider:
 * - an exact weight/style match wins;
 * - with [bestEffort] the closest available weight is returned (preferring the same style);
 * - without [bestEffort] a missing exact match returns null.
 */
internal fun FontDirectory.resolve(
    familyName: String,
    weight: Int,
    italic: Boolean,
    bestEffort: Boolean,
): FontEntry? {
    val family = families.firstOrNull { it.name.equals(familyName, ignoreCase = true) }
        ?: return null
    val exact = family.fonts.firstOrNull { it.weight == weight && it.italic == italic }
    if (exact != null) return exact
    if (!bestEffort) return null
    val sameItalic = family.fonts.filter { it.italic == italic }
    val pool = sameItalic.ifEmpty { family.fonts }
    return pool.minByOrNull { abs(it.weight - weight) }
}