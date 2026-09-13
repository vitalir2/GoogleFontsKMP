package vitalir.me.googlefonts

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.res.Resources
import androidx.annotation.WorkerThread
import androidx.core.content.res.FontResourcesParserCompat
import java.util.Arrays

/**
 * Checks whether the downloadable-fonts provider is available on this device.
 *
 * This is not necessary for normal usage — it is useful when debugging downloadable-fonts
 * behavior, for example to confirm that Google Play Services is present and correctly signed.
 *
 * ```kotlin
 * if (myProvider.isAvailableOnDevice(context)) {
 *     // fonts can be loaded through myProvider
 * }
 * ```
 *
 * @param context context used to look up the font provider.
 * @return `true` if the provider is usable for downloadable fonts, `false` if it is not found.
 * @throws IllegalStateException if the provider is present on device but its certificates do not
 *   match the ones supplied.
 */
@WorkerThread
public fun GoogleFont.Provider.isAvailableOnDevice(context: Context): Boolean =
    checkAvailable(context.packageManager, context.resources)

@SuppressLint("ListIterator") // not a hot code path, not optimized
@WorkerThread
internal fun GoogleFont.Provider.checkAvailable(
    packageManager: PackageManager,
    resources: Resources,
): Boolean {
    @Suppress("DEPRECATION")
    val providerInfo = packageManager.resolveContentProvider(providerAuthority, 0) ?: return false
    if (providerInfo.packageName != providerPackage) return false

    val signatures = packageManager.getSignatures(providerInfo.packageName)
    val sortedSignatures = signatures.sortedWith(ByteArrayComparator)
    val allExpectedCerts = loadCertsIfNeeded(resources)
    val certsMatched = allExpectedCerts.any { certList ->
        val expected = certList?.sortedWith(ByteArrayComparator)
        if (expected?.size != sortedSignatures.size) return@any false
        for (i in expected.indices) {
            if (!Arrays.equals(expected[i], sortedSignatures[i])) return@any false
        }
        true
    }
    return if (certsMatched) {
        true
    } else {
        throwFormattedCertsMissError(signatures)
    }
}

@SuppressLint("ListIterator") // not a hot code path, not optimized
private fun throwFormattedCertsMissError(signatures: List<ByteArray>): Nothing {
    val fullDescription =
        signatures.joinToString(",", prefix = "listOf(listOf(", postfix = "))") { repr(it) }
    throw IllegalStateException(
        "Provided signatures did not match. Actual signatures of package are:\n\n$fullDescription",
    )
}

private fun repr(b: ByteArray): String {
    return b.joinToString(",", prefix = "byteArrayOf(", postfix = ")")
}

private fun GoogleFont.Provider.loadCertsIfNeeded(resources: Resources): List<List<ByteArray?>?> {
    if (certificates != null) {
        return certificates
    }
    return FontResourcesParserCompat.readCerts(resources, certificatesRes)
}

private fun PackageManager.getSignatures(packageName: String): List<ByteArray> {
    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    val packageInfo: PackageInfo = getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
    @Suppress("DEPRECATION")
    return convertToByteArrayList(packageInfo.signatures!!)
}

private val ByteArrayComparator = Comparator { l: ByteArray, r: ByteArray ->
    if (l.size != r.size) {
        return@Comparator l.size - r.size
    }
    var i = 0
    while (i < l.size) {
        if (l[i] != r[i]) {
            return@Comparator l[i] - r[i]
        }
        ++i
    }
    0
}

private fun convertToByteArrayList(signatures: Array<Signature>): List<ByteArray> {
    val shaList: MutableList<ByteArray> = ArrayList()
    for (signature in signatures) {
        shaList.add(signature.toByteArray())
    }
    return shaList
}
