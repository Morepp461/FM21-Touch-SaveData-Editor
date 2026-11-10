package com.morepp.fm21touchsaveeditor.core
import java.io.InputStream
import java.io.OutputStream
data class ContainerInspection(
    val formatName: String, val version: String?,
    val components: List<ComponentDescriptor>, val isWritable: Boolean
)
data class ComponentDescriptor(val id: String, val offset: Long, val length: Long, val checksum: String?)
interface SaveContainerParser { fun inspect(input: InputStream): ContainerInspection }
interface ComponentExtractor {
    fun extract(input: InputStream, component: ComponentDescriptor, output: OutputStream)
}
interface PlayerRecordMapper { fun map(databaseComponent: ByteArray): List<PlayerRecord> }
interface SaveRebuilder {
    fun rebuild(original: InputStream, editedComponents: Map<String, ByteArray>, output: OutputStream)
}
class UnsupportedSaveFormat(message: String) : Exception(message)
class UnsafeSaveWrite(message: String) : Exception(message)
object SaveWritePolicy {
    fun requireVerifiedWritable(inspection: ContainerInspection) {
        if (!inspection.isWritable) throw UnsafeSaveWrite("This save format has not passed write-validation.")
    }
}
