package org.opensources.umai.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.opensources.umai.core.image.EncodedImage

/** The media types of what is sent to Mealie. */
object MediaTypes {
    val TextPlain = "text/plain".toMediaType()
    val Json = "application/json".toMediaType()
}

/** [this] picture as the multipart field [name] of an upload, in a file named [fileName] plus its extension. */
fun EncodedImage.formPart(name: String, fileName: String): MultipartBody.Part =
    MultipartBody.Part.createFormData(name, "$fileName.$extension", bytes.toRequestBody(mediaType.toMediaTypeOrNull()))
