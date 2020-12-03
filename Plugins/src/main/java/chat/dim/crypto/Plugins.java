/* license: https://mit-license.org
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ==============================================================================
 */
package chat.dim.crypto;

import chat.dim.digest.DataDigester;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import chat.dim.digest.Keccak256;
import chat.dim.digest.RIPEMD160;

import javax.crypto.NoSuchPaddingException;

public abstract class Plugins extends chat.dim.format.Plugins {

    static {

        /*
         *  Key Parsers
         */

        Factories.symmetricKeyFactory = new SymmetricKey.Factory() {

            @Override
            public SymmetricKey generateSymmetricKey(String algorithm) {
                // Plain key
                if (PlainKey.PLAIN.equals(algorithm)) {
                    return PlainKey.getInstance();
                }
                Map<String, Object> key = new HashMap<>();
                key.put("algorithm", algorithm);
                return parseSymmetricKey(key);
            }

            @Override
            public SymmetricKey parseSymmetricKey(Map<String, Object> key) {
                String algorithm = (String) key.get("algorithm");
                // AES key
                if (SymmetricKey.AES.equals(algorithm)) {
                    try {
                        return new AESKey(key);
                    } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
                // Plain key
                if (PlainKey.PLAIN.equals(algorithm)) {
                    return PlainKey.getInstance();
                }
                return null;
            }
        };
        Factories.publicKeyFactory = new PublicKey.Factory() {

            @Override
            public PublicKey parsePublicKey(Map<String, Object> key) {
                String algorithm = (String) key.get("algorithm");
                // RSA key
                if (AsymmetricKey.RSA.equals(algorithm)) {
                    try {
                        return new RSAPublicKey(key);
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
                // ECC key
                if (AsymmetricKey.ECC.equals(algorithm)) {
                    try {
                        return new ECCPublicKey(key);
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
                return null;
            }
        };
        Factories.privateKeyFactory = new PrivateKey.Factory() {

            @Override
            public PrivateKey generatePrivateKey(String algorithm) {
                Map<String, Object> key = new HashMap<>();
                key.put("algorithm", algorithm);
                return parsePrivateKey(key);
            }

            @Override
            public PrivateKey parsePrivateKey(Map<String, Object> key) {
                String algorithm = (String) key.get("algorithm");
                // RSA key
                if (AsymmetricKey.RSA.equals(algorithm)) {
                    try {
                        return new RSAPrivateKey(key);
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
                // ECC key
                if (AsymmetricKey.ECC.equals(algorithm)) {
                    try {
                        return new ECCPrivateKey(key);
                    } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
                return null;
            }
        };

        /*
         *  Digest
         */

        // RIPEMD160
        RIPEMD160.digester = new DataDigester() {
            @Override
            public byte[] digest(byte[] data) {
                RIPEMD160Digest digest = new RIPEMD160Digest();
                digest.update(data, 0, data.length);
                byte[] out = new byte[20];
                digest.doFinal(out, 0);
                return out;
            }
        };

        // Keccak256
        Keccak256.digester = new DataDigester() {
            @Override
            public byte[] digest(byte[] data) {
                KeccakDigest digest = new KeccakDigest(256);
                digest.update(data, 0, data.length);
                byte[] out = new byte[digest.getDigestSize()];
                digest.doFinal(out, 0);
                return out;
            }
        };
    }
}
