/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.ColorUtils;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.BitmapShader;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.systemui.R;
import com.android.systemui.qs.customize.QSCustomizer;
import com.android.systemui.util.animation.PhysicsAnimator;
import com.android.internal.util.xtended.XImageUtils;
import com.android.systemui.statusbar.NotificationMediaManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper view with background which contains {@link QSPanel} and {@link BaseStatusBarHeader}
 */
public class QSContainerImpl extends FrameLayout {

    private final Point mSizePoint = new Point();
    private static final FloatPropertyCompat<QSContainerImpl> BACKGROUND_BOTTOM =
            new FloatPropertyCompat<QSContainerImpl>("backgroundBottom") {
                @Override
                public float getValue(QSContainerImpl qsImpl) {
                    return qsImpl.getBackgroundBottom();
                }

                @Override
                public void setValue(QSContainerImpl background, float value) {
                    background.setBackgroundBottom((int) value);
                }
            };
    private static final PhysicsAnimator.SpringConfig BACKGROUND_SPRING
            = new PhysicsAnimator.SpringConfig(SpringForce.STIFFNESS_MEDIUM,
            SpringForce.DAMPING_RATIO_LOW_BOUNCY);
    private int mBackgroundBottom = -1;
    private int mHeightOverride = -1;
    private QSPanel mQSPanel;
    private View mQSDetail;
    private QuickStatusBarHeader mHeader;
    private float mQsExpansion;
    private QSCustomizer mQSCustomizer;
    private View mDragHandle;
    private View mQSPanelContainer;

    private ViewGroup mBackground;
    private ImageView mQsBackgroundImage;
    private View mBackgroundGradient;
    private View mStatusBarBackground;
    private Drawable mQsBackGround;
    private int mQsBgNewEnabled;
    private int mQsHeaderBgNew;

    private int mSideMargins;
    private boolean mQsDisabled;
    private int mContentPaddingStart = -1;
    private int mContentPaddingEnd = -1;
    private boolean mAnimateBottomOnNextLayout;

    private int mQsBackGroundAlpha;
    private int mCurrentColor;
    private Drawable mQsHeaderBackGround;
    private Drawable mSbHeaderBackGround;
    private boolean mQsBackgroundBlur;
    private boolean mQsBackGroundType;

    private Context mContext;

    private int mCount = 0;

    private static final String QS_PANEL_FILE_IMAGE = "custom_file_qs_panel_image";

    private boolean mImmerseMode;

    public QSContainerImpl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        Handler mHandler = new Handler();
        SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQSPanel = findViewById(R.id.quick_settings_panel);
        mQSPanelContainer = findViewById(R.id.expanded_qs_scroll_view);
        mQSDetail = findViewById(R.id.qs_detail);
        mHeader = findViewById(R.id.header);
        mQSCustomizer = (QSCustomizer) findViewById(R.id.qs_customize);
        mDragHandle = findViewById(R.id.qs_drag_handle_view);
        mBackground = findViewById(R.id.quick_settings_background);
        mQsBackgroundImage = findViewById(R.id.qs_image_view);
        mStatusBarBackground = findViewById(R.id.quick_settings_status_bar_background);
        mSbHeaderBackGround = getContext().getDrawable(R.drawable.qs_header_primary);
        mBackgroundGradient = findViewById(R.id.quick_settings_gradient_view);
        mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
        mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
        updateResources();
        updateSettings();
        mHeader.getHeaderQsPanel().setMediaVisibilityChangedListener((visible) -> {
            if (mHeader.getHeaderQsPanel().isShown()) {
                mAnimateBottomOnNextLayout = true;
            }
        });
        mQSPanel.setMediaVisibilityChangedListener((visible) -> {
            if (mQSPanel.isShown()) {
                mAnimateBottomOnNextLayout = true;
            }
        });


        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void setBackgroundBottom(int value) {
        // We're saving the bottom separately since otherwise the bottom would be overridden in
        // the layout and the animation wouldn't properly start at the old position.
        mBackgroundBottom = value;
        mBackground.setBottom(value);
    }

