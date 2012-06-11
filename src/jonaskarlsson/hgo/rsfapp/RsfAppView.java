/*
 * Copyright (C) 2007 The Android Open Source Project
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
 * limitations under the License.
 */

package jonaskarlsson.hgo.rsfapp;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * View that draws, takes keystrokes, etc. for the RFS game.
 * 
 * Has a mode which RUNNING, PAUSED, etc. Has a x, y, dx, dy, ... capturing the
 * current journalist physics. All x/y etc. are measured with (0,0) at the lower
 * left. updatePhysics() advances the physics based on realtime. draw() renders
 * the journalist, and does an invalidate() to prompt another draw() as soon as
 * possible by the system.
 */
class RsfAppView extends SurfaceView implements SurfaceHolder.Callback {
	class RsfAppThread extends Thread implements SensorEventListener {

		private SensorManager sensorManager;
		private Sensor sensor;

		/*
		 * Difficulty setting constants
		 */
		public static final int DIFFICULTY_EASY = 0;
		public static final int DIFFICULTY_HARD = 1;
		public static final int DIFFICULTY_MEDIUM = 2;
		/*
		 * Physics constants
		 */
		public static final int PHYS_DOWN_ACCEL_SEC = 35;
		public static final int PHYS_SPEED_INIT = 30;
		public static final int PHYS_SPEED_MAX = 1000;
		/*
		 * State-tracking constants
		 */
		public static final int STATE_LOSE = 1;
		public static final int STATE_PAUSE = 2;
		public static final int STATE_READY = 3;
		public static final int STATE_RUNNING = 4;
		public static final int STATE_WIN = 5;
		public static final int STATE_GAME_OVER = 6;
		public static final int STATE_GAME_FINISHED = 7;
		public static final int STATE_GAME_STOPPED = 8;

		/*
		 * Goal condition constants
		 */
		public static final int TARGET_BOTTOM_PADDING = 17; // px below gear
		public static final int TARGET_PAD_HEIGHT = 8; // how high above ground
		public static final double TARGET_WIDTH = 1; // width of target
		
		private static final String KEY_DIFFICULTY = "mDifficulty";
		private static final String KEY_DX = "mDX";

		private static final String KEY_DY = "mDY";
		private static final String KEY_GOAL_SPEED = "mGoalSpeed";
		private static final String KEY_GOAL_WIDTH = "mGoalWidth";

		private static final String KEY_GOAL_X = "mGoalX";
		private static final String KEY_JOURNALIST_HEIGHT = "mJournalistHeight";
		private static final String KEY_JOURNALIST_WIDTH = "mJournalistWidth";
		private static final String KEY_FREED = "mFreed";
		private static final String KEY_CAUGHT = "mFreed";
		private static final String KEY_GAME_LEVEL = "mGameLevel";

		private static final String KEY_X = "mX";
		private static final String KEY_Y = "mY";
		private float mValueOfZ = 0;
		private int mRemainingPrisoners = 0;
		private int mCurrentLevelRemainingPrisoners = 0;

		/*
		 * Member (state) fields
		 */
		/** The drawable to use as the background of the animation canvas */
		private Bitmap mBackgroundImage;

		/**
		 * Current height of the surface/canvas.
		 * 
		 * @see #setSurfaceSize
		 */
		private int mCanvasHeight = 1;

		/**
		 * Current width of the surface/canvas.
		 * 
		 * @see #setSurfaceSize
		 */
		private int mCanvasWidth = 1;

		/** What to draw for the journalist when is captured */
		private Drawable mCapturedImage;

		/**
		 * Current difficulty --- Default is MEDIUM.
		 */
		private int mDifficulty;

		/** Velocity dx. */
		private double mDX;

		/** Velocity dy. */
		private double mDY;

		/** Allowed speed. */
		private int mGoalSpeed;

		/** Width of the prison. */
		private int mGoalWidth;

		/** X of the prison. */
		private int mGoalX;

		/** Message handler used by thread to interact with TextView */
		private Handler mHandler;

