package com.example.tools.myDefineRecoder;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

@SuppressLint("SimpleDateFormat")
public class MyRecoder {
	@SuppressWarnings("deprecation")
	private int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;// 采样粗度，一个采样面16比特，相称于2个字节
	private int audioSource = MediaRecorder.AudioSource.MIC;// 来源为麦克风
	private int frequency = 11025;// 采样率，每秒11025个采样面

	private AudioRecord audioRecord = null;
	private boolean isRecord;// 用于记录是否正在录音
	private int bufferSize;// 用于记录最小的录音缓存大小

	private File file;// 用于记录存储的录音文件

	private TextView show_time;// 显示录音时间
	private TextView show_percent;
	private ProgressBar pgb;
	private MyHandler handler = new MyHandler();
	// 用于表示录音状态
	private String state = "";
	// 用于记录录音秒数
	private int timeCount;
	// 用于记录播放的时间
	private int time;

	public MyRecoder(TextView show_time, ProgressBar pgb, TextView show_percent) {
		this.show_time = show_time;
		this.pgb = pgb;
		this.show_percent = show_percent;
		// 用于记录录音时间
		Thread t = new Thread(new HandleInvocation());
		t.start();
	}

	@SuppressLint("SimpleDateFormat")
	private void initVoiceRecode() {

		// 初始化buffer以及audioRecord
		bufferSize = AudioRecord.getMinBufferSize(frequency,
				channelConfiguration, audioEncoding);
		audioRecord = new AudioRecord(audioSource, frequency,
				channelConfiguration, audioEncoding, bufferSize);
	}

	// 生成文件
	public void createFile() {
		// 获取当前时间
		SimpleDateFormat formate = new SimpleDateFormat("yyyy年MM月dd日HH时mm分ss秒");
		Date date = new Date(System.currentTimeMillis());
		String currentTime = formate.format(date);

		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			File directory = new File(Environment.getExternalStorageDirectory()
					.getAbsoluteFile() + "/voice");

			directory.mkdirs();

			file = new File(directory.getAbsoluteFile() + "/" + currentTime
					+ ".pcm");
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else {
			Log.i("提示", "请检查存储卡");
		}
	}

	public void start() {
		timeCount = 0;
		// 只保存一个录音文件
		if (file != null) {
			file.delete();
		}
		state = "recording";
		isRecord = true;// 表示正在录音
		VoiceRecordTask task = new VoiceRecordTask();
		task.execute();
	}

	// 暂停
	public void pause() {
		state = "pause";
		isRecord = false;
	}

	public void stop() {
		state = "stop";
		isRecord = false;
	}

	// 继续录音
	public void continuerRecord() {
		isRecord = true;
		new VoiceRecordTask().execute();
	}

	public void play() {
		if (file != null) {
			int voiceLength = (int) (file.length() / 2);
			short[] music = new short[voiceLength];

			try {
				DataInputStream dataInputStream = new DataInputStream(
						new BufferedInputStream(new FileInputStream(file)));

				for (int i = 0; dataInputStream.available() > 0; i++) {
					music[i] = dataInputStream.readShort();
				}
				// 关闭输入流
				dataInputStream.close();
				AudioTrack audioTrack = new AudioTrack(
						AudioManager.STREAM_MUSIC, frequency,
						channelConfiguration, audioEncoding, voiceLength * 2,
						AudioTrack.MODE_STREAM);
				audioTrack.write(music, 0, voiceLength);
				// 开始播放
				audioTrack.play();
				// write the music buffer to the AudioTrack object
				audioTrack.stop();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				Log.e("AudioTrack", "Playback Failed");
				e.printStackTrace();
			}
			pgb.setMax(timeCount);
			Thread t = new Thread(new ＨandlePlay());
			t.start();
		}
	}

	class ＨandlePlay implements Runnable {

		@Override
		public void run() {
			for (time = 1; time <= timeCount; time++) {
				pgb.setProgress(time);
				Message msg = new Message();
				msg.what = 1;
				handler.sendMessage(msg);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	class VoiceRecordTask extends AsyncTask<Void, Void, Void> {
		// 可在该方法中做一些准备
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		// 该方法必须实现
		@Override
		protected Void doInBackground(Void... params) {
			// 点击start后就会重新创建文件保存录音数据
			if (state.equals("recording")) {
				createFile();
			}
			if (null == audioRecord) {
				initVoiceRecode();
			}
			try {
				// 便于往文件中追加文件内容
				RandomAccessFile random = new RandomAccessFile(file, "rw");
				short[] shortArray = new short[bufferSize / 4];
				// 开始录制音频
				audioRecord.startRecording();

				while (isRecord) {
					int bufferResult = audioRecord.read(shortArray, 0,
							bufferSize);
					// 将读写文件指针移动到末尾
					random.seek(random.length());
					for (int i = 0; i < bufferResult; i++) {
						random.writeShort(shortArray[i]);
					}
				}

				// 停止录音
				if (null != audioRecord)
					audioRecord.stop();
				random.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return null;
		}

		// 在调用publishProgress后调用
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
		}

		// 可用于处理doInBackground返回的结果
		@Override
		protected void onProgressUpdate(Void... values) {
			// TODO Auto-generated method stub
			super.onProgressUpdate(values);
		}

	}

	@SuppressLint("HandlerLeak")
	class MyHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (0 == msg.what) {
				int second = timeCount % 60;
				int minute = timeCount / 60;

				String sec = second > 9 ? second + "" : "0" + second;
				String min = minute > 9 ? minute + "" : "0" + minute;

				show_time.setText(min + ":" + sec);
			} else if (1 == msg.what) {
				show_percent
						.setText((int) ((time * 1.0 / timeCount * 1.0) * 100)
								+ "%");
			}
		}
	}

	class HandleInvocation implements Runnable {

		@Override
		public void run() {
			while (true) {
				if (isRecord) {
					Message msg = new Message();
					msg.what = 0;
					handler.sendMessage(msg);
					timeCount++;
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}

	}
}
