package com.ssoliwal.image;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * FFImageView is fully featured, complete one stop solution to manage and display bitmaps in android.
 * <p>
 * Supports all gestures: PinchZoom, DoubleTap
 * Supports Image rotation with animation.
 *
 * @author Shailesh Soliwal
 */

public class FFImageView extends View implements GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener {

    private final static long ZOOM_ANIMATION_DURATION = 300L;
    private final static float DOUBLE_TAP_SCALE_FACTOR = 1.5f;
    private final static long ROTATE_ANIMATION_DURATION = 200L;
    private final static float SNAP_THRESHOLD = 20.0f;
    private static final long SNAP_DURATION = 100L;
    private static final long SNAP_DELAY = 250L;
    private TranslateRunnable mTranslateRunnable;
    private SnapRunnable mSnapRunnable;
    private ScaleRunnable mScaleRunnable;
    private RotateRunnable mRotateRunnable;
    private float mMaxInitialScaleFactor = 1;
    private boolean mHaveLayout;
    private RectF mTempSrc = new RectF();
    private RectF mTempDst = new RectF();
    private RectF mTranslateRect = new RectF();
    private float mMinScale = 0.2f;
    private float mMaxScale = 4f;
    private final Context context;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetectorCompat mGestureDetector;

    private Matrix mMatrix = new Matrix();
    private Matrix mDrawMatrix;
    private Matrix mOriginalMatrix = new Matrix();
    private float[] mValues = new float[9];

    private Bitmap mBitmap;
    private BitmapDrawable mDrawable;
    float lastRotation = 0f;

    public FFImageView(Context context) {
        super(context);
        this.context = context;
    }

    public FFImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
        mDrawable = new BitmapDrawable(getResources(), mBitmap);

