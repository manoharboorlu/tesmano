package com.matedroid.data.api.models

/** TeslaMateApi can report a domain error in an otherwise successful HTTP response. */
interface ApiEnvelope {
    val error: String?
}