		/** What to draw for the prison */
		private Drawable mPrisionImage;

		/**
		 * Pixel height of prison image.
		 */
		private int mPrisonHeight;

		/**
		 * Pixel width of prison image.
		 */
		private int mPrisonWidth;

		/**
		 * Pixel height of journalist image.
		 */
		private int mJournalistHeight;

		/** What to draw for the journalist in its normal state */
		private Drawable mJournalistImage;

		/** Pixel width of journalist image. */
		private int mJournalistWidth;

		/** Used to figure out elapsed time between frames */
		private long mLastTime;
		
		/** The state of the game. One of READY, RUNNING, PAUSE, LOSE, or WIN */
		private int mMode;

		/** Indicate whether the surface has been created & is ready to draw */
		private boolean mRun = false;

		/** Handle to the surface manager object we interact with */
		private SurfaceHolder mSurfaceHolder;

		/**
		 * The current level of the game. There are five levels in this version.
		 */
		private int mGameLevel = 1;

		/** Number of freed journalists. */
		private int mFreed;

		/** Number of times being caught */
		private int mCaughtNrOfTimes;

		/** X of journalist center. */
		private double mX;

		/** Y of journalist center. */
		private double mY;

		private Rect mPrisonRect;

		public RsfAppThread(SurfaceHolder surfaceHolder, Context context,
				Handler handler) {

			this.sensorManager = (SensorManager) context
					.getSystemService(Context.SENSOR_SERVICE);
			List<Sensor> sensors = sensorManager
					.getSensorList(Sensor.TYPE_ORIENTATION);

			if (sensors.isEmpty()) {
				Toast.makeText(
						context,
						"No orientation sensor available! Fall back to different input types!",
						Toast.LENGTH_LONG);
			}

			this.sensor = sensors.get(0);
			this.sensorManager.registerListener(this, this.sensor,
					SensorManager.SENSOR_DELAY_GAME);
			// get handles to some important objects
			mSurfaceHolder = surfaceHolder;
			mHandler = handler;
			mContext = context;

			Resources res = context.getResources();
			// cache handles to our key sprites & other drawables
			mJournalistImage = context.getResources().getDrawable(
					R.drawable.journalist_plain);
			mCapturedImage = context.getResources().getDrawable(
					R.drawable.journalist_captured);

			mPrisionImage = context.getResources().getDrawable(
					R.drawable.prison);

			// load background image as a Bitmap instead of a Drawable b/c
			// we don't need to transform it and it's faster to draw this way
			mBackgroundImage = BitmapFactory.decodeResource(res,
					R.drawable.background);

			mJournalistWidth = mJournalistImage.getIntrinsicWidth();
			mJournalistHeight = mJournalistImage.getIntrinsicHeight();

			mPrisonHeight = mPrisionImage.getIntrinsicHeight();
			mPrisonWidth = mPrisionImage.getIntrinsicWidth();

			mDifficulty = DIFFICULTY_MEDIUM;

			// initial show-up of journalist (not yet playing)
			mX = mJournalistWidth;
			mY = mJournalistHeight * 2;
			mDX = 0;
			mDY = 0;
		}

