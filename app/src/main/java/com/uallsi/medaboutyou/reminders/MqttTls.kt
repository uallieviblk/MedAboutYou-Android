// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.reminders

import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/** Builds an [SSLSocketFactory] for secure MQTT from optional PEM material. */
object MqttTls {

    /**
     * @param caCertPem     optional CA cert to trust (self-signed broker); blank → platform default trust
     * @param clientCertPem optional client cert chain for mutual TLS
     * @param clientKeyPem  optional client private key (PKCS#8 PEM) for mutual TLS
     */
    fun socketFactory(caCertPem: String, clientCertPem: String, clientKeyPem: String): SSLSocketFactory {
        val trustManagers = if (caCertPem.isNotBlank()) {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            parseCerts(caCertPem).forEachIndexed { i, c -> ks.setCertificateEntry("ca$i", c) }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(ks) }.trustManagers
        } else {
            null
        }

        val keyManagers = if (clientCertPem.isNotBlank() && clientKeyPem.isNotBlank()) {
            val certs = parseCerts(clientCertPem)
            val chain = Array<Certificate>(certs.size) { certs[it] }
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setKeyEntry("client", parsePrivateKey(clientKeyPem), CharArray(0), chain)
            }
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(ks, CharArray(0)) }.keyManagers
        } else {
            null
        }

        return SSLContext.getInstance("TLS").apply { init(keyManagers, trustManagers, null) }.socketFactory
    }

    private fun parseCerts(pem: String): List<X509Certificate> =
        CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(pem.toByteArray()))
            .map { it as X509Certificate }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val der = Base64.decode(
            pem.replace(Regex("-----(BEGIN|END)[^-]*-----"), "").replace(Regex("\\s"), ""),
            Base64.DEFAULT,
        )
        val spec = PKCS8EncodedKeySpec(der)
        // The PKCS#8 PEM doesn't name its algorithm; try the common ones.
        for (alg in listOf("RSA", "EC")) {
            runCatching { return KeyFactory.getInstance(alg).generatePrivate(spec) }
        }
        error("Unsupported MQTT client private key (expected PKCS#8 RSA or EC)")
    }
}
