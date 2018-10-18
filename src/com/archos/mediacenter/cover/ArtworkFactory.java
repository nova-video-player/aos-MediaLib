// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.cover;

import com.archos.environment.ArchosFeatures;
import com.archos.medialib.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.io.IOException;

public class ArtworkFactory {

	static final String TAG = "ArtworkFactory";
	static final boolean DBG = false;

	// Hard to decide if we filter or not the bitmaps
	// When they are large enough it's not worth it.
	// But for the few small ones I improve a lot...
	// Maybe it's time to decide that most AlbumArts are quite large now?
	static final boolean ARTWORK_BITMAP_FILTERING =true;

	// Dithering is not needed since most artworks are complex images
	static final boolean ARTWORK_BITMAP_DITHERING = false;

	static final int OVERLAY_DESCRIPTION_MAX_HEIGHT = 300;
	private static final int NO_SIZE_LIMIT = 1024;

	static final int ALIGN_CENTER = 0;
	static final int ALIGN_TOP = 1;
	static final int ALIGN_BOTTOM = 2;

	private final int mWidth;
	private final int mHeight;

	Context mContext;
	Resources mRes;
	ContentResolver mContentResolver;
	LayoutInflater mLayoutInflater;

	private final BitmapFactory.Options mBitmapOptions;
	private final Canvas mCanvas;
	Paint mPaint;
	NinePatch mShadow9patch;
	Rect mShadow9patchPadding;
	Bitmap mShadowBitmap;
    TextView mLabelView = null;
    private float mContentLabelFontsize;


	public ArtworkFactory( Context context, int width, int height) {

		mContext = context;
		mRes = mContext.getResources();
		mContentResolver =  context.getContentResolver();
		mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		mWidth = width;
		mHeight = height;

        mContentLabelFontsize = mRes.getDimension(R.dimen.ContentLabel_fontsize);

		mBitmapOptions = new BitmapFactory.Options();
		// RGB565 is OK for the artwork, 888 will be needed only when we add the shadow
		// Also no need for dithering. OpenGL will do a good job at displaying it without.
		//TODO: check if using ARGB888 is not faster since it will be converted after anyway
		mBitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
		mBitmapOptions.inDither = ARTWORK_BITMAP_DITHERING;
		mBitmapOptions.inSampleSize = 1; // no sub-sampling
		mBitmapOptions.inJustDecodeBounds = false;

		// Prepare a painter and enable filtering if showing large bitmaps to avoid ugly rendering of the rescaled bitmap
		mPaint = new Paint();
		mPaint.setFilterBitmap(ARTWORK_BITMAP_FILTERING);
		mPaint.setDither(ARTWORK_BITMAP_DITHERING);

		// ======== Now prepare the shadow stuff =========

		// Create the destination bitmap and associate a canvas to it
		// Require ARGB for the shadow effect
		mShadowBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888 );
		mShadowBitmap.eraseColor(Color.TRANSPARENT);
		mCanvas = new Canvas(mShadowBitmap);

		// Decode the shadow bitmap
		int shadowId = ArchosFeatures.isAndroidTV(mContext)|| ArchosFeatures.isLUDO()?R.drawable.cover_shadow_512:(mWidth==128) ? R.drawable.cover_shadow_128 : R.drawable.cover_shadow_256;
		InputStream is = context.getResources().openRawResource(shadowId);
		mShadow9patchPadding = new Rect();