		/**
		 * Starts the game, setting parameters for the current difficulty.
		 */
		public void doStart() {
			synchronized (mSurfaceHolder) {

				// First set the game for Medium difficulty
				mGoalWidth = (int) (mJournalistWidth * TARGET_WIDTH);
				int speedInit = PHYS_SPEED_INIT;

				// Adjust difficulty params for EASY/HARD
				if (mDifficulty == DIFFICULTY_EASY) {
					mGoalWidth = mGoalWidth * 4 / 3;
					speedInit = speedInit * 3 / 4;
				} else if (mDifficulty == DIFFICULTY_HARD) {
					mGoalWidth = mGoalWidth * 3 / 4;
					speedInit = speedInit * 4 / 3;
				}

				// Calculate the speed based on game level.
				speedInit = calculateSpeed(speedInit);

				if (mRemainingPrisoners == mCurrentLevelRemainingPrisoners) {
					// Calculate how many imprisoned journalists to free
					mRemainingPrisoners = calculateRemainingPrisoners();
					mCurrentLevelRemainingPrisoners = mRemainingPrisoners;
				}

				// pick a convenient initial location for journalist
				mX = mCanvasWidth / 2;
				mY = mCanvasHeight - mJournalistHeight / 2;

				// start with a little random motion
				mDY = speedInit;
				mDX = 0;

				// Figure initial spot for landing, not too near center
				while (true) {
					mGoalX = (int) (Math.random() * (mCanvasWidth - mGoalWidth));
					if (Math.abs(mGoalX - (mX - mJournalistWidth / 2)) > mCanvasHeight / 6)
						break;
				}

				mLastTime = System.currentTimeMillis() + 100;
				setState(STATE_RUNNING);
			}
		}

		/**
		 * Calculates the speed the journalist drops from the sky depending on
		 * the current level of the game.
		 * 
		 * @param speedInit
		 *            the initial speed
		 * @return the calculated speed.
		 */
		private int calculateSpeed(int speedInit) {
			int mCalculatedSpeed = speedInit;

			int mLevel1 = speedInit * -1;
			int mLevel2 = speedInit * -3;
			int mLevel3 = speedInit * -6;
			int mLevel4 = speedInit * -9;
			int mLevel5 = speedInit * -12;

			switch (mGameLevel) {
			case (1):
				mCalculatedSpeed = mLevel1;
			break;
			case (2):
				mCalculatedSpeed = mLevel2;
			break;
			case (3):
				mCalculatedSpeed = mLevel3;
			break;
			case (4):
				mCalculatedSpeed = mLevel4;
			break;
			case (5):
				mCalculatedSpeed = mLevel5;
			break;
			}

			return mCalculatedSpeed;

		}

		/**
		 * Calculates how many more prisoners the user has to free.
		 * @return the number of remaining prisoners.
		 */
		private int calculateRemainingPrisoners() {
			switch (mGameLevel) {
			case (1):
				mRemainingPrisoners = 50;
			break;
			case (2):
				mRemainingPrisoners = 100;
			break;
			case (3):
				mRemainingPrisoners = 150;
			break;
			case (4):
				mRemainingPrisoners = 200;
			break;
			case (5):
				mRemainingPrisoners = 250;
			break;
			}

			return mRemainingPrisoners;
		}

		/**
		 * Pauses the physics update & animation.
		 */
		public void pause() {
			synchronized (mSurfaceHolder) {
				if (mMode == STATE_RUNNING)
					setState(STATE_PAUSE);
			}
		}

		/**
		 * Restores game state from the indicated Bundle. Typically called when
		 * the Activity is being restored after having been previously
		 * destroyed.
		 * 
		 * @param savedState
		 *            Bundle containing the game state
		 */
		public synchronized void restoreState(Bundle savedState) {
			synchronized (mSurfaceHolder) {
				setState(STATE_PAUSE);

				mDifficulty = savedState.getInt(KEY_DIFFICULTY);
				mX = savedState.getDouble(KEY_X);
				mY = savedState.getDouble(KEY_Y);
				mDX = savedState.getDouble(KEY_DX);
				mDY = savedState.getDouble(KEY_DY);

				mJournalistWidth = savedState.getInt(KEY_JOURNALIST_WIDTH);
				mJournalistHeight = savedState.getInt(KEY_JOURNALIST_HEIGHT);
				mGoalX = savedState.getInt(KEY_GOAL_X);
				mGoalSpeed = savedState.getInt(KEY_GOAL_SPEED);
				mGoalWidth = savedState.getInt(KEY_GOAL_WIDTH);
				mFreed = savedState.getInt(KEY_FREED);
				mGameLevel = savedState.getInt(KEY_GAME_LEVEL);
			}
		}

