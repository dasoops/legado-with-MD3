package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import io.legado.app.R
import io.legado.app.databinding.ViewSimpleCounterBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.gone
import io.legado.app.utils.visible

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class SimpleCounterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = ViewSimpleCounterBinding.inflate(LayoutInflater.from(context), this)
    private var orientation: Int = 1
    private var currentProgress = 0
    private var maximum = 100
    private var minimum = 0
    private val isBottomBackground: Boolean

    var valueFormat: ((Int) -> String)? = null
    var onChanged: ((Int) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isIncrementing = false
    private var isDecrementing = false

    private val longPressDelay = 400L
    private val repeatInterval = 320L

    private val tvTit: TextView

    var progress: Int
        get() = currentProgress
        set(value) {
            currentProgress = value.coerceIn(min, max)
            updateValue()
        }

    var max: Int
        get() = maximum
        set(value) {
            maximum = value.coerceAtLeast(1)
            currentProgress = currentProgress.coerceAtMost(maximum)
            updateValue()
        }

    var min: Int
        get() = minimum
        set(value) {
            minimum = value
            currentProgress = currentProgress.coerceAtLeast(minimum)
            updateValue()
        }

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DetailSeekBar)
        isBottomBackground = typedArray.getBoolean(R.styleable.DetailSeekBar_isBottomBackground, false)
        val title = typedArray.getText(R.styleable.DetailSeekBar_title)
        maximum = typedArray.getInt(R.styleable.DetailSeekBar_max, 100)
        minimum = typedArray.getInt(R.styleable.DetailSeekBar_min, 0)
        currentProgress = currentProgress.coerceIn(minimum, maximum)

        val ta = context.obtainStyledAttributes(attrs, R.styleable.SimpleCounterView)
        orientation = ta.getInt(R.styleable.SimpleCounterView_orientation, 1)
        ta.recycle()

        if (orientation == 1) {
            tvTit = binding.tvSeekTitleVertical
            binding.tvSeekTitle.gone()
            binding.tvSeekTitleVertical.visible()
        } else {
            tvTit = binding.tvSeekTitle
            binding.tvSeekTitle.visible()
            binding.tvSeekTitleVertical.gone()
        }

        typedArray.recycle()

        tvTit.text = title
        TooltipCompat.setTooltipText(tvTit, title)

        binding.ivSeekPlus.setOnClickListener {
            if (progress < max) {
                progress++
                updateValue()
                onChanged?.invoke(progress)
            }
        }

        binding.ivSeekReduce.setOnClickListener {
            if (progress > min) {
                progress--
                updateValue()
                onChanged?.invoke(progress)
            }
        }

        binding.ivSeekPlus.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isIncrementing = true
                    handler.postDelayed(startIncrementRunnable, longPressDelay)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isIncrementing = false
                    handler.removeCallbacks(startIncrementRunnable)
                    handler.removeCallbacks(incrementRunnable)
                }
            }
            false
        }

        binding.ivSeekReduce.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDecrementing = true
                    handler.postDelayed(startDecrementRunnable, longPressDelay)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDecrementing = false
                    handler.removeCallbacks(startDecrementRunnable)
                    handler.removeCallbacks(decrementRunnable)
                }
            }
            false
        }

        binding.tvSeekValue.setOnClickListener {
            showSeekBarDialog()
        }

        updateValue()
    }

    private fun showSeekBarDialog() {
        context.alert(title = null, message = null) {
            customView {
                val detailSeekBar = DetailSeekBar(ctx).apply {
                    max = this@SimpleCounterView.max
                    min = this@SimpleCounterView.min
                    progress = this@SimpleCounterView.progress
                    valueFormat = this@SimpleCounterView.valueFormat
                    setTitle(tvTit.text)
                    onChanged = {
                        this@SimpleCounterView.progress = it
                        updateValue()
                        this@SimpleCounterView.onChanged?.invoke(it)
                    }
                }

                FrameLayout(ctx).apply {
                    setPadding(64, 0, 64, 0)
                    addView(
                        detailSeekBar,
                        LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
            }

            okButton()
            cancelButton()
        }
    }

    private fun updateValue() {
        binding.tvSeekValue.text = valueFormat?.invoke(progress) ?: progress.toString()
    }

    private val startIncrementRunnable = Runnable {
        handler.post(incrementRunnable)
    }

    private val startDecrementRunnable = Runnable {
        handler.post(decrementRunnable)
    }

    private val incrementRunnable = object : Runnable {
        override fun run() {
            if (progress < max) {
                progress++
                updateValue()
                onChanged?.invoke(progress)
                handler.postDelayed(this, repeatInterval)
            }
        }
    }

    private val decrementRunnable = object : Runnable {
        override fun run() {
            if (progress > min) {
                progress--
                updateValue()
                onChanged?.invoke(progress)
                handler.postDelayed(this, repeatInterval)
            }
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(incrementRunnable)
        handler.removeCallbacks(decrementRunnable)
        super.onDetachedFromWindow()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        tvTit.isEnabled = enabled
        binding.ivSeekPlus.isEnabled = enabled
        binding.ivSeekReduce.isEnabled = enabled
        binding.tvSeekValue.isEnabled = enabled
    }
}
