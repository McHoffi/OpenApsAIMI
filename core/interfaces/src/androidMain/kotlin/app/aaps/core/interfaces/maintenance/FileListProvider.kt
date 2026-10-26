package app.aaps.core.interfaces.maintenance

import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.rx.weardata.CwfFile
import java.io.File

interface FileListProvider : PrefsFileInfo {

    val resultPath: File
    fun ensurePreferenceDirExists(): DocumentFile?
    fun ensureExportDirExists(): DocumentFile?
    fun ensureTempDirExists(): DocumentFile?
    fun ensureExtraDirExists(): DocumentFile?

    /**
     * True when AAPS uses the fixed folder `Documents/AAPS` as plain files (needs "all files
     * access"). The folder picker is not used then and cannot change the folder.
     */
    fun usesFixedFolder(): Boolean

    /**
     * Does the plain file `Documents/AAPS/extra/<name>` exist?
     * Returns null when this check is not possible (no "all files access"). The caller then asks
     * the chosen folder instead.
     */
    fun extraFileExists(name: String): Boolean?

    fun newPreferenceFile(): DocumentFile?
    fun newExportCsvFile(): DocumentFile?
    fun newCwfFile(filename: String, withDate: Boolean = true): DocumentFile?

    fun ensureResultDirExists(): File
    fun newResultFile(): File
    fun listCustomWatchfaceFiles(): MutableList<CwfFile>
    fun checkMetadata(metadata: Map<PrefsMetadataKey, PrefMetadata>): Map<PrefsMetadataKey, PrefMetadata>
}