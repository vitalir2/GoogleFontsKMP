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

    /**
     * Attributes used to create a font request, mirroring the AndroidX `GoogleFont.Provider`.
     *
     * On Android the provider identifies the downloadable-fonts provider (Google Play Services)
     * and its signing certificates. On other platforms the provider is accepted for API
     * compatibility but ignored — fonts are fetched from the Google Fonts directory.
     */
    public class Provider
    internal constructor(
        internal val providerAuthority: String,
        internal val providerPackage: String,
        internal val certificates: List<List<ByteArray>>?,
        internal val certificatesRes: Int,
    ) {

        /**
         * Describe a font provider using a list of certificate hashes.
         *
         * @param providerAuthority the authority of the font provider.
         * @param providerPackage the package of the font provider.
         * @param certificates the list of sets of certificate hashes the provider should be
         *   signed with.
         */
        public constructor(
            providerAuthority: String,
            providerPackage: String,
            certificates: List<List<ByteArray>>,
        ) : this(providerAuthority, providerPackage, certificates, 0)

        /**
         * Describe a font provider using a resource array of certificate hashes (Android).
         *
         * @param providerAuthority the authority of the font provider.
         * @param providerPackage the package of the font provider.
         * @param certificates a resource array with the certificate hashes the provider should be
         *   signed with.
         */
        public constructor(
            providerAuthority: String,
            providerPackage: String,
            certificates: Int,
        ) : this(providerAuthority, providerPackage, null, certificates)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Provider) return false
            if (providerAuthority != other.providerAuthority) return false
            if (providerPackage != other.providerPackage) return false
            if (certificates != other.certificates) return false
            if (certificatesRes != other.certificatesRes) return false
            return true
        }

        override fun hashCode(): Int {
            var result = providerAuthority.hashCode()
            result = 31 * result + providerPackage.hashCode()
            result = 31 * result + (certificates?.hashCode() ?: 0)
            result = 31 * result + certificatesRes
            return result
        }
    }

    override fun toString(): String = "GoogleFont(name=$name, bestEffort=$bestEffort)"
}