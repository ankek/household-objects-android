package dev.hho.android.data.apiclient

import dev.hho.android.data.apiclient.generated.apis.AttachmentsApi
import dev.hho.android.data.apiclient.generated.models.Attachment

enum class AttachmentCategory(val wire: String) {
    IMAGE("image"),
    MANUAL("manual"),
    WARRANTY("warranty"),
    RECEIPT("receipt"),
    GENERAL("general"),
}

data class AttachmentInfo(
    val id: String,
    val itemId: String,
    val category: String,
    val originalFilename: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAt: Long,
    val hasThumbnail: Boolean,
)

internal fun AttachmentCategory.toGenerated(): AttachmentsApi.CategoryUploadItemAttachment =
    when (this) {
        AttachmentCategory.IMAGE -> AttachmentsApi.CategoryUploadItemAttachment.Image
        AttachmentCategory.MANUAL -> AttachmentsApi.CategoryUploadItemAttachment.Manual
        AttachmentCategory.WARRANTY -> AttachmentsApi.CategoryUploadItemAttachment.Warranty
        AttachmentCategory.RECEIPT -> AttachmentsApi.CategoryUploadItemAttachment.Receipt
        AttachmentCategory.GENERAL -> AttachmentsApi.CategoryUploadItemAttachment.General
    }

internal fun Attachment.toInfo(): AttachmentInfo =
    AttachmentInfo(
        id = id,
        itemId = itemId,
        category = category.value,
        originalFilename = originalFilename,
        contentType = contentType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        createdAt = createdAt,
        hasThumbnail = hasThumbnail,
    )
