package com.picozense.sdk.sample;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.picozense.sdk.PsFrame;
import com.picozense.sdk.IFrameCallback;
import com.picozense.sdk.PsCamera;
import com.picozense.sdk.PsFrame.DataType;
import com.picozense.sdk.PsParameter;
import android.content.pm.PackageManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.picozense.sdk.PsCamera.OnPicoCameraConnectListener;

import static com.picozense.sdk.PsFrame.PixelFormat.PixelFormatGray16;

public class MainActivity extends Activity {
	private static final boolean DEBUG = true;
	private static final String TAG = "Activity";
	private final Object mSync = new Object();
	private PsCamera mPicoCamera;
	private FrameCallback mFrameCallback = null;
	ByteBuffer mdepthBmpBuf;
    ByteBuffer mIrBmpBuf;
	MyRenderer myrender;
	private GLSurfaceView mGlview;
	private Spinner sp_mView = null;
	private Spinner sp_mData = null;
	private Spinner sp_mResolution = null;
	private Spinner sp_mPara = null;
	private EditText mEditPara = null;
	private CheckBox ck_MapRgb;
	private CheckBox ck_MapDepth;
	boolean showDepth = true;
	boolean showIr = false;
	boolean showRgb = false;
	boolean isSpDataFirst = true;
	boolean isSpViewFirst = true;
	boolean isSpResolutionFirst = true;
	boolean isSpParaFirst = true;
	//CameraParameter mPara;
	Bitmap mBmpDepth;
    Bitmap mBmpIr;
	Bitmap mBmpRgb;
	private int resolutionIndex = 3;
	private int currentParaType = 0;
	private DataType  dataType = DataType.DATA_TYPE_DEPTH_RGB_30;
	private static final int TOF_360 = 0;
	private static final int TOF_480 = 1;
	private static final int TOF_720 = 2;
	private static final int TOF_1080 = 3;
	String[] viewType = { "DEPTH","IR", "RGB"};
	String[] resolutionString = { "640*360", "640*480", "1280*720","1920*1080"};
	String[] paraType = { "DepthRange","PulseCount", "Threshold"};
	String[] dataType_sp = { "DepthAndRGB_30","IRAndRGB_30","DepthAndIR_30", "DepthAndIR_15_RGB_30", "WDRDepthAndRGB_30"};

