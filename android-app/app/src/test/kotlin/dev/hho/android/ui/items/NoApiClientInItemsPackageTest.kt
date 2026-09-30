package dev.hho.android.ui.items

import dev.hho.android.data.apiclient.HhoApiClient
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class NoApiClientInItemsPackageTest {

    @Test
    fun `ItemListViewModel's constructor never mentions HhoApiClient`() {
        assertNoConstructorParameterIsHhoApiClient(ItemListViewModel::class.java)
    }

    @Test
    fun `ItemDetailViewModel's constructor never mentions HhoApiClient`() {
        assertNoConstructorParameterIsHhoApiClient(ItemDetailViewModel::class.java)
    }

    @Test
    fun `no source file under ui items mentions HhoApiClient`() {
        val sourceFiles = itemsPackageSourceFiles()
        assertTrue(
            "Expected to find ui/items Kotlin source files to scan, found none at ${itemsPackageDir().absolutePath} " +
                "— this test would otherwise pass vacuously.",
            sourceFiles.isNotEmpty(),
        )

        val offenders = sourceFiles.filter { it.readText().contains("HhoApiClient") }
        if (offenders.isNotEmpty()) {
            fail(
                "ui/items must never reference HhoApiClient (plan.md D3.5, FR-112/FR-113 — this " +
                    "package's screens/ViewModels read exclusively from Room), but found it in: " +
                    offenders.joinToString { it.relativeTo(itemsPackageDir()).path },
            )
        }
    }

    private fun assertNoConstructorParameterIsHhoApiClient(viewModelClass: Class<*>) {
        val offendingConstructors = viewModelClass.declaredConstructors.filter { constructor ->
            constructor.parameterTypes.any { it == HhoApiClient::class.java }
        }
        assertTrue(
            "${viewModelClass.simpleName} must never take an HhoApiClient constructor parameter " +
                "(plan.md D3.5: 'no ViewModel in this deliverable holds a reference to HhoApiClient " +
                "at all') — found it on: $offendingConstructors",
            offendingConstructors.isEmpty(),
        )
    }

    private fun itemsPackageDir(): File =
        File("src/main/kotlin/dev/hho/android/ui/items")

    private fun itemsPackageSourceFiles(): List<File> =
        itemsPackageDir().listFiles { file -> file.isFile && file.extension == "kt" }?.toList().orEmpty()
}