		// We must use this version of "decode" in order to get the nine-patch padding
		Bitmap shadowNinePatchBitmap = BitmapFactory.decodeStream(is, mShadow9patchPadding, null);
		try {
			is.close();
		} catch (IOException e) {}
		mShadow9patch = new NinePatch(shadowNinePatchBitmap, shadowNinePatchBitmap.getNinePatchChunk(), null);
	}


	public Context getContext() {
		return mContext;
	}
	public ContentResolver getContentResolver() {
		return mContentResolver;
	}
	public Resources getResources() {
		return mRes;
	}
	public BitmapFactory.Options getBitmapOptions() {
		return mBitmapOptions;
	}
	public LayoutInflater getLayoutInflater() {
		return mLayoutInflater;
	}

	/*
	 * Draw the a crop from sourceBitmap into area destinationRect of destinationBitmap
	 */
	public void drawBitmapInBitmap(Bitmap sourceBitmap, Rect sourceCrop, Bitmap destinationBitmap, Rect destinationRect) {
		synchronized (mCanvas) {
			mCanvas.setBitmap(destinationBitmap);
			mCanvas.drawBitmap(sourceBitmap, sourceCrop, destinationRect, mPaint);
		}
	}

	/**
     * Draw a shadow around the given bitmap.
     * Also draw a description view on top of it
     *
	 * @param artwork in
	 * @param descriptionView in
	 * @param srcCrop in
	 * @param shrinkFactor in
	 * @param shadowPaddingRect out
	 * @return
	 */
	public Bitmap addShadowAndDescription( Bitmap artwork, View descriptionView, Rect srcCrop, float shrinkFactor, Rect shadowPaddingRect) {

		Rect spr = new Rect(0,0,0,0);
		Bitmap destBitmap = addShadow(artwork, srcCrop, shrinkFactor, spr);

		// Draw the description over the cover
		if (descriptionView!=null) {
			addDescription(destBitmap, descriptionView, spr);
		}

		if (shadowPaddingRect!=null) {
			shadowPaddingRect.set(spr);
		}

		return destBitmap;
	}


    /**
     * Draw a shadow around the given bitmap.
     * Allocates a new bitmap to do it.
     *
     * @param artwork	Draw shadow around this bitmap
     * @param srcCrop   Use this region of the artwork shadow. Use the whole artwork if null.
     * @param shadowPaddingRect	out: returns the area inside the shadow (i.e. the area of the artwork in the middle of the shadow). Can be null.
     * @return     The shadowed bitmap, in a newly allocated bitmap
     */
	public Bitmap addShadow( Bitmap artwork, Rect srcCrop, Rect shadowPaddingRect) {
		return addShadow(artwork, srcCrop, 1f, shadowPaddingRect);
	}
    /**
     * Draw a shadow around the given bitmap.
     * Allocates a new bitmap to do it.
     *
     * @param artwork	Draw shadow around this bitmap
     * @param srcCrop   Use this region of the artwork shadow. Use the whole artwork if null.
     * @param shrinkFactor	set <1f To have a smaller artwork area (shadow is not shrinked)
     * @param shadowPaddingRect	out: returns the area inside the shadow (i.e. the area of the artwork in the middle of the shadow). Can be null.
     * @return     The shadowed bitmap, in a newly allocated bitmap
     */
	public Bitmap addShadow( Bitmap artwork, Rect srcCrop, float shrinkFactor, Rect shadowPaddingRect) {

		if (srcCrop==null) {
			srcCrop = new Rect(0,0,artwork.getWidth(),artwork.getHeight());
		}

		if(DBG) Log.d(TAG, "addShadow " + srcCrop.width() + "x" + srcCrop.height() + "  ("+srcCrop.width()/(float)srcCrop.height()+")");

		// wide or tall?
		final boolean tall = srcCrop.height() > srcCrop.width();

		float resizeFactor;
		if (tall) {
			// part to resize is image height minus shadow area (that is not resized)
			final float available_height = (mWidth - mShadow9patchPadding.top - mShadow9patchPadding.bottom)*shrinkFactor;
			resizeFactor =  available_height / (float)srcCrop.height();
		} else {
			// part to resize is image width minus shadow area (that is not resized)
			final float available_width = (mWidth - mShadow9patchPadding.left - mShadow9patchPadding.right)*shrinkFactor;
			resizeFactor =  available_width / (float)srcCrop.width();
		}

		// Allocate the result bitmap
		Bitmap destBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888 );
		destBitmap.eraseColor(Color.TRANSPARENT);

		synchronized (mCanvas) {
			mCanvas.setBitmap(destBitmap);
			//mCanvas.drawColor(Color.RED); //useful for debugging...

			// Size and position of the artwork
			final int artworkResizedWidth = (int)(srcCrop.width() * resizeFactor);
			final int artworkResizedHeight = (int)(srcCrop.height() * resizeFactor);
			final int artworkResizedX = (mWidth - artworkResizedWidth)/2;
			final int artworkResizedY = (mHeight - artworkResizedHeight)/2;
			final Rect artworkResizedArea = new Rect(artworkResizedX, artworkResizedY, artworkResizedX+artworkResizedWidth, artworkResizedY+artworkResizedHeight);
			if(DBG) Log.d(TAG, "artworkResizedArea = " + artworkResizedArea);

			// Size and position of the shadow
			final int shadowX = artworkResizedX-mShadow9patchPadding.left;
			final int shadowY = artworkResizedY-mShadow9patchPadding.top;
			final int shadowWidth = artworkResizedWidth + mShadow9patchPadding.left + mShadow9patchPadding.right;
			final int shadowHeight = artworkResizedHeight + mShadow9patchPadding.top + mShadow9patchPadding.bottom;
			final Rect shadowArea = new Rect(shadowX, shadowY, shadowX+shadowWidth, shadowY+shadowHeight);
			if(DBG) Log.d(TAG, "shadowArea = " + shadowArea);

            // Draw the wanted part (crop) of the artwork into the shadow free area
            mCanvas.drawBitmap(artwork, srcCrop, artworkResizedArea, mPaint);

			// Draw the nine-patch shadow
			mShadow9patch.draw(mCanvas, shadowArea);

			if (shadowPaddingRect!=null) {
				shadowPaddingRect.left = artworkResizedArea.left;
				shadowPaddingRect.top = artworkResizedArea.top;
				shadowPaddingRect.right = mWidth - artworkResizedArea.right;
				shadowPaddingRect.bottom = mHeight - artworkResizedArea.bottom;
			}
		}

		return destBitmap;
	}

	/**
	 * Caution, this method is very limited in the sense that it requires that the right bitmap is used in the canvas already
	 */
	public Bitmap addDescription( Bitmap destBitmap, View descriptionView, Rect shadowPaddingRect) {
		final int descriptionWidth = mWidth - shadowPaddingRect.left - shadowPaddingRect.right;
		descriptionView.setLayoutParams( new FrameLayout.LayoutParams(descriptionWidth, OVERLAY_DESCRIPTION_MAX_HEIGHT) );
		// Update the layout setup to take care of the updated text views
		descriptionView.measure(View.MeasureSpec.makeMeasureSpec(descriptionWidth, View.MeasureSpec.EXACTLY),
							    View.MeasureSpec.makeMeasureSpec(OVERLAY_DESCRIPTION_MAX_HEIGHT, View.MeasureSpec.AT_MOST));
		descriptionView.layout(0, 0, descriptionView.getMeasuredWidth(), descriptionView.getMeasuredHeight());
		mCanvas.save();
		mCanvas.translate(shadowPaddingRect.left, mHeight-shadowPaddingRect.bottom-descriptionView.getMeasuredHeight());
		descriptionView.draw(mCanvas);
		mCanvas.restore();

		return destBitmap;
	}

	// single line label
	public Bitmap createLabelBitmap(String label) {
        if (DBG) Log.d(TAG, "createLabelBitmap : label="+ label);

        mLabelView = (TextView)mLayoutInflater.inflate(R.layout.cover_roll_label, null);
		mLabelView.setText(label);
		mLabelView.setLayoutParams( new FrameLayout.LayoutParams(NO_SIZE_LIMIT,NO_SIZE_LIMIT) );
		mLabelView.measure(NO_SIZE_LIMIT, NO_SIZE_LIMIT);
		final int width = mLabelView.getMeasuredWidth();
		final int height = mLabelView.getMeasuredHeight();
		if(DBG) Log.d(TAG, "createLabelBitmap: mLabelView size = "+width+"x"+height);
		mLabelView.layout(0, 0, width, height);

        StaticLayout textLayout = null;
        if (isRtlLabel()) {
            // Archos hack : when a right-to-left language is selected calling TextView.draw()
            // draws the background but not the text, so also call TextLayout.draw() which works
            textLayout = createTextLayout(label, Color.LTGRAY, mContentLabelFontsize, width);
        }
        return createViewBitmap(mLabelView, textLayout, width, height, ALIGN_BOTTOM);
	}

	// multi line label
	public Bitmap createMessageBitmap(String msg, float fontSize, float width) {
		if (DBG) Log.d(TAG, "createMessageBitmap " + fontSize + " " + width);

		final int textPadding = 20; //MAGICAL

		final int boxWidth = (int)(width+0.5f);
		final int textWidth = boxWidth - 2*textPadding; // remove padding from the width required by user

        StaticLayout textLayout = createTextLayout(msg, Color.WHITE, fontSize, textWidth);

		final int textHeight = textLayout.getHeight();
		final int boxHeight = textHeight + 2*textPadding; // add padding to the text height computed by the layout

		// Allocate the bitmap, must be power of 2 to be used as OpenGL texture
		final int bitmapWidth = getPowerOfTwo(boxWidth);
		final int bitmapHeight = getPowerOfTwo(boxHeight);
		Bitmap destBitmap = Bitmap.createBitmap( bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888 );
		destBitmap.eraseColor(Color.TRANSPARENT);

		synchronized (mCanvas) {
			mCanvas.setBitmap(destBitmap);

			// Draw background
			Paint bgpaint = new Paint();
			bgpaint.setColor(Color.BLACK);
			bgpaint.setAlpha(128);
			final float bgleft = (bitmapWidth-boxWidth)/2;
			final float bgtop = (bitmapHeight-boxHeight)/2;
			mCanvas.translate(bgleft, bgtop);
			RectF windowbg = new RectF( 0, 0, boxWidth, boxHeight);
			mCanvas.drawRoundRect( windowbg, 6f, 6f, bgpaint); //MAGICAL
			mCanvas.translate(-bgleft, -bgtop);

			// Draw message
			final float textleft = (bitmapWidth-textWidth)/2;
			final float texttop = (bitmapHeight-textHeight)/2;
			mCanvas.translate(textleft, texttop);
			textLayout.draw(mCanvas);
			mCanvas.translate(-textleft, -texttop);
		}

		return destBitmap;
	}

	// Create a bitmap from an android view. At a given bitmap size
	public Bitmap createViewBitmap(View view, int bitmapWidth, int bitmapHeight) {
		return createViewBitmap(view, null, bitmapWidth, bitmapHeight, ALIGN_CENTER); // default is centering
	}

	// Create a bitmap from an android view. At a given bitmap size
	public Bitmap createViewBitmap(View view, Layout textLayout, int bitmapWidth, int bitmapHeight, int vertical_align) {
		final int actualBitmapWidth = getPowerOfTwo(bitmapWidth);
		final int actualBitmapHeight = getPowerOfTwo(bitmapHeight);
		Bitmap destBitmap = Bitmap.createBitmap( actualBitmapWidth, actualBitmapHeight, Bitmap.Config.ARGB_8888 );
		destBitmap.eraseColor(Color.TRANSPARENT);

		synchronized (mCanvas) {
			mCanvas.setBitmap(destBitmap);
			mCanvas.save();

			// Center the bitmap horizontally inside the "powerOfTwo" texture bitmap
            mCanvas.translate((actualBitmapWidth - bitmapWidth) / 2, 0);

			// Align vertically depending of the argument
			switch (vertical_align) {
			case ALIGN_BOTTOM:
				mCanvas.translate(0,actualBitmapHeight - bitmapHeight);
				break;
			case ALIGN_TOP:
				break;
			case ALIGN_CENTER:
			default:
				mCanvas.translate(0, (actualBitmapHeight - bitmapHeight) / 2);
			}

			view.draw(mCanvas);
            if (textLayout != null) {
                // Draw the text using the TextLayout if one is provided
                mCanvas.translate(0, (actualBitmapHeight - bitmapHeight) / 2);
                textLayout.draw(mCanvas);
            }

			mCanvas.restore();
		}
		return destBitmap;
	}

	// Compute a square crop in the cover bitmap
	public Rect getSquareCrop(Bitmap coverBitmap) {

		// In order to keep the bitmap aspect ratio we must apply the same scale factor to both axis
		// => compute the horizontal and vertical scale factors and apply the largest value
		// so that the image will have no black bars (will be cropped instead)
		final int w = coverBitmap.getWidth();
		final int h = coverBitmap.getHeight();
		Rect crop = new Rect();

		if (w==h) {
			// Square case (most common case first)
			crop.left = crop.top = 0;
			crop.right = w;
			crop.bottom = h;
		}
		else if (w > h) {
			// Wide case
			crop.top = 0;
			crop.bottom = h;
			crop.left = (w-h+1)/2;
			crop.right = crop.left + h;
		}
		else if (h > w) {
			// Tall case
			crop.left = 0;
			crop.right = w;
			crop.top = (h-w+1)/2;
			crop.bottom = crop.top + w;
		}
		return crop;
	}

	static private int getPowerOfTwo(int i) {
		if (i<=32) {
			return 32;
		} else if (i<=64) {
			return 64;
		} else if (i<=128) {
			return 128;
		} else if (i<=256) {
			return 256;
		} else if (i<=512) {
			return 512;
		} else {
			return 1024;
		}
		//return i == 1 ? 1 : Integer.highestOneBit(i - 1) << 1;
	}

	/**
	 * RemoveFilenameExtension
	 * @param filenameWithExtension
	 * @return filename without extension
	 */
	public String removeFilenameExtension(String filenameWithExtension) {
        int dotPos = filenameWithExtension.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < filenameWithExtension.length()) {
            return filenameWithExtension.substring(0, dotPos);
        } else {
        	return filenameWithExtension;
        }
	}

    public boolean isRtlLabel() {
        if (mLabelView != null && mLabelView.getLayout() != null) {
            // The label has only one line of text, so check the direction of line 0
            return (mLabelView.getLayout().getParagraphDirection(0) == -1);
        }
        return false;
    }

    private StaticLayout createTextLayout(String text, int color, float fontSize, int width) {
        TextPaint paint = new TextPaint();
        paint.setColor(color);
        paint.setTextSize(fontSize);
        paint.setTypeface(Typeface.SANS_SERIF);
        paint.setAntiAlias(true);

        return (new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.1f, 0f, true));
    }
}
