package com.jacob.ble.factory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;


/**
 * Package : com.cvte.kidtracker.ui.bluetooth
 * Author : jacob
 * Date : 15-5-15
 * Description : 这个类是扫描的视图
 */
public class BleTrackingView extends View {
    private Bitmap mBitmapTrack;
    private Paint mPaint;
    private Rect mRect;

    public BleTrackingView(Context context) {
        this(context, null);
    }

    public BleTrackingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BleTrackingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBitmapTrack = BitmapFactory.decodeResource(getResources(), R.mipmap.bg_ble_tracking);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRect = new Rect(0, 0, mBitmapTrack.getWidth(), mBitmapTrack.getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
        canvas.drawBitmap(mBitmapTrack, null, mRect, mPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int bitmapWidth = mBitmapTrack.getWidth();
        int bitmapHeight = mBitmapTrack.getHeight();
        int bitmapSize = Math.max(bitmapWidth, bitmapHeight);
        setMeasuredDimension(bitmapSize, bitmapSize);
    }

    /**
     * 回收资源
     */
    public void onDestroy(){
        mBitmapTrack.recycle();
        mBitmapTrack = null;
    }

}
