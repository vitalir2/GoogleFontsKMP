package vitalir.me.googlefonts

/**
 * A font family available on Google Fonts.
 *
 * @param name name of the font family, e.g. "Roboto" or "Open Sans".
 * @param bestEffort if `true` and the requested weight/style is not available for the family,
 *   the closest available match is returned. If `false`, loading fails when the exact
 *   weight/style is not available.
 */
public class GoogleFont(
    public val name: String,
    public val bestEffort: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "name cannot be blank" }
    }

    override fun toString(): String = "GoogleFont(name=$name, bestEffort=$bestEffort)"
}