		@Override
		public void run() {
			while (mRun) {
				Canvas c = null;
				try {
					c = mSurfaceHolder.lockCanvas(null);
					synchronized (mSurfaceHolder) {
						if (mMode == STATE_RUNNING)
							updatePhysics();
						doDraw(c);
					}
				} finally {
					// do this in a finally so that if an exception is thrown
					// during the above, we don't leave the Surface in an
					// inconsistent state
					if (c != null) {
						mSurfaceHolder.unlockCanvasAndPost(c);
					}
				}
			}
		}

		
		public boolean isThreadRunning() {
			return mRun;
		}

		/**
		 * Dump game state to the provided Bundle. Typically called when the
		 * Activity is being suspended.
		 * 
		 * @return Bundle with this view's state
		 */
		public Bundle saveState(Bundle map) {
			synchronized (mSurfaceHolder) {
				if (map != null) {
					map.putInt(KEY_DIFFICULTY, Integer.valueOf(mDifficulty));
					map.putDouble(KEY_X, Double.valueOf(mX));
					map.putDouble(KEY_Y, Double.valueOf(mY));
					map.putDouble(KEY_DX, Double.valueOf(mDX));
					map.putDouble(KEY_DY, Double.valueOf(mDY));
					map.putInt(KEY_JOURNALIST_WIDTH,
							Integer.valueOf(mJournalistWidth));
					map.putInt(KEY_JOURNALIST_HEIGHT,
							Integer.valueOf(mJournalistHeight));
					map.putInt(KEY_GOAL_X, Integer.valueOf(mGoalX));
					map.putInt(KEY_GOAL_SPEED, Integer.valueOf(mGoalSpeed));
					map.putInt(KEY_GOAL_WIDTH, Integer.valueOf(mGoalWidth));
					map.putInt(KEY_FREED, Integer.valueOf(mFreed));
					map.putInt(KEY_FREED, Integer.valueOf(mRemainingPrisoners));
					map.putInt(KEY_CAUGHT, Integer.valueOf(mCaughtNrOfTimes));
					map.putInt(KEY_GAME_LEVEL, Integer.valueOf(mGameLevel));
				}
			}
			return map;
		}

		/**
		 * Sets the current difficulty.
		 * 
		 * @param difficulty
		 */
		public void setDifficulty(int difficulty) {
			synchronized (mSurfaceHolder) {
				mDifficulty = difficulty;
			}
		}

		/**
		 * Used to signal the thread whether it should be running or not.
		 * Passing true allows the thread to run; passing false will shut it
		 * down if it's already running. Calling start() after this was most
		 * recently called with false will result in an immediate shutdown.
		 * 
		 * @param b
		 *            true to run, false to shut down
		 */
		public void setRunning(boolean b) {
			mRun = b;
		}

		/**
		 * Sets the game mode. That is, whether we are running, paused, in the
		 * failure state, in the victory state, etc.
		 * 
		 * @see #setState(int, CharSequence)
		 * @param mode
		 *            one of the STATE_* constants
		 */
		public void setState(int mode) {
			synchronized (mSurfaceHolder) {
				setState(mode, null);
			}
		}