    private float getBackgroundBottom() {
        if (mBackgroundBottom == -1) {
            return mBackground.getBottom();
        }
        return mBackgroundBottom;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setBackgroundGradientVisibility(newConfig);
        updateResources();
        mSizePoint.set(0, 0); // Will be retrieved on next measure pass.
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_PANEL_BG_ALPHA), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_TYPE_BACKGROUND), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_CUSTOM_IMAGE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_CUSTOM_IMAGE_BLUR), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.DISPLAY_CUTOUT_MODE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_NEW_BG_ENABLED), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_HEADER_NEW_BG), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
         public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(Settings.System.QS_PANEL_CUSTOM_IMAGE)) ||
                uri.equals(Settings.System.getUriFor(Settings.System.QS_PANEL_TYPE_BACKGROUND))) {
                mCount = 0;
                updateSettings();
            } else {
                updateSettings();
            }
        }
    }

    private void updateSettings() {
        ContentResolver resolver = getContext().getContentResolver();
        String imageUri = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.QS_PANEL_CUSTOM_IMAGE, UserHandle.USER_CURRENT);
        mQsBackGroundAlpha = Settings.System.getIntForUser(resolver,
                Settings.System.QS_PANEL_BG_ALPHA, 255, UserHandle.USER_CURRENT);
        mImmerseMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_CUTOUT_MODE, 0, UserHandle.USER_CURRENT) == 1;
        mQsBgNewEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                    Settings.System.QS_NEW_BG_ENABLED, 0, UserHandle.USER_CURRENT);
        mQsHeaderBgNew = Settings.System.getIntForUser(getContext().getContentResolver(),
                    Settings.System.QS_HEADER_NEW_BG, 0, UserHandle.USER_CURRENT);
        post(new Runnable() {
            public void run() {
                setQsBackground();
            }
        });
        if (imageUri != null) {
            saveCustomFileFromString(Uri.parse(imageUri), QS_PANEL_FILE_IMAGE);
        }
        updateResources();
    }

    private void setQsBackground() {
        ContentResolver resolver = getContext().getContentResolver();
        BitmapDrawable currentImage = null;
        mCurrentColor = Color.WHITE;
        mQsBackGroundType = Settings.System.getIntForUser(resolver,
                    Settings.System.QS_PANEL_TYPE_BACKGROUND, 0, UserHandle.USER_CURRENT) == 1;
        mQsBackgroundBlur = Settings.System.getIntForUser(resolver,
                    Settings.System.QS_PANEL_CUSTOM_IMAGE_BLUR, 1, UserHandle.USER_CURRENT) == 1;

        if (mQsBackGroundType) {
            currentImage = getCustomImageFromString(QS_PANEL_FILE_IMAGE);
        }
        if (currentImage != null && mQsBackGroundType) {
            int width = mQSPanel.getWidth();
            int height = mQSPanelContainer.getHeight() + mDragHandle.getHeight();
            int corner = getContext().getResources().getDimensionPixelSize(R.dimen.qs_corner_radius);

            Bitmap bitmap = mQsBackgroundBlur ? XImageUtils.getBlurredImage(mContext, currentImage.getBitmap()) : currentImage.getBitmap();
            Bitmap toCenter = XImageUtils.scaleCenterCrop(bitmap, width, height);
            BitmapDrawable bDrawable = new BitmapDrawable(mContext.getResources(),
                            XImageUtils.getRoundedCornerBitmap(toCenter, corner, width, height, mCurrentColor));

            mQsBackGround = new InsetDrawable(bDrawable, 0, 0, 0, mContext.getResources().getDimensionPixelSize(com.android.internal.R.dimen.qs_background_inset));

            mBackground.setBackground(mQsBackGround);
            mBackground.setClipToOutline(true);
        } else {
	    if (!mQsBackGroundType) {
		switch(mQsBgNewEnabled) {
			// Accent Bottom
	               case 1:
		            mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary_accent);
			    mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary_accent);
			    break;
			// Gradient Bottom
		       case 2:
			    mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary_grad);
			    mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary_grad);
			    break;
			// Reverse Gradient Bottom
		       case 3:
			    mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary_rev_grad);
			    mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary_rev_grad);
			    break;
                        // Accent Border
                       case 4:
                            mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary_accent_brdr);
                            mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary_accent_brdr);
                            break;
                        // Gradient Border
                       case 5:
                            mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary_grad_brdr);
                            mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary_grad_brdr);
                            break;
                        // Reverse Gradient Border
                       case 6:
                            mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary_rev_grad_brdr);
                            mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary_rev_grad_brdr);
                            break;
		        // Default Black
		       default:
		       case 0:
			    mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
			    mQsHeaderBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
		            break;
                }
            }
	}
        switch(mQsHeaderBgNew) {
               // Accent Border
            case 1:
                mSbHeaderBackGround = getContext().getDrawable(R.drawable.qs_header_bg_accent_brdr);
                break;
               // Gradient Border
            case 2:
                mSbHeaderBackGround = getContext().getDrawable(R.drawable.qs_header_bg_grad_brdr);
                break;
               // Reverse Gradient Border
            case 3:
                mSbHeaderBackGround = getContext().getDrawable(R.drawable.qs_header_bg_rev_grad_brdr);
                break;
            default:
            case 0:
                mSbHeaderBackGround = getContext().getDrawable(R.drawable.qs_header_primary);
                break;
        }
	mBackground.setBackground(mQsBackGround);
        mStatusBarBackground.setBackground(mSbHeaderBackGround);
	mQsBackGround.setAlpha(mQsBackGroundAlpha);
        mStatusBarBackground.setAlpha(mQsBackGroundAlpha);
        mSbHeaderBackGround.setAlpha(mQsBackGroundAlpha);
        mQsHeaderBackGround.setAlpha(mQsBackGroundAlpha);
    }

    @Override
    public boolean performClick() {
        // Want to receive clicks so missing QQS tiles doesn't cause collapse, but
        // don't want to do anything with them.
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // QSPanel will show as many rows as it can (up to TileLayout.MAX_ROWS) such that the
        // bottom and footer are inside the screen.
        Configuration config = getResources().getConfiguration();
        boolean navBelow = config.smallestScreenWidthDp >= 600
                || config.orientation != Configuration.ORIENTATION_LANDSCAPE;
        MarginLayoutParams layoutParams = (MarginLayoutParams) mQSPanelContainer.getLayoutParams();

        // The footer is pinned to the bottom of QSPanel (same bottoms), therefore we don't need to
        // subtract its height. We do not care if the collapsed notifications fit in the screen.
        int maxQs = getDisplayHeight() - layoutParams.topMargin - layoutParams.bottomMargin
                - getPaddingBottom();
        if (navBelow) {
            maxQs -= getResources().getDimensionPixelSize(R.dimen.navigation_bar_height);
        }

        int padding = mPaddingLeft + mPaddingRight + layoutParams.leftMargin
                + layoutParams.rightMargin;
        final int qsPanelWidthSpec = getChildMeasureSpec(widthMeasureSpec, padding,
                layoutParams.width);
        mQSPanelContainer.measure(qsPanelWidthSpec,
                MeasureSpec.makeMeasureSpec(maxQs, MeasureSpec.AT_MOST));
        int width = mQSPanelContainer.getMeasuredWidth() + padding;
        int height = layoutParams.topMargin + layoutParams.bottomMargin
                + mQSPanelContainer.getMeasuredHeight() + getPaddingBottom();
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        // QSCustomizer will always be the height of the screen, but do this after
        // other measuring to avoid changing the height of the QS.
        mQSCustomizer.measure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(getDisplayHeight(), MeasureSpec.EXACTLY));
    }


    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        // Do not measure QSPanel again when doing super.onMeasure.
        // This prevents the pages in PagedTileLayout to be remeasured with a different (incorrect)
        // size to the one used for determining the number of rows and then the number of pages.
        if (child != mQSPanelContainer) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateExpansion(mAnimateBottomOnNextLayout /* animate */);
        mAnimateBottomOnNextLayout = false;
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        setBackgroundGradientVisibility(getResources().getConfiguration());
        mBackground.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
    }

    private void updateResources() {
        LayoutParams layoutParams = (LayoutParams) mQSPanelContainer.getLayoutParams();
        layoutParams.topMargin = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height);
        mQSPanelContainer.setLayoutParams(layoutParams);

        mSideMargins = getResources().getDimensionPixelSize(R.dimen.notification_side_paddings);
        mContentPaddingStart = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.notification_content_margin_start);
        int newPaddingEnd = getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.notification_content_margin_end);
        boolean marginsChanged = newPaddingEnd != mContentPaddingEnd;
        mContentPaddingEnd = newPaddingEnd;
        if (marginsChanged) {
            updatePaddingsAndMargins();
        }
        post(new Runnable() {
            public void run() {
                setQsBackground();
            }
        });
    }

    public void saveCustomFileFromString(Uri fileUri, String fileName) {
        try {
            final InputStream fileStream = mContext.getContentResolver().openInputStream(fileUri);
            File file = new File(mContext.getFilesDir(), fileName);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream output = new FileOutputStream(file);
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = fileStream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (Exception e) {
            if (mCount > 2) return;
            if (mQsBackGroundType) {
                Toast toast = Toast.makeText(mContext, R.string.photos_not_allowed, Toast.LENGTH_SHORT);
                toast.show();
            }
            mCount++;
        }
    }

    public BitmapDrawable getCustomImageFromString(String fileName) {
        BitmapDrawable mImage = null;
        File file = new File(mContext.getFilesDir(), fileName);
        if (file.exists()) {
            final Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());
            mImage = new BitmapDrawable(mContext.getResources(), XImageUtils.resizeMaxDeviceSize(mContext, image));
        }
        return mImage;
    }

    /**
     * Overrides the height of this view (post-layout), so that the content is clipped to that
     * height and the background is set to that height.
     *
     * @param heightOverride the overridden height
     */
    public void setHeightOverride(int heightOverride) {
        mHeightOverride = heightOverride;
        updateExpansion();
    }

    public void updateExpansion() {
        updateExpansion(false /* animate */);
    }

    public void updateExpansion(boolean animate) {
        int height = calculateContainerHeight();
        setBottom(getTop() + height);
        mQSDetail.setBottom(getTop() + height);
        // Pin the drag handle to the bottom of the panel.
        mDragHandle.setTranslationY(height - mDragHandle.getHeight());
        mBackground.setTop(mQSPanelContainer.getTop());
        updateBackgroundBottom(height, animate);
    }

    private void updateBackgroundBottom(int height, boolean animated) {
        PhysicsAnimator<QSContainerImpl> physicsAnimator = PhysicsAnimator.getInstance(this);
        if (physicsAnimator.isPropertyAnimating(BACKGROUND_BOTTOM) || animated) {
            // An animation is running or we want to animate
            // Let's make sure to set the currentValue again, since the call below might only
            // start in the next frame and otherwise we'd flicker
            BACKGROUND_BOTTOM.setValue(this, BACKGROUND_BOTTOM.getValue(this));
            physicsAnimator.spring(BACKGROUND_BOTTOM, height, BACKGROUND_SPRING).start();
        } else {
            BACKGROUND_BOTTOM.setValue(this, height);
        }

    }

    protected int calculateContainerHeight() {
        int heightOverride = mHeightOverride != -1 ? mHeightOverride : getMeasuredHeight();
        return mQSCustomizer.isCustomizing() ? mQSCustomizer.getHeight()
                : Math.round(mQsExpansion * (heightOverride - mHeader.getHeight()))
                + mHeader.getHeight();
    }

    private void setBackgroundGradientVisibility(Configuration newConfig) {
        if (newConfig.orientation == ORIENTATION_LANDSCAPE) {
            mBackgroundGradient.setVisibility(View.INVISIBLE);
        } else {
            mBackgroundGradient.setVisibility(mQsDisabled ? View.INVISIBLE : View.VISIBLE);
        }
    }

    public void setExpansion(float expansion) {
        mQsExpansion = expansion;
        mDragHandle.setAlpha(1.0f - expansion);
        updateExpansion();
    }

    private void updatePaddingsAndMargins() {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view == mBackgroundGradient
                    || view == mQSCustomizer) {
                // Some views are always full width
                continue;
            }
            LayoutParams lp = (LayoutParams) view.getLayoutParams();
            lp.rightMargin = mSideMargins;
            lp.leftMargin = mSideMargins;
            if (view == mQSPanelContainer) {
                // QS panel lays out some of its content full width
                mQSPanel.setContentMargins(mContentPaddingStart, mContentPaddingEnd);
            } else if (view == mHeader) {
                // The header contains the QQS panel which needs to have special padding, to
                // visually align them.
                mHeader.setContentMargins(mContentPaddingStart, mContentPaddingEnd);
            } else {
                view.setPaddingRelative(
                        mContentPaddingStart,
                        view.getPaddingTop(),
                        mContentPaddingEnd,
                        view.getPaddingBottom());
            }
        }
    }

    private int getDisplayHeight() {
        if (mSizePoint.y == 0) {
            getDisplay().getRealSize(mSizePoint);
        }
        return mSizePoint.y;
    }
}