	private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
    };
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        String android_version = android.os.Build.VERSION.RELEASE;
        int version = Integer.parseInt(android_version.substring(0,1));
        if(version >= 6 ) {
            int permission = this.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(
                        PERMISSIONS_STORAGE,
                        REQUEST_EXTERNAL_STORAGE
                );
            }
        }
		setContentView(R.layout.activity_main);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		mGlview = (GLSurfaceView)findViewById(R.id.glv_main);
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);

		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT);
		lp.height = dm.heightPixels;
		lp.width = lp.height*4/3;
		lp.gravity = Gravity.CENTER;
	    mGlview.setLayoutParams(lp);
		myrender = new MyRenderer(this);
		mGlview.setEGLContextClientVersion(2);
		mGlview.setRenderer(myrender);
		mGlview.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

		ck_MapRgb = (CheckBox) findViewById(R.id.map_rgb);
		ck_MapRgb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked){
					mPicoCamera.setMapperEnabledDepthToRGB(true);
					generateRgbBmp(2);
				}else{
					mPicoCamera.setMapperEnabledDepthToRGB(false);
					generateRgbBmp(resolutionIndex);
				}
			}
		});

		ck_MapDepth = (CheckBox) findViewById(R.id.map_depth);
		ck_MapDepth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked){
					mPicoCamera.setMapperEnabledRGBToDepth(true);
					if(0 == resolutionIndex) {
						generateTofBmp(0,3);
					}else if(1 == resolutionIndex) {
						generateTofBmp(0,2);
					}else if(2 == resolutionIndex) {
						generateTofBmp(0,1);
					}else if(3 == resolutionIndex) {
						generateTofBmp(0,0);
					}
				}else{
					mPicoCamera.setMapperEnabledRGBToDepth(false);
					generateTofBmp(0,TOF_480);
				}
			}
		});

		mEditPara= (EditText) findViewById(R.id.paraValue);
		mEditPara.setInputType( InputType.TYPE_CLASS_NUMBER);
		Button bSetPara = (Button) findViewById(R.id.setPara);
		bSetPara.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(mPicoCamera != null){
					if(mEditPara != null){
						String paraValue= mEditPara.getText().toString();
						if(paraValue.equals(""))
						{
							Toast.makeText(MainActivity.this, "please input the value!",Toast.LENGTH_SHORT).show();
							return;
						}
						int parameterValue = Integer.parseInt(paraValue);
						if(0 == currentParaType) {
							//set depthrange
							if(parameterValue >= 0 && parameterValue <= 8){
								mPicoCamera.setDepthRange(parameterValue);
							} else{
								Toast.makeText(MainActivity.this, "depth range is 0-8",Toast.LENGTH_SHORT).show();
							}
						}else if(1 == currentParaType){
							//set Pulsecount
							if(parameterValue >= 0 && parameterValue <= 600){
								mPicoCamera.setPulseCount(parameterValue);
							} else{
								Toast.makeText(MainActivity.this, "pulseCount range must 0~600",Toast.LENGTH_SHORT).show();
							}
						}else if(2 == currentParaType){
							//set Threshold
							if(parameterValue >= 0 && parameterValue <= 200){
								mPicoCamera.setBGThreshold(parameterValue);
							} else{
								Toast.makeText(MainActivity.this, "threshold range must be 0~200",Toast.LENGTH_SHORT).show();
							}
						}
					}

				}
			}
		});

		sp_mPara = (Spinner) findViewById(R.id.spinner_paratype);
		ArrayAdapter<String> mAdapterPara=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, paraType);
		sp_mPara.setAdapter(mAdapterPara);
		sp_mPara.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
									   int position, long id) {
				if(isSpParaFirst){
					isSpParaFirst = false;
					return;
				}
				String str=parent.getItemAtPosition(position).toString();
				if(str.equals("DepthRange")){
					currentParaType = 0;
				}else if(str.equals("PulseCount")) {
					currentParaType = 1;
				}else if(str.equals("Threshold")){
					currentParaType = 2;
				}
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}
		});

		sp_mView = (Spinner) findViewById(R.id.spinner_viewtype);
        ArrayAdapter<String> mAdapterView=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, viewType);
		sp_mView.setAdapter(mAdapterView);
		sp_mView.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
				if(isSpViewFirst){
					isSpViewFirst = false;
					return;
				}
                String str=parent.getItemAtPosition(position).toString();
                if(str.equals("DEPTH")){
					if(sp_mResolution != null){
						sp_mResolution.setEnabled(false);
					}
                    showDepth = true;
                    showIr = false;
                    showRgb = false;
                }else if(str.equals("IR")) {
					if(sp_mResolution != null){
						sp_mResolution.setEnabled(false);
					}
                    showIr = true;
                    showDepth = false;
                    showRgb = false;
                }else if(str.equals("RGB")){
					if(sp_mResolution != null){
						sp_mResolution.setEnabled(true);
					}
                    showIr = false;
                    showDepth = false;
                    showRgb = true;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

		sp_mResolution = (Spinner) findViewById(R.id.spinner_resolution);
		ArrayAdapter<String> mAdapterResolution=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, resolutionString);
		sp_mResolution.setAdapter(mAdapterResolution);
		sp_mResolution.setEnabled(false);

		sp_mResolution.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
									   int position, long id) {
				if (isSpResolutionFirst) {
					isSpResolutionFirst = false;
					return;
				}
				String str = parent.getItemAtPosition(position).toString();
				if (str.equals("640*360")) {
					mPicoCamera.setRgbResolution(3);
					resolutionIndex = 3;
				} else if (str.equals("640*480")) {
					mPicoCamera.setRgbResolution(2);
					resolutionIndex = 2;
				} else if (str.equals("1280*720")) {
					mPicoCamera.setRgbResolution(1);
					resolutionIndex = 1;
				} else if (str.equals("1920*1080")) {
					mPicoCamera.setRgbResolution(0);
					resolutionIndex = 0;
				}
				generateRgbBmp(resolutionIndex);
				generateTofBmp(0,TOF_480);
				generateTofBmp(1,TOF_480);
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}
		});

		sp_mData = (Spinner) findViewById(R.id.spinner_datatype);
        ArrayAdapter<String> mAdapterData=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, dataType_sp);
		sp_mData.setAdapter(mAdapterData);
		sp_mData.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
            	if(isSpDataFirst){
					isSpDataFirst = false;
            		return;
				}

                String str = parent.getItemAtPosition(position).toString();
                if(str.equals("DepthAndRGB_30")){
                    if(mPicoCamera != null){
                        dataType = DataType.DATA_TYPE_DEPTH_RGB_30;
                        mPicoCamera.setDataType(dataType.ordinal());
					
                        if(ck_MapRgb != null){
							ck_MapRgb.setClickable(true);
                        }
						if(ck_MapDepth != null){
							ck_MapDepth.setClickable(true);
						}
                    }
                }else if(str.equals("IRAndRGB_30")) {
					dataType = DataType.DATA_TYPE_IR_RGB_30;
                    mPicoCamera.setDataType(dataType.ordinal());
                    if(ck_MapRgb != null){
						ck_MapRgb.setClickable(false);
                    }
					if(ck_MapDepth != null){
						ck_MapDepth.setClickable(false);
					}
                }else if(str.equals("DepthAndIR_30")){
					if(mPicoCamera != null) {
						dataType = DataType.DATA_TYPE_DEPTH_IR_30;
						mPicoCamera.setDataType(dataType.ordinal());
						if (ck_MapRgb != null) {
							ck_MapRgb.setClickable(false);
						}
						if(ck_MapDepth != null){
							ck_MapDepth.setClickable(false);
						}
					}
                }else if(str.equals("DepthAndIR_15_RGB_30")){
					if(mPicoCamera != null) {
						dataType = DataType.DATA_TYPE_DEPTH_IR_15_RGB_30;
						mPicoCamera.setDataType(dataType.ordinal());
					}
                    if(ck_MapRgb != null){
						ck_MapRgb.setClickable(true);
                    }
					if(ck_MapDepth != null){
						ck_MapDepth.setClickable(true);
					}
                }else if(str.equals("WDRDepthAndRGB_30")){
					if(mPicoCamera != null) {
						dataType = DataType.DATA_TYPE_WDRDEPTH_RGB_30;
						mPicoCamera.setDataType(dataType.ordinal());
						PsParameter.PsWDRMode  mode = new PsParameter.PsWDRMode();
						mode.totalRange = 2;
						mode.range1 = 0;
						mode.range1Count = 1;
						mode.range2 = 2;
						mode.range2Count = 1;
						PsParameter.PsThreshold threshold = new PsParameter.PsThreshold();
						threshold.Threshold1 = 1000;
						threshold.Threshold2 = 1800;
						mPicoCamera.setWDRMode(mode);
						mPicoCamera.setWDRThreshold(threshold);
						
						PsParameter.PsWDRMode  mode2 = new PsParameter.PsWDRMode();
						mPicoCamera.getWDRMode(mode2);
						Log.e(TAG,"wdr mode	total:"+ mode2.totalRange + ","+mode2.range1+","+mode2.range1Count+","+mode2.range2Count+","+mode2.range2Count+",");
					}
                    if(ck_MapRgb != null){
						ck_MapRgb.setClickable(true);
                    }
					if(ck_MapDepth != null){
						ck_MapDepth.setClickable(true);
					}
                }
				mPicoCamera.setMapperEnabledDepthToRGB(false);
				mPicoCamera.setMapperEnabledRGBToDepth(false);
				mPicoCamera.setMapperEnabledRGBToIR(false);
				ck_MapRgb.setChecked(false);
				ck_MapDepth.setChecked(false);
	
				generateTofBmp(0,TOF_480);
				generateTofBmp(1,TOF_480);
				generateRgbBmp(resolutionIndex);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mPicoCamera = new PsCamera();
		if (mPicoCamera != null) {
			mPicoCamera.init(this,mOnPicoCameraConnectListener);
		}
		mFrameCallback = new FrameCallback();
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");

		if (mPicoCamera != null) {
			mPicoCamera.setFrameCallback(mFrameCallback);
			mPicoCamera.setBGThreshold(20);   //set threshold value  0-200   0:close
			mPicoCamera.setDepthRange(0);   //set depth range value  only support 0-8
			dataType = DataType.DATA_TYPE_DEPTH_RGB_30;
			mPicoCamera.setMapperEnabledDepthToRGB(false);
			mPicoCamera.setMapperEnabledRGBToDepth(false);
			mPicoCamera.setMapperEnabledRGBToIR(false);
			ck_MapRgb.setChecked(false);
			ck_MapDepth.setChecked(false);				
			mPicoCamera.setDataType(dataType.ordinal());
			sp_mData.setSelection(0);
			if(dataType == DataType.DATA_TYPE_SCANFACE)
			{
				if(ck_MapRgb != null){
					ck_MapRgb.setClickable(false);
				}
				if(ck_MapDepth != null){
					ck_MapDepth.setClickable(true);
				}
			}
			mPicoCamera.setRgbResolution(3);//0 :1920x1080 1:1280x720 2:640x480 3:640x360
			resolutionIndex = 3;
			mPicoCamera.start(this);
		}
		mdepthBmpBuf = ByteBuffer.allocateDirect(1920 * 1080 * 4);
		mIrBmpBuf = ByteBuffer.allocateDirect(1920 * 1080 * 4);
		generateTofBmp(0,TOF_480);
		generateTofBmp(1,TOF_480);
		generateRgbBmp(2);
	}

	void generateTofBmp(int frameType,int size){
		if(0 == frameType){
			if(mBmpDepth != null){
				mBmpDepth = null;
			}
			if (TOF_480 == size) {
				mBmpDepth = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
			} else if (TOF_360 == size) {
				mBmpDepth = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
			} else if (TOF_720 == size) {
				mBmpDepth = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
			} else if (TOF_1080 == size) {
				mBmpDepth = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
			}
		}else if(1 == frameType){
			if(mBmpIr != null){
				mBmpIr = null;
			}
			if (TOF_480 == size) {
				mBmpIr = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
			} else if (TOF_360 == size) {
				mBmpIr = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
			}else if (TOF_720 == size) {
				mBmpIr = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
			} else if (TOF_1080 == size) {
				mBmpIr = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
			}
		}
	}

	void generateRgbBmp(int resolutionIndex){
		if(mBmpRgb != null){
			mBmpRgb = null;
		}
		if (0 == resolutionIndex) {
			mBmpRgb = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
		} else if (1 == resolutionIndex) {
			mBmpRgb = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
		} else if (2 == resolutionIndex) {
			mBmpRgb = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
		} else if (3 == resolutionIndex) {
			mBmpRgb = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
		}
	}
	@Override
	protected void onStop() {
		if (DEBUG) Log.v(TAG, "onStop:");
		synchronized (mSync) {
			if (mPicoCamera != null) {
				mPicoCamera.stop();
			}
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		if (mPicoCamera != null) {
			mPicoCamera.destroy();
			mPicoCamera.shutDown();
			mPicoCamera = null;
		}
		if (mGlview != null) {
			mGlview = null;
		}

		if(mBmpDepth != null){
			mBmpDepth.recycle();
			mBmpDepth = null;
		}
		if(mBmpIr != null){
			mBmpIr.recycle();
			mBmpIr = null;
		}
		if(mBmpRgb != null){
			mBmpRgb.recycle();
			mBmpRgb = null;
		}
		super.onDestroy();
		System.exit(0);
	}

	private final OnPicoCameraConnectListener mOnPicoCameraConnectListener = new OnPicoCameraConnectListener() {
		@Override
		public void onAttach() {
			if (DEBUG) Log.e(TAG, "onAttach:");
		}

		@Override
		public void onConnect(int connectStatus) {
			if (DEBUG) Log.e(TAG, "onConnect" + connectStatus);
		}

		@Override
		public void onDisconnect() {
			if (DEBUG) Log.e(TAG, "onDisconnect");
		}

		@Override
		public void onDettach() {
			if (DEBUG) Log.e(TAG, "onDettach");
		}

		@Override
		public void onCancel() {
			if (DEBUG) Log.e(TAG, "onCancel");
		}
		
		@Override
		public void onError() {
			if (DEBUG) Log.e(TAG, "onError");
		}
	};

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Bundle mainBundle = new Bundle();

		mainBundle.putInt("frameShow", sp_mView.getSelectedItemPosition());
		mainBundle.putInt("dataMode", sp_mData.getSelectedItemPosition());
		mainBundle.putInt("resolution", sp_mResolution.getSelectedItemPosition());
		mainBundle.putBoolean("mapRgb", ck_MapRgb.isChecked());
		mainBundle.putBoolean("mapDepth", ck_MapDepth.isChecked());
		outState.putBundle("main", mainBundle);
	}

	public class FrameCallback implements IFrameCallback {
		@Override
		public void onFrame(PsFrame DepthFrame,PsFrame IrFrame,PsFrame RgbFrame){
			if(showDepth && null != DepthFrame) {
				byte[] mByteBuffer_depth;
				int depthValue = 0;
				mByteBuffer_depth = new byte[DepthFrame.frameData.remaining()];
				DepthFrame.frameData.rewind();
				DepthFrame.frameData.get(mByteBuffer_depth);
				int count = mByteBuffer_depth.length >> 1;
				short[] depthBuf = new short[count];
				ByteBuffer.wrap(mByteBuffer_depth).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(depthBuf);
				depthValue = depthBuf[DepthFrame.width * (DepthFrame.height / 2) + (DepthFrame.width / 2)] & 0xFFFF;
				mBmpDepth = Bitmap.createBitmap(DepthFrame.width, DepthFrame.height, Bitmap.Config.ARGB_8888);
				mPicoCamera.Y16ToRgba(mByteBuffer_depth, mBmpDepth,DepthFrame.width,DepthFrame.height, 4400);
				Canvas canvas = new Canvas(mBmpDepth);
				Paint paint = new Paint();
				paint.setAntiAlias(true);
				paint.setDither(true);
				paint.setTextSize(40);
				paint.setColor(Color.parseColor("#ff0000"));
				canvas.drawText(String.valueOf(depthValue), (mBmpDepth.getWidth() / 2) - 20, (mBmpDepth.getHeight() / 2), paint);
				canvas.drawText(".", (mBmpDepth.getWidth() / 2), (mBmpDepth.getHeight() / 2 + 20), paint);
				paint.setColor(Color.parseColor("#00ff00"));
				paint.setTextSize(DepthFrame.width / 24);
				canvas.drawText(DepthFrame.width + "x" + DepthFrame.height, DepthFrame.width / 16, DepthFrame.height / 10, paint);
				myrender.setBuf(mBmpDepth);
				mGlview.requestRender();
			}
			if(showIr && null != IrFrame){

				mBmpIr = Bitmap.createBitmap(IrFrame.width, IrFrame.height, Bitmap.Config.ARGB_8888);
				
				if (PixelFormatGray16.ordinal() == IrFrame.pixelFormat){
					mPicoCamera.Y16ToRgba_bf(IrFrame.frameData, mBmpIr,IrFrame.width,IrFrame.height, 3840);
				}else{
					mPicoCamera.Y8ToRgba_bf(IrFrame.frameData, mBmpIr,IrFrame.width,IrFrame.height);
				}
				Canvas canvas = new Canvas(mBmpIr);
				Paint paint = new Paint();
				paint.setAntiAlias(true);
				paint.setDither(true);
				paint.setTextSize(IrFrame.width / 24);
				paint.setColor(Color.parseColor("#00ff00"));
				canvas.drawText(IrFrame.width + "x" + IrFrame.height, IrFrame.width / 16, IrFrame.height / 10, paint);
				myrender.setBuf(mBmpIr);
				mGlview.requestRender();

			}
			if(showRgb && null != RgbFrame){

				mBmpRgb = Bitmap.createBitmap(RgbFrame.width, RgbFrame.height, Bitmap.Config.ARGB_8888);
				mPicoCamera.RgbToRgba_bf(RgbFrame.frameData,mBmpRgb,RgbFrame.width,RgbFrame.height);
				Canvas canvas = new Canvas(mBmpRgb);
				Paint paint = new Paint();
				paint.setAntiAlias(true);
				paint.setDither(true);
				paint.setTextSize(RgbFrame.width / 24);
				paint.setColor(Color.parseColor("#00ff00"));
				canvas.drawText(RgbFrame.width + "x" + RgbFrame.height, RgbFrame.width / 16, RgbFrame.height / 10, paint);

				myrender.setBuf(mBmpRgb);
				mGlview.requestRender();
			}
		}
	}
}