		/**
		 * Sets the game mode. That is, whether we are running, paused, in the
		 * failure state, in the victory state, etc.
		 * 
		 * @param mode
		 *            one of the STATE_* constants
		 * @param message
		 *            string to add to screen or null
		 */
		public void setState(int mode, CharSequence message) {
			/*
			 * This method optionally can cause a text message to be displayed
			 * to the user when the mode changes. Since the View that actually
			 * renders that text is part of the main View hierarchy and not
			 * owned by this thread, we can't touch the state of that View.
			 * Instead we use a Message + Handler to relay commands to the main
			 * thread, which updates the user-text View.
			 */
			synchronized (mSurfaceHolder) {
				mMode = mode;

				if (mMode == STATE_RUNNING) {
					Message msg = mHandler.obtainMessage();
					Bundle b = new Bundle();
					b.putString("text", "");
					b.putInt("viz", View.INVISIBLE);
					msg.setData(b);
					mHandler.sendMessage(msg);
				} else {

					Resources res = mContext.getResources();
					CharSequence str = "";
					if (mMode == STATE_READY)
						str = res.getText(R.string.mode_ready);
					else if (mMode == STATE_PAUSE)
						str = res.getText(R.string.mode_pause);
					else if (mMode == STATE_LOSE) {
						str = res.getText(R.string.mode_lose);

						str = res.getText(R.string.mode_state_lose_suffix);
					} else if (mMode == STATE_WIN) {
						str = res.getString(R.string.mode_win_prefix)
								+ res.getString(R.string.mode_win_suffix);
					} else if (mMode == STATE_GAME_OVER) {
						str = res.getText(R.string.mode_state_game_over_suffix);

					} else if (mMode == STATE_GAME_FINISHED) {
						str = res
								.getText(R.string.mode_state_game_finished_suffix);

					}else if (mMode == STATE_GAME_STOPPED) {
						str = res
								.getText(R.string.mode_state_game_stopped_suffix);
						mFreed = 0;
						mGameLevel = 1;						
					}

					if (message != null) {
						str = message + "\n" + str;
					}

					Message msg = mHandler.obtainMessage();
					Bundle b = new Bundle();
					b.putString("text", str.toString());
					b.putInt("viz", View.VISIBLE);
					msg.setData(b);
					mHandler.sendMessage(msg);
				}
			}
		}

		/**
		 *  Callback invoked when the surface dimensions change. */
		public void setSurfaceSize(int width, int height) {
			// synchronized to make sure these all change atomically
			synchronized (mSurfaceHolder) {
				mCanvasWidth = width;
				mCanvasHeight = height;

				// don't forget to resize the background image
				mBackgroundImage = Bitmap.createScaledBitmap(mBackgroundImage,
						width, height, true);
			}
		}

		/**
		 * Resumes from a pause.
		 */
		public void unpause() {
			// Move the real time clock up to now
			synchronized (mSurfaceHolder) {
				mLastTime = System.currentTimeMillis() + 100;
			}
			setState(STATE_RUNNING);
		}

