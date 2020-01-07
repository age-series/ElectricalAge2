package org.eln2.math

import ext.ed25519.AxlSign
import java.util.*

class Ed25519 {

    companion object {

        private val _axlSign = AxlSign()
        private val _base64encoder = Base64.getEncoder()
        private val _base64decoder = Base64.getDecoder()

        fun generateKeyPair(): List<KeyData> {
            val pair = _axlSign.generateKeyPair(_axlSign.randomBytes(32))

            // TODO: Convert pair into base64 encoded strings

            return listOf(PublicKey(""), PrivateKey(""))
        }

        fun sign(privateKey: PrivateKey, message: IntArray): IntArray {
            return arrayOf(0).toIntArray()
        }

        fun verify(publicKey: PublicKey, message: IntArray, signature: IntArray): IntArray? {
            return null
        }

        @JvmStatic
        fun main(args: Array<String>) {
            generateKeyPair()
        }
    }
}

abstract class KeyData {
    abstract val keyData: String
}

class PublicKey(override val keyData: String): KeyData()

class PrivateKey(override val keyData: String): KeyData()

