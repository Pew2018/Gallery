package org.fossify.gallery.helpers.backup

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.room.Room
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.FavoriteAccessSessionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RestoreSafBoundaryTest {
    private val app get() = RuntimeEnvironment.getApplication()
    // The persisted grant and provider metadata are real Robolectric state; permission delegation
    // models Android's tree-grant check, without granting any filesystem capability.
    private val context get() = object : ContextWrapper(app) {
        override fun checkUriPermission(uri: Uri, pid: Int, uid: Int, modeFlags: Int) = PackageManager.PERMISSION_GRANTED
        override fun checkCallingOrSelfUriPermission(uri: Uri, modeFlags: Int) = PackageManager.PERMISSION_GRANTED
    }
    @Before fun setup() {
        app.getSharedPrefs().edit().clear().commit()
        app.contentResolver.persistedUriPermissions.toList().forEach {
            app.contentResolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.config.saveFavoriteCredential("pin-fixture", PROTECTION_PIN)
    }
    class DocumentProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            return MatrixCursor(columns).apply {
                addRow(columns.map { column -> when (column) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> "primary:RestoreSaf/a.jpg"
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> "image/jpeg"
                    DocumentsContract.Document.COLUMN_FLAGS -> 0
                    DocumentsContract.Document.COLUMN_SIZE -> 1L
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1L
                    else -> "a.jpg"
                } }.toTypedArray())
            }
        }
        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    }
    private fun register(authority: String) {
        val provider = DocumentProvider()
        provider.attachInfo(app, android.content.pm.ProviderInfo().apply { this.authority = authority; exported = true })
        ShadowContentResolver.registerProviderInternal(authority, provider)
    }
    @Test fun validVirtualProofDoesNotPromoteAnOpaqueGrantedProviderOrMissingGrant() {
        register("opaque.restore.test")
        val uri = Uri.parse("content://opaque.restore.test/document/123")
        val request = BackupPathRequest(uri.toString(), false)
        assertEquals(BackupPathStatus.SAF_PERMISSION, DeviceBackupPathAccess(context).inspectBatch(setOf(request)).getValue(request).status(true))
        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val detail = DeviceBackupPathAccess(context).inspectBatch(setOf(request)).getValue(request)
        assertEquals(BackupPathStatus.UNCONFIRMED, detail.status(true))
        app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(BackupPathStatus.SAF_PERMISSION, DeviceBackupPathAccess(context).inspectBatch(setOf(request)).getValue(request).status(true))
    }
    @Test fun externalDocumentRetainsIndependentUriAndPhysicalFolderProtections() {
        val authority = "com.android.externalstorage.documents"
        register(authority)
        val folder = File(Environment.getExternalStorageDirectory(), "RestoreSaf").apply { mkdirs() }
        val file = File(folder, "a.jpg").apply { writeText("a") }
        val uri = DocumentsContract.buildDocumentUri(authority, "primary:RestoreSaf/a.jpg")
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "primary:RestoreSaf")
        try {
            app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val request = BackupPathRequest(uri.toString(), false)
            val inspector = DeviceBackupPathAccess(context)
            assertEquals(BackupPathStatus.AVAILABLE, inspector.inspectBatch(setOf(request)).getValue(request).status(true))
            app.config.addFolderProtection(folder.absolutePath, "physical-independent", PROTECTION_PIN)
            var detail = inspector.inspectBatch(setOf(request)).getValue(request)
            assertTrue(folder.absolutePath in detail.protectedRoots); assertEquals(BackupPathStatus.PROTECTED, detail.status(true))
            app.config.removeFolderProtection(folder.absolutePath)
            app.config.addFolderProtection(tree.toString(), "uri-independent", PROTECTION_PIN)
            detail = inspector.inspectBatch(setOf(request)).getValue(request)
            assertTrue(tree.toString() in detail.protectedRoots); assertEquals(BackupPathStatus.PROTECTED, detail.status(true))
            app.config.removeFolderProtection(tree.toString())
            val volume = DocumentsContract.buildTreeDocumentUri(authority, "primary:")
            app.config.addFolderProtection(volume.toString(), "volume-independent", PROTECTION_PIN)
            detail = inspector.inspectBatch(setOf(request)).getValue(request)
            assertTrue(volume.toString() in detail.protectedRoots); assertEquals(BackupPathStatus.PROTECTED, detail.status(true))
        } finally { file.delete(); folder.delete() }
    }
}
