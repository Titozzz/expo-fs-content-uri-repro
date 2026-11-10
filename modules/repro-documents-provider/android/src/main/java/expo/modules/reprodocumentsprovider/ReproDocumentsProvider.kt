package expo.modules.reprodocumentsprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/**
 * A documents provider whose files misbehave the way some real providers do.
 * Every file has the same content: REAL_SIZE deterministic bytes.
 */
class ReproDocumentsProvider : DocumentsProvider() {
  private data class Doc(
    val id: String,
    /** Value of COLUMN_SIZE, null for "unknown". */
    val reportedSize: Long?,
    /** Serve the content through a pipe instead of a real file. */
    val viaPipe: Boolean
  )

  companion object {
    private const val ROOT_ID = "repro"
    private const val ROOT_DOC_ID = "root"
    private const val MIME_TYPE = "application/octet-stream"

    const val REAL_SIZE = 262_144L // 256 KiB

    private val DOCS = listOf(
      // Control: correct size, real file.
      Doc("correct-size.bin", REAL_SIZE, viaPipe = false),
      // Reports less than the content (the number seen in production).
      Doc("wrong-size.bin", 196_608L, viaPipe = false),
      // Reports no size: DocumentFile.length() returns 0.
      Doc("unknown-size.bin", null, viaPipe = false),
      // Correct size, but the content comes through a pipe.
      Doc("pipe.bin", REAL_SIZE, viaPipe = true)
    )

    private val DEFAULT_ROOT_PROJECTION = arrayOf(
      Root.COLUMN_ROOT_ID,
      Root.COLUMN_DOCUMENT_ID,
      Root.COLUMN_TITLE,
      Root.COLUMN_FLAGS,
      Root.COLUMN_ICON
    )

    private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
      Document.COLUMN_DOCUMENT_ID,
      Document.COLUMN_DISPLAY_NAME,
      Document.COLUMN_MIME_TYPE,
      Document.COLUMN_SIZE,
      Document.COLUMN_FLAGS,
      Document.COLUMN_LAST_MODIFIED
    )

    private fun content(): ByteArray = ByteArray(REAL_SIZE.toInt()) { (it % 251).toByte() }
  }

  override fun onCreate() = true

  override fun queryRoots(projection: Array<out String>?): Cursor {
    val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
    cursor.newRow().apply {
      add(Root.COLUMN_ROOT_ID, ROOT_ID)
      add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
      add(Root.COLUMN_TITLE, "Repro files")
      add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY)
      add(Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
    }
    return cursor
  }

  override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
    val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
    if (documentId == ROOT_DOC_ID) {
      cursor.newRow().apply {
        add(Document.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
        add(Document.COLUMN_DISPLAY_NAME, "Repro files")
        add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        add(Document.COLUMN_FLAGS, 0)
      }
    } else {
      addDocRow(cursor, findDoc(documentId))
    }
    return cursor
  }

  override fun queryChildDocuments(
    parentDocumentId: String,
    projection: Array<out String>?,
    sortOrder: String?
  ): Cursor {
    val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
    DOCS.forEach { addDocRow(cursor, it) }
    return cursor
  }

  override fun openDocument(
    documentId: String,
    mode: String,
    signal: CancellationSignal?
  ): ParcelFileDescriptor {
    val doc = findDoc(documentId)

    if (doc.viaPipe) {
      val (readSide, writeSide) = ParcelFileDescriptor.createReliablePipe()
      thread {
        // Repro-run change: the reader may close early; don't crash the process on EPIPE.
        try {
          ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { it.write(content()) }
        } catch (e: java.io.IOException) {
          android.util.Log.w("ReproProvider", "pipe writer for $documentId: ${e.message}")
        }
      }
      return readSide
    }

    val file = File(context!!.filesDir, "repro-content.bin")
    if (file.length() != REAL_SIZE) {
      file.writeBytes(content())
    }
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  }

  private fun findDoc(documentId: String) =
    DOCS.find { it.id == documentId } ?: throw FileNotFoundException(documentId)

  private fun addDocRow(cursor: MatrixCursor, doc: Doc) {
    cursor.newRow().apply {
      add(Document.COLUMN_DOCUMENT_ID, doc.id)
      add(Document.COLUMN_DISPLAY_NAME, doc.id)
      add(Document.COLUMN_MIME_TYPE, MIME_TYPE)
      add(Document.COLUMN_SIZE, doc.reportedSize)
      add(Document.COLUMN_FLAGS, 0)
      add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
    }
  }
}