        mGestureDetector = new GestureDetectorCompat(context, this, null);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mSnapRunnable = new SnapRunnable(this);
        mScaleRunnable = new ScaleRunnable(this);
        mTranslateRunnable = new TranslateRunnable(this);
        mRotateRunnable = new RotateRunnable(this);
        configureBounds(true);
        generateMatrix();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mHaveLayout = true;
        configureBounds(changed);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        generateMatrix();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mBitmap == null) return;

        int saveCount = canvas.getSaveCount();
        canvas.save();
        // Finally, draw the bitmap using the matrix as a guide.
        //canvas.drawBitmap(mBitmap, mMatrix, null);
        if (mDrawMatrix != null) {
            canvas.concat(mDrawMatrix);
        }
        mDrawable.draw(canvas);
        canvas.restoreToCount(saveCount);

        // Extract the drawable's bounds (in our own copy, to not alter the image)
        mTranslateRect.set(mDrawable.getBounds());
        if (mDrawMatrix != null) {
            mDrawMatrix.mapRect(mTranslateRect);
        }
    }

    public void setBitmapFromResource(int drawableId) {
        Resources res = getResources();
        setBitmap(BitmapFactory.decodeResource(res, drawableId));
    }

    public void rotateImage(int angle, boolean animate) {
        if (animate) {
            mRotateRunnable.start(angle);
        } else {
            rotateAndUpdateMatrix(angle);
        }
    }

    private void rotateAndUpdateMatrix(float rotationDegrees) {
        if (rotationDegrees > 0) {
            float vw = this.getWidth();
            float vh = this.getHeight();
            float hvw = vw / 2;
            float hvh = vh / 2;
            float bw = (float) mBitmap.getWidth();
            float bh = (float) mBitmap.getHeight();

            // Rotate the bitmap the specified number of degrees.
            lastRotation += rotationDegrees;
            if (lastRotation == 90 || lastRotation == 270) {
                float temp = bw;
                bw = bh;
                bh = temp;
            }

            // First scale the bitmap to fit into the view. Use either scale factor for width and height, whichever is the smallest.
            float s1x = vw / bw;
            float s1y = vh / bh;
            mMinScale = (s1x < s1y) ? s1x : s1y;

            mMatrix.postRotate(rotationDegrees, hvw, hvh);

            scale(mMinScale, vw / 2, vh / 2);
            if (lastRotation >= 360) {
                lastRotation = 0;
            }

            invalidate();
        }
    }

    private float getScale() {
        mMatrix.getValues(mValues);
        float scaleX = mValues[Matrix.MSCALE_X];
        float skewY = mValues[Matrix.MSKEW_Y];

        // calc real scale
        float currentScale = (float) Math.sqrt(scaleX * scaleX + skewY * skewY);


        return currentScale;
    }

    private void scale(float newScale, float centerX, float centerY) {
        // ensure that mMixScale <= newScale <= mMaxScale
        newScale = Math.max(newScale, mMinScale);
        newScale = Math.min(newScale, mMaxScale);
        float currentScale = getScale();
        float factor = newScale / currentScale;
        // apply the scale factor
        mMatrix.postScale(factor, factor, centerX, centerY);
        // ensure the image is within the view bounds
        snap();
        invalidate();
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float currentScale = getScale();
        float newScale = currentScale * detector.getScaleFactor();
        scale(newScale, detector.getFocusX(), detector.getFocusY());

        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mScaleRunnable.stop();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mScaleGestureDetector == null) {
            // We're being destroyed; ignore any touch events
            return true;
        }

        mScaleGestureDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);
        final int action = event.getAction();

        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mTranslateRunnable.mRunning) {
                    snap();
                }
                break;
        }

        return true;
    }

    /**
     * Snaps the image so it touches all edges of the view.
     */
    private void snap() {
        mTranslateRect.set(mTempSrc);
        mMatrix.mapRect(mTranslateRect);
        // Determine how much to snap in the horizontal direction [if any]
        float maxLeft = 0.0f;
        float maxRight = getWidth();
        float l = mTranslateRect.left;
        float r = mTranslateRect.right;
        final float translateX;
        if (r - l < maxRight - maxLeft) {
            // Image is narrower than view; translate to the center of the view
            translateX = maxLeft + ((maxRight - maxLeft) - (r + l)) / 2;
        } else if (l > maxLeft) {
            // Image is off right-edge of screen; bring it into view
            translateX = maxLeft - l;
        } else if (r < maxRight) {
            // Image is off left-edge of screen; bring it into view
            translateX = maxRight - r;
        } else {
            translateX = 0.0f;
        }
        // Determine how much to snap in the vertical direction [if any]
        float maxTop = 0.0f;
        float maxBottom = getHeight();
        float t = mTranslateRect.top;
        float b = mTranslateRect.bottom;
        final float translateY;
        if (b - t < maxBottom - maxTop) {
            // Image is shorter than view; translate to the bottom edge of the view
            translateY = maxTop + ((maxBottom - maxTop) - (b + t)) / 2;
        } else if (t > maxTop) {
            // Image is off bottom-edge of screen; bring it into view
            translateY = maxTop - t;
        } else if (b < maxBottom) {
            // Image is off top-edge of screen; bring it into view
            translateY = maxBottom - b;
        } else {
            translateY = 0.0f;
        }
        if (Math.abs(translateX) > SNAP_THRESHOLD || Math.abs(translateY) > SNAP_THRESHOLD) {
            mSnapRunnable.start(translateX, translateY);
        } else {
            mMatrix.postTranslate(translateX, translateY);
            invalidate();
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        mTranslateRunnable.stop();
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        translate(-distanceX, -distanceY);
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        mTranslateRunnable.start(velocityX, velocityY);
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        float currentScale = getScale();
        float targetScale = mMinScale * DOUBLE_TAP_SCALE_FACTOR;
        if (currentScale < targetScale) {
            mScaleRunnable.start(currentScale, targetScale, e.getX(), e.getY());
        } else {
            mScaleRunnable.start(currentScale, mMinScale, e.getX(), e.getY());
        }

        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    /**
     * Runnable that animates an image translation operation.
     */
    private static class SnapRunnable implements Runnable {
        private static final long NEVER = -1L;
        private final FFImageView mHeader;
        private float mTranslateX;
        private float mTranslateY;
        private long mStartRunTime;
        private boolean mRunning;
        private boolean mStop;

        public SnapRunnable(FFImageView header) {
            mStartRunTime = NEVER;
            mHeader = header;
        }

        /**
         * Starts the animation.
         */
        public boolean start(float translateX, float translateY) {
            if (mRunning) {
                return false;
            }
            mStartRunTime = NEVER;
            mTranslateX = translateX;
            mTranslateY = translateY;
            mStop = false;
            mRunning = true;
            mHeader.postDelayed(this, SNAP_DELAY);
            return true;
        }

        /**
         * Stops the animation in place. It does not snap the image to its final translation.
         */
        public void stop() {
            mRunning = false;
            mStop = true;
        }

        @Override
        public void run() {
            // See if we were told to stop:
            if (mStop) {
                return;
            }
            // Translate according to current velocities and time delta:
            long now = System.currentTimeMillis();
            float delta = (mStartRunTime != NEVER) ? (now - mStartRunTime) : 0f;
            if (mStartRunTime == NEVER) {
                mStartRunTime = now;
            }
            float transX;
            float transY;
            if (delta >= SNAP_DURATION) {
                transX = mTranslateX;
                transY = mTranslateY;
            } else {
                transX = (mTranslateX / (SNAP_DURATION - delta)) * 10f;
                transY = (mTranslateY / (SNAP_DURATION - delta)) * 10f;
                if (Math.abs(transX) > Math.abs(mTranslateX) || transX == Float.NaN) {
                    transX = mTranslateX;
                }
                if (Math.abs(transY) > Math.abs(mTranslateY) || transY == Float.NaN) {
                    transY = mTranslateY;
                }
            }
            mHeader.translate(transX, transY);
            mTranslateX -= transX;
            mTranslateY -= transY;
            if (mTranslateX == 0 && mTranslateY == 0) {
                stop();
            }
            // See if we need to continue flinging:
            if (mStop) {
                return;
            }
            mHeader.post(this);
        }
    }

    private boolean translate(float tx, float ty) {
        mTranslateRect.set(mTempSrc);
        mMatrix.mapRect(mTranslateRect);
        final float maxLeft = 0.0f;
        final float maxRight = getWidth();
        float l = mTranslateRect.left;
        float r = mTranslateRect.right;
        final float translateX;

        // Otherwise, ensure the image never leaves the screen
        if (r - l < maxRight - maxLeft) {
            translateX = maxLeft + ((maxRight - maxLeft) - (r + l)) / 2;
        } else {
            translateX = Math.max(maxRight - r, Math.min(maxLeft - l, tx));
        }

        float maxTop = 0.0f;
        float maxBottom = getHeight();
        float t = mTranslateRect.top;
        float b = mTranslateRect.bottom;
        final float translateY;

        // Otherwise, ensure the image never leaves the screen
        if (b - t < maxBottom - maxTop) {
            translateY = maxTop + ((maxBottom - maxTop) - (b + t)) / 2;
        } else {
            translateY = Math.max(maxBottom - b, Math.min(maxTop - t, ty));
        }

        // Do the translation
        mMatrix.postTranslate(translateX, translateY);
        invalidate();
        return (translateX == tx) && (translateY == ty);
    }

    /**
     * Configures the bounds of the photo. The photo will always be scaled to fit center.
     */
    private void configureBounds(boolean changed) {
        if (mDrawable == null || !mHaveLayout) {
            return;
        }
        final int dwidth = mDrawable.getIntrinsicWidth();
        final int dheight = mDrawable.getIntrinsicHeight();
        final int vwidth = getWidth();
        final int vheight = getHeight();
        final boolean fits = (dwidth < 0 || vwidth == dwidth) &&
                (dheight < 0 || vheight == dheight);
        // We need to do the scaling ourself, so have the drawable use its native size.
        mDrawable.setBounds(0, 0, dwidth, dheight);
        // Create a matrix with the proper transforms
        if (changed || (mMinScale == 0 && mDrawable != null && mHaveLayout)) {
            generateMatrix();
            generateScale();
        }
        if (fits || mMatrix.isIdentity()) {
            // The bitmap fits exactly, no transform needed.
            mDrawMatrix = null;
        } else {
            mDrawMatrix = mMatrix;
        }
    }

    private void generateScale() {
        final int dwidth = mDrawable.getIntrinsicWidth();
        final int dheight = mDrawable.getIntrinsicHeight();
        final int vwidth = getWidth();
        final int vheight = getHeight();
        if (dwidth < vwidth && dheight < vheight) {
            mMinScale = 1.0f;
        } else {
            mMinScale = getScale();
        }
        mMaxScale = Math.max(mMinScale * 8, 8);
    }

    private void generateMatrix() {
        if (mBitmap != null) {
            final int dwidth = mBitmap.getWidth();
            final int dheight = mBitmap.getHeight();
            final int vwidth = getWidth();
            final int vheight = getHeight();
            final boolean fits = (dwidth < 0 || vwidth == dwidth) &&
                    (dheight < 0 || vheight == dheight);
            if (fits) {
                mMatrix.reset();
            } else {
                // Generate the required transforms for the photo
                mTempSrc.set(0, 0, dwidth, dheight);
                mTempDst.set(0, 0, vwidth, vheight);

                RectF scaledDestination = new RectF(
                        (vwidth / 2) - (dwidth * mMaxInitialScaleFactor / 2),
                        (vheight / 2) - (dheight * mMaxInitialScaleFactor / 2),
                        (vwidth / 2) + (dwidth * mMaxInitialScaleFactor / 2),
                        (vheight / 2) + (dheight * mMaxInitialScaleFactor / 2));
                if (mTempDst.contains(scaledDestination)) {
                    mMatrix.setRectToRect(mTempSrc, scaledDestination, Matrix.ScaleToFit.CENTER);
                } else {
                    mMatrix.setRectToRect(mTempSrc, mTempDst, Matrix.ScaleToFit.CENTER);
                }
            }
            mOriginalMatrix.set(mMatrix);
        }
    }

    /**
     * Runnable that animates an image rotation operation.
     */
    private static class RotateRunnable implements Runnable {
        private static final long NEVER = -1L;
        private final FFImageView mHeader;
        private float mTargetRotation;
        private float mAppliedRotation;
        private float mVelocity;
        private long mLastRuntime;
        private boolean mRunning;
        private boolean mStop;

        public RotateRunnable(FFImageView header) {
            mHeader = header;
        }

        /**
         * Starts the animation.
         */
        public void start(float rotation) {
            if (mRunning) {
                return;
            }
            mTargetRotation = rotation;
            mVelocity = mTargetRotation / ROTATE_ANIMATION_DURATION;
            mAppliedRotation = 0f;
            mLastRuntime = NEVER;
            mStop = false;
            mRunning = true;
            mHeader.post(this);
        }

        /**
         * Stops the animation in place. It does not snap the image to its final rotation.
         */
        public void stop() {
            float remainder = (90 - (mHeader.lastRotation % 90));
            if (remainder > 0.00001 && remainder < 50) {
                mHeader.rotateAndUpdateMatrix(remainder);
            }
            mRunning = false;
            mStop = true;
        }

        @Override
        public void run() {

            synchronized (mHeader) {
                if (mStop) {
                    return;
                }
                if (mAppliedRotation != mTargetRotation) {
                    long now = System.currentTimeMillis();
                    long delta = mLastRuntime != NEVER ? now - mLastRuntime : 0L;
                    float rotationAmount = mVelocity * delta;
                    if (mAppliedRotation < mTargetRotation
                            && mAppliedRotation + rotationAmount > mTargetRotation
                            || mAppliedRotation > mTargetRotation
                            && mAppliedRotation + rotationAmount < mTargetRotation) {
                        rotationAmount = mTargetRotation - mAppliedRotation;
                    }
                    //mHeader.rotate(rotationAmount, false);

                    mAppliedRotation += rotationAmount;
                    mHeader.rotateAndUpdateMatrix(rotationAmount);

                    if (mAppliedRotation == mTargetRotation) {
                        stop();
                    }
                    mLastRuntime = now;
                }
            }
            if (mStop) {
                return;
            }
            mHeader.post(this);
        }
    }

    /**
     * Runnable that animates an image scale operation.
     */
    private static class ScaleRunnable implements Runnable {
        private final FFImageView mHeader;
        private float mCenterX;
        private float mCenterY;
        private boolean mZoomingIn;
        private float mTargetScale;
        private float mStartScale;
        private float mVelocity;
        private long mStartTime;
        private boolean mRunning;
        private boolean mStop;

        public ScaleRunnable(FFImageView header) {
            mHeader = header;
        }

        /**
         * Starts the animation. There is no target scale bounds check.
         */
        public boolean start(float startScale, float targetScale, float centerX, float centerY) {
            if (mRunning) {
                return false;
            }
            mCenterX = centerX;
            mCenterY = centerY;
            // Ensure the target scale is within the min/max bounds
            mTargetScale = targetScale;
            mStartTime = System.currentTimeMillis();
            mStartScale = startScale;
            mZoomingIn = mTargetScale > mStartScale;
            mVelocity = (mTargetScale - mStartScale) / ZOOM_ANIMATION_DURATION;
            mRunning = true;
            mStop = false;
            mHeader.post(this);
            return true;
        }

        /**
         * Stops the animation in place. It does not snap the image to its final zoom.
         */
        public void stop() {
            mRunning = false;
            mStop = true;
        }

        @Override
        public void run() {
            if (mStop) {
                return;
            }
            // Scale
            long now = System.currentTimeMillis();
            long ellapsed = now - mStartTime;
            float newScale = (mStartScale + mVelocity * ellapsed);
            mHeader.scale(newScale, mCenterX, mCenterY);
            // Stop when done
            if (newScale == mTargetScale || (mZoomingIn == (newScale > mTargetScale))) {
                mHeader.scale(mTargetScale, mCenterX, mCenterY);
                stop();
            }
            if (!mStop) {
                mHeader.post(this);
            }
        }
    }

    /**
     * Runnable that animates an image translation operation.
     */
    private static class TranslateRunnable implements Runnable {
        private static final float DECELERATION_RATE = 1000f;
        private static final long NEVER = -1L;
        private final FFImageView mHeader;
        private float mVelocityX;
        private float mVelocityY;
        private long mLastRunTime;
        private boolean mRunning;
        private boolean mStop;

        public TranslateRunnable(FFImageView header) {
            mLastRunTime = NEVER;
            mHeader = header;
        }

        /**
         * Starts the animation.
         */
        public boolean start(float velocityX, float velocityY) {
            if (mRunning) {
                return false;
            }
            mLastRunTime = NEVER;
            mVelocityX = velocityX;
            mVelocityY = velocityY;
            mStop = false;
            mRunning = true;
            mHeader.post(this);
            return true;
        }

        /**
         * Stops the animation in place. It does not snap the image to its final translation.
         */
        public void stop() {
            mRunning = false;
            mStop = true;
        }

        @Override
        public void run() {
            // See if we were told to stop:
            if (mStop) {
                return;
            }
            // Translate according to current velocities and time delta:
            long now = System.currentTimeMillis();
            float delta = (mLastRunTime != NEVER) ? (now - mLastRunTime) / 1000f : 0f;
            final boolean didTranslate = mHeader.translate(mVelocityX * delta, mVelocityY * delta);
            mLastRunTime = now;
            // Slow down:
            float slowDown = DECELERATION_RATE * delta;
            if (mVelocityX > 0f) {
                mVelocityX -= slowDown;
                if (mVelocityX < 0f) {
                    mVelocityX = 0f;
                }
            } else {
                mVelocityX += slowDown;
                if (mVelocityX > 0f) {
                    mVelocityX = 0f;
                }
            }
            if (mVelocityY > 0f) {
                mVelocityY -= slowDown;
                if (mVelocityY < 0f) {
                    mVelocityY = 0f;
                }
            } else {
                mVelocityY += slowDown;
                if (mVelocityY > 0f) {
                    mVelocityY = 0f;
                }
            }
            // Stop when done
            if ((mVelocityX == 0f && mVelocityY == 0f) || !didTranslate) {
                stop();
                mHeader.snap();
            }
            // See if we need to continue flinging:
            if (mStop) {
                return;
            }
            mHeader.post(this);
        }
    }

    /**
     * Free all resources held by this view.
     * The view is on its way to be collected and will not be reused.
     */
    public void clear() {
        mGestureDetector = null;
        mScaleGestureDetector = null;
        mDrawable = null;
        mScaleRunnable.stop();
        mScaleRunnable = null;
        mTranslateRunnable.stop();
        mTranslateRunnable = null;
        mSnapRunnable.stop();
        mSnapRunnable = null;
        mRotateRunnable.stop();
        mRotateRunnable = null;
        setOnClickListener(null);
    }
}
