package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.R
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.help.glide.BlurTransformation
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.isNightMode
import io.legado.app.utils.sysConfiguration
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import kotlin.random.Random

@Keep
object BookCover : KoinComponent {

    private val coverAlbumUseCase: CoverAlbumUseCase by inject()
    private val shellSettingsGateway: AppShellSettingsGateway by inject()
    private val coverSettingsGateway: CoverSettingsGateway by inject()

    private val isNightTheme: Boolean
        get() = when (shellSettingsGateway.currentSettings.themeMode) {
            "1" -> false
            "2" -> true
            else -> sysConfiguration.isNightMode
        }

    val defaultDrawable: Drawable
        @SuppressLint("UseCompatLoadingForDrawables")
        get() {
            val paths = coverAlbumUseCase.selectedImagePaths(isNightTheme)

            if (paths.isEmpty()) {
                return appCtx.resources.getDrawable(R.drawable.image_cover_default, null)
            }

            val randomPath = paths[Random.nextInt(paths.size)]
            return kotlin.runCatching {
                BitmapUtils.decodeBitmap(randomPath, 600, 900)!!.toDrawable(appCtx.resources)
            }.getOrDefault(appCtx.resources.getDrawable(R.drawable.image_cover_default, null))
        }

    fun getRandomDefaultPath(
        seed: Any? = null,
        isNight: Boolean = isNightTheme
    ): String? {
        val paths = coverAlbumUseCase.selectedImagePaths(isNight)
        if (paths.isEmpty()) return null
        val random = if (seed != null) Random(seed.hashCode()) else Random
        return paths[random.nextInt(paths.size)]
    }

    // 缓存随机封面 Drawable，避免重复解码
    private val randomDrawableCache = mutableMapOf<String, Drawable>()

    fun getRandomDefaultDrawable(
        seed: Any? = null,
        isNight: Boolean = isNightTheme
    ): Drawable {
        val randomPath = getRandomDefaultPath(seed, isNight)
            ?: return appCtx.resources.getDrawable(R.drawable.image_cover_default, null)

        // 生成缓存键
        val cacheKey = "$randomPath-${isNight}"

        // 从缓存中获取，如果没有则解码并缓存
        val drawable = randomDrawableCache.getOrPut(cacheKey) {
            kotlin.runCatching {
                BitmapUtils.decodeBitmap(randomPath, 600, 900)!!.toDrawable(appCtx.resources)
            }.getOrDefault(appCtx.resources.getDrawable(R.drawable.image_cover_default, null))
        }

        // 返回克隆的实例并 mutate，防止多个 View 共享状态（如 bounds）导致显示异常
        return drawable.constantState?.newDrawable()?.mutate() ?: drawable
    }

    /**
     * 加载封面
     */
    fun load(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
        onLoadFinish: (() -> Unit)? = null,
    ): RequestBuilder<Drawable> {
        val currentDefault = getRandomDefaultDrawable()
        if (coverSettingsGateway.currentSettings.useDefaultCover) {
            return ImageLoader.load(context, currentDefault)
                .centerCrop()
        }
        var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
        if (sourceOrigin != null) {
            options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
        }
        var builder = ImageLoader.load(context, path)
            .apply(options)
        if (onLoadFinish != null) {
            builder = builder.addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>,
                    isFirstResource: Boolean,
                ): Boolean {
                    onLoadFinish.invoke()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    onLoadFinish.invoke()
                    return false
                }
            })
        }
        return builder.placeholder(currentDefault)
            .error(currentDefault)
            .centerCrop()
    }



    /**
     * 加载模糊封面
     */
    fun loadBlur(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
    ): RequestBuilder<Drawable> {
        val currentDefault = getRandomDefaultDrawable()
        val loadBlur = ImageLoader.load(context, currentDefault)
            .transform(BlurTransformation(25), CenterCrop())
        if (coverSettingsGateway.currentSettings.useDefaultCover) {
            return loadBlur
        }
        var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
        if (sourceOrigin != null) {
            options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
        }
        return ImageLoader.load(context, path)
            .apply(options)
            .transform(BlurTransformation(25), CenterCrop())
            .transition(DrawableTransitionOptions.withCrossFade(1500))
            .thumbnail(loadBlur)
    }

}
