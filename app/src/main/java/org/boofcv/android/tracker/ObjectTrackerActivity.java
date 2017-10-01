package org.boofcv.android.tracker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.boofcv.android.DemoVideoDisplayActivity;
import org.boofcv.android.R;

import boofcv.abst.tracker.ConfigTld;
import boofcv.abst.tracker.TrackerObjectQuad;
import boofcv.android.ConvertBitmap;
import boofcv.android.gui.VideoImageProcessing;
import boofcv.core.image.ConvertImage;
import boofcv.factory.tracker.FactoryTrackerObjectQuad;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.Quadrilateral_F64;

/**
 * Allow the user to select an object in the image and then track it
 *
 * @author Peter Abeles
 */
public class ObjectTrackerActivity extends DemoVideoDisplayActivity
{

	private static final String TAG = ObjectTrackerActivity.class.getCanonicalName();

	int mode = 0;
	boolean display = false;

	// size of the minimum square which the user can select
	final static int MINIMUM_MOTION = 20;

	Point2D_I32 click0 = new Point2D_I32();
	Point2D_I32 click1 = new Point2D_I32();

	public ObjectTrackerActivity() {
		super(true);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater inflater = getLayoutInflater();
		LinearLayout controls = (LinearLayout)inflater.inflate(R.layout.objecttrack_controls,null);

		LinearLayout parent = getViewContent();
		parent.addView(controls);
	}

	@Override
	protected void onResume() {
		super.onResume();

		final ImageType imageType = ImageType.single(GrayU8.class);
		final TrackerObjectQuad tracker = FactoryTrackerObjectQuad.tld(new ConfigTld(false),GrayU8.class);
//		new Thread(new Runnable() {
//			@Override
//			public void run() {
//				setProcessing(new TrackingProcessing(tracker,imageType) );
//			}
//		}).start();
		setProcessing(new TrackingProcessing(tracker,imageType) );
	}

	public void resetPressed( View view ) {
		mode = 0;
	}

	protected class TrackingProcessing<T extends ImageBase> extends VideoImageProcessing<Planar<GrayU8>>
	{

		T input;
		ImageType<T> inputType;

		TrackerObjectQuad tracker;
		boolean visible;

		Quadrilateral_F64 location = new Quadrilateral_F64();

		Paint paintLine = new Paint();

		private Paint textPaint = new Paint();

		protected TrackingProcessing(TrackerObjectQuad tracker , ImageType<T> inputType) {
			super(ImageType.pl(3, GrayU8.class));
			this.inputType = inputType;

			if( inputType.getFamily() == ImageType.Family.GRAY ) {
				input = inputType.createImage(1,1);
			}

			mode = -1;
			this.tracker = tracker;

			paintLine.setColor(Color.BLUE);
			paintLine.setStrokeWidth(3f);


			// Create out paint to use for drawing
			textPaint.setARGB(255, 200, 0, 0);
			textPaint.setTextSize(60);

		}

		@Override
		protected void process(final Planar<GrayU8> input, final Bitmap output, final byte[] storage)
		{
			if(false) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						processCore(input, output, storage);
					}
				}).start();
			} else {
				processCore(input, output, storage);
			}
		}

		private void processCore(final Planar<GrayU8> input, final Bitmap output, final byte[] storage) {
			updateTracker(input);
			if(display) {
				visualize(input, output, storage);
			} else {
				locate(input, output, storage);
			}
		}

		private void updateTracker(Planar<GrayU8> color) {
			if( inputType.getFamily() == ImageType.Family.GRAY ) {
				input.reshape(color.width,color.height);
				ConvertImage.average(color,(GrayU8)input);
			} else {
				input = (T)color;
			}

			if( mode == 0 ) {
					click0.set(800, 400);
					click1.set(800, 400);
					mode = 1;
			} else if( mode == 1 ) {
					click1.set(900, 600);
					mode = 2;
			}

			if( mode == 2 ) {
				imageToOutput(click0.x, click0.y, location.a);
				imageToOutput(click1.x, click1.y, location.c);

				// make sure the user selected a valid region
				makeInBounds(location.a);
				makeInBounds(location.c);

				if( movedSignificantly(location.a,location.c) ) {
					// use the selected region and start the tracker
					location.b.set(location.c.x, location.a.y);
					location.d.set( location.a.x, location.c.y );

					tracker.initialize(input, location);
					visible = true;
					mode = 3;
				} else {
					// the user screw up. Let them know what they did wrong
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(ObjectTrackerActivity.this, "Drag a larger region", Toast.LENGTH_SHORT).show();
						}
					});
					mode = 0;
				}
			} else if( mode == 3 ) {
				visible = tracker.process(input,location);
			}
		}

		private void locate(Planar<GrayU8> color, Bitmap output, byte[] storage) {
			if( mode >= 2 ) {
				Log.d(TAG, "position=" + location.a.getX() + "|" + location.a.getY());
			}
		}

		private void visualize(Planar<GrayU8> color, Bitmap output, byte[] storage) {
			ConvertBitmap.multiToBitmap(color, output, storage);
			Canvas canvas = new Canvas(output);

			if( mode == 1 ) {
				Point2D_F64 a = new Point2D_F64();
				Point2D_F64 b = new Point2D_F64();

				imageToOutput(click0.x, click0.y, a);
				imageToOutput(click1.x, click1.y, b);

			} else if( mode >= 2 ) {
				if( visible ) {
					Quadrilateral_F64 q = location;

					drawLine(canvas,q.a,q.b,paintLine);
					drawLine(canvas,q.b,q.c,paintLine);
					drawLine(canvas,q.c,q.d,paintLine);
					drawLine(canvas,q.d,q.a,paintLine);
					Log.d(TAG, "position=" + location.a.getX()+"|" + location.a.getY());
				} else {
					canvas.drawText("?",color.width/2,color.height/2,textPaint);
				}
			}
		}

		private void drawLine( Canvas canvas , Point2D_F64 a , Point2D_F64 b , Paint color ) {
			canvas.drawLine((float)a.x,(float)a.y,(float)b.x,(float)b.y,color);
		}

		private void makeInBounds( Point2D_F64 p ) {
			if( p.x < 0 ) p.x = 0;
			else if( p.x >= input.width )
				p.x = input.width - 1;

			if( p.y < 0 ) p.y = 0;
			else if( p.y >= input.height )
				p.y = input.height - 1;

		}

		private boolean movedSignificantly( Point2D_F64 a , Point2D_F64 b ) {
			if( Math.abs(a.x-b.x) < MINIMUM_MOTION )
				return false;
			if( Math.abs(a.y-b.y) < MINIMUM_MOTION )
				return false;

			return true;
		}
	}
}