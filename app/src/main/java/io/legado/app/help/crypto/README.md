# 加密工具

基于 JCA (`javax.crypto` / `java.security`) 的加解密封装, 用于本地数据备份等场景。

非对称加密一般只能知道其中一个密钥, 因此提供以下重载函数:

```kotlin
fun setPublicKey(key: ByteArray): T
fun setPublicKey(key: String): T
fun setPrivateKey(key: ByteArray): T
fun setPrivateKey(key: String): T

fun decrypt(data: Any, usePublicKey: Boolean? = true): ByteArray?
fun decryptStr(data: Any, usePublicKey: Boolean? = true): String?

fun encrypt(data: Any, usePublicKey: Boolean? = true): ByteArray?
fun encryptHex(data: Any, usePublicKey: Boolean? = true): String?
fun encryptBase64(data: Any, usePublicKey: Boolean? = true): String?
```