		/**
		 * Handles a key-down event.
		 * 
		 * @param keyCode
		 *            the key that was pressed
		 * @param msg
		 *            the original event object
		 * @return true
		 */
		boolean doKeyDown(int keyCode, KeyEvent msg) {
			synchronized (mSurfaceHolder) {
				boolean okStart = false;
				if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
					okStart = true;
				if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
					okStart = true;
				if (keyCode == KeyEvent.KEYCODE_S)
					okStart = true;

				if (okStart
						&& (mMode == STATE_READY || mMode == STATE_LOSE || mMode == STATE_WIN)) {
					// ready-to-start -> start
					doStart();
					return true;
				} else if (mMode == STATE_PAUSE && okStart) {
					// paused -> running
					unpause();
					return true;
				} else if (mMode == STATE_RUNNING) {
					// center/space -> fire
					if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
							|| keyCode == KeyEvent.KEYCODE_SPACE) {

						return true;
						// left/q -> left
					} else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
							|| keyCode == KeyEvent.KEYCODE_Q) {
						mValueOfZ = 9 * (mValueOfZ - 30);

						return true;
						// right/w -> right
					} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
							|| keyCode == KeyEvent.KEYCODE_W) {

						mValueOfZ = 9 * (mValueOfZ + 30);

						return true;
						// up -> pause
					} else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
						pause();
						return true;
					}
				}

				return false;
			}
		}

		/**
		 * Handles a key-up event.
		 * 
		 * @param keyCode
		 *            the key that was pressed
		 * @param msg
		 *            the original event object
		 * @return true if the key was handled and consumed, or else false
		 */
		boolean doKeyUp(int keyCode, KeyEvent msg) {
			boolean handled = false;

			synchronized (mSurfaceHolder) {
				if (mMode == STATE_RUNNING) {
					if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
							|| keyCode == KeyEvent.KEYCODE_SPACE) {

						handled = true;
					} else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
							|| keyCode == KeyEvent.KEYCODE_Q
							|| keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
							|| keyCode == KeyEvent.KEYCODE_W) {

						handled = true;
					}
				}
			}

			return handled;
		}

		/**
		 * Draws the journalist, messeges to the user, and background to the provided
		 * Canvas.
		 */
		private void doDraw(Canvas canvas) {

			// Draw the background image. Operations on the Canvas accumulate
			// so this is like clearing the screen.
			canvas.drawBitmap(mBackgroundImage, 0, 0, null);

			int yTop = mCanvasHeight - ((int) mY + mJournalistHeight / 2);

			int xLeft = (int) mX - mJournalistWidth / 2;

			mPrisionImage.setBounds(mGoalX, mCanvasHeight - mPrisonHeight,
					mGoalX + mPrisonWidth, mCanvasHeight);
			mPrisionImage.draw(canvas);

			String strFreed = "Freed: " + mFreed;

			Paint mTextFreed = new Paint();
			mTextFreed.setColor(Color.BLACK);
			mTextFreed.setTextSize(20);
			canvas.drawText(strFreed, 10, 30, mTextFreed);

			String mRemaining = "Remaining: " + mCurrentLevelRemainingPrisoners;

			Paint mTextRemaining = new Paint();
			mTextRemaining.setColor(Color.BLACK);
			mTextRemaining.setTextSize(20);
			canvas.drawText(mRemaining, 10, 50, mTextRemaining);

			String strCaughtNrOfTimes = "Caught: " + mCaughtNrOfTimes;

			String strGameLevel = "Level: " + mGameLevel;

			Paint mTextGameLevel = new Paint();
			mTextGameLevel.setColor(Color.BLACK);
			mTextGameLevel.setTextSize(20);
			canvas.drawText(strGameLevel, 10, 70, mTextGameLevel);

			Paint mCaught = new Paint();
			mCaught.setColor(Color.BLACK);
			mCaught.setTextSize(20);
			canvas.drawText(strCaughtNrOfTimes, 10, 90, mCaught);

			canvas.save();

			if (mMode == STATE_LOSE) {
				mCapturedImage.setBounds(xLeft, yTop, xLeft + mJournalistWidth,
						yTop + mJournalistHeight);
				mCapturedImage.draw(canvas);
			} else {
				mJournalistImage.setBounds(xLeft, yTop, xLeft
						+ mJournalistWidth, yTop + mJournalistHeight);
				mJournalistImage.draw(canvas);
			}

			canvas.restore();
		}

		/**
		 * Figures the where the journalist are located (x, y ...) based on the
		 * passage of real time. Does not invalidate(). Called at the start of
		 * draw(). Detects the end-of-game and sets the UI to the next state.
		 */
		private void updatePhysics() {
			long now = System.currentTimeMillis();

			int yTop = mCanvasHeight - ((int) mY + mJournalistHeight / 2);

			int xLeft = (int) mX - mJournalistWidth / 2;

			// Do nothing if mLastTime is in the future.
			// This allows the game-start to delay the start of the physics
			// by 100ms or whatever.
			if (mLastTime > now)
				return;

			double elapsed = (now - mLastTime) / 1000.0;

			// Base accelerations -- 0 for x, gravity for y
			double ddx = 0.0;
			double ddy = -PHYS_DOWN_ACCEL_SEC * elapsed;

			System.out.println("mValueOfZ : " + mValueOfZ);

			// Makes the steering a little easier and not so fast
			double mSteering = ((double) mValueOfZ / 3);

			double dxOld = mDX;
			double dyOld = mDY;

			// figure speeds for the end of the period
			mDX += ddx;
			mDY += ddy;

			// figure position based on average speed during the period
			mX += (elapsed * (mDX + dxOld) / 2) + mSteering;
			;
			mY += elapsed * (mDY + dyOld) / 2;

			mLastTime = now;

			mPrisonRect = new Rect(mGoalX, mCanvasHeight - mPrisonHeight,
					mGoalX + mPrisonWidth, mCanvasHeight);

			// Evaluate if we have landed ... stop the game
			double yLowerBound = TARGET_PAD_HEIGHT + mJournalistHeight / 2
					- TARGET_BOTTOM_PADDING;
			if (mY <= yLowerBound) {
				mY = yLowerBound;

				int result = STATE_WIN;
				Rect mRectionJournalist = new Rect(xLeft, yTop, xLeft
						+ mJournalistWidth, yTop + mJournalistHeight);

				if (mPrisonRect.intersect(mRectionJournalist)) {
					result = STATE_LOSE;
					mCaughtNrOfTimes++;
					if (mCaughtNrOfTimes >= 3) {
						result = STATE_GAME_OVER;

					}

				} else {

					mFreed++;
					mCurrentLevelRemainingPrisoners--;
					if (mCurrentLevelRemainingPrisoners == 0) {
						if (mGameLevel <= 4) {
							mGameLevel++;
							mRemainingPrisoners = 0;
						} else {
							result = STATE_GAME_FINISHED;
							mGameLevel=1;	
							mFreed = 0;
						}
					}
				}

				CharSequence message = "";
				setState(result, message);
				if (result >= 6) {

					Context context = getContext();
					Intent i = new Intent(context, DeathTollActivity.class);
					context.startActivity(i);
				}
			}
		}

		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// Do nothing
		}

		public void onSensorChanged(SensorEvent event) {
			mValueOfZ = event.values[2];
		}

	}

	/** Handle to the application context, used to e.g. fetch Drawables. */
	private Context mContext;

	/** Pointer to the text view to display "Paused.." etc. */
	private TextView mStatusText;

	/** The thread that actually draws the animation */
	private RsfAppThread thread;

	public RsfAppView(Context context, AttributeSet attrs) {
		super(context, attrs);

		// register our interest in hearing about changes to our surface
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);

		// create thread only; it's started in surfaceCreated()
		thread = new RsfAppThread(holder, context, new Handler() {
			@Override
			public void handleMessage(Message m) {
				mStatusText.setVisibility(m.getData().getInt("viz"));
				mStatusText.setText(m.getData().getString("text"));
			}
		});

		setFocusable(true); // make sure we get key events
	}

	/**
	 * Fetches the animation thread corresponding to this RsfAppView.
	 * 
	 * @return the animation thread
	 */
	public RsfAppThread getThread() {
		return thread;
	}

	/**
	 * Standard override to get key-press events.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent msg) {
		return thread.doKeyDown(keyCode, msg);
	}

	/**
	 * Standard override for key-up. We actually care about these, so we can
	 * turn off the engine or stop rotating.
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent msg) {
		return thread.doKeyUp(keyCode, msg);
	}

	/**
	 * Standard window-focus override. Notice focus lost so we can pause on
	 * focus lost. e.g. user switches to take a call.
	 */
	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		if (!hasWindowFocus)
			thread.pause();
	}

	/**
	 * Installs a pointer to the text view used for messages.
	 */
	public void setTextView(TextView textView) {
		mStatusText = textView;
	}

	/* Callback invoked when the surface dimensions change. */
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		thread.setSurfaceSize(width, height);
	}

	/*
	 * Callback invoked when the Surface has been created and is ready to be
	 * used.
	 */
	public void surfaceCreated(SurfaceHolder holder) {
		// start the thread here so that we don't busy-wait in run()
		// waiting for the surface to be created

		if ((thread.getState()) == (Thread.State.TERMINATED)) {
			thread.run();

		} else {
			thread.setRunning(true);

			thread.start();

		}

	}

	/*
	 * Callback invoked when the Surface has been destroyed and must no longer
	 * be touched. WARNING: after this method returns, the Surface/Canvas must
	 * never be touched again!
	 */
	public void surfaceDestroyed(SurfaceHolder holder) {
		// we have to tell thread to shut down & wait for it to finish, or else
		// it might touch the Surface after we return and explode
		boolean retry = true;
		thread.setRunning(false);
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
			}
		}
	}
}